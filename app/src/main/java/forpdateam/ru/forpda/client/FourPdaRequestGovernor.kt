package forpdateam.ru.forpda.client

import timber.log.Timber
import java.io.IOException

/**
 * Единый регулятор исходящей нагрузки на 4pda.
 *
 * Зачем: раньше каждая подсистема сама решала, сколько запросов слать (лента + аватарки + префетч
 * статьи могли уйти пачкой в одну секунду), а на `429 Too Many Requests` приложение только писало
 * строчку в лог. Здесь один общий бюджет запросов на хост и одно место, где реакция на 429 гасит
 * фоновую активность, не трогая то, что пользователь ждёт прямо сейчас.
 *
 * Модель — token bucket: [CAPACITY] токенов, пополнение [REFILL_PER_SECOND] в секунду.
 *  - [RequestPriority.USER] берёт токен и, если ведро пусто, ждёт не дольше [USER_MAX_WAIT_MS],
 *    после чего идёт всё равно: тормозить пользователя дольше вредно, а один лишний запрос погоды
 *    не делает.
 *  - [RequestPriority.BACKGROUND] (префетч, аватарки) стартует только когда в ведре остаётся запас
 *    [BACKGROUND_RESERVE] — фон не имеет права выесть бюджет, нужный экрану.
 *
 * После 429 фоновые запросы блокируются на время `Retry-After` (минимум [MIN_COOLDOWN_MS]);
 * пользовательские продолжают идти, но через опустошённое ведро, то есть заметно реже.
 */
object FourPdaRequestGovernor {

    /** Фоновый запрос отменён, потому что 4pda прямо сейчас ограничивает нас по частоте. */
    class BackgroundThrottledException(
            val cooldownRemainingMs: Long
    ) : IOException("Background request throttled for ${cooldownRemainingMs}ms")

    private const val CAPACITY = 6.0
    private const val REFILL_PER_SECOND = 4.0
    private const val BACKGROUND_RESERVE = 3.0
    private const val USER_MAX_WAIT_MS = 3_000L
    private const val BACKGROUND_MAX_WAIT_MS = 10_000L
    private const val MIN_COOLDOWN_MS = 15_000L
    private const val MAX_COOLDOWN_MS = 120_000L
    private const val SLEEP_STEP_MS = 50L

    private val lock = Any()
    private var tokens = CAPACITY
    private var lastRefillMs = 0L
    @Volatile
    private var cooldownUntilMs = 0L

    /** Осталось ли действие кулдауна после 429 (используется, чтобы вообще не планировать префетч). */
    fun isCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean = cooldownUntilMs > nowMs

    fun cooldownRemainingMs(nowMs: Long = System.currentTimeMillis()): Long =
            (cooldownUntilMs - nowMs).coerceAtLeast(0L)

    /**
     * Блокирует вызывающий поток до момента, когда запрос разрешено выпустить.
     * Вызывается из сетевого интерцептора OkHttp, то есть всегда с фонового потока.
     *
     * @throws BackgroundThrottledException если фоновому запросу так и не дали пройти.
     */
    @Throws(IOException::class)
    fun acquire(priority: RequestPriority, nowMs: Long = System.currentTimeMillis()) {
        val deadline = nowMs + if (priority == RequestPriority.USER) USER_MAX_WAIT_MS else BACKGROUND_MAX_WAIT_MS
        while (true) {
            val now = System.currentTimeMillis()
            if (priority == RequestPriority.BACKGROUND && isCoolingDown(now)) {
                if (now >= deadline) throw BackgroundThrottledException(cooldownRemainingMs(now))
                sleepStep()
                continue
            }
            if (tryTake(priority, now)) return
            if (now >= deadline) {
                if (priority == RequestPriority.BACKGROUND) {
                    throw BackgroundThrottledException(cooldownRemainingMs(now))
                }
                // Пользовательский запрос дольше не ждёт — уходим в сеть с пустым ведром.
                return
            }
            sleepStep()
        }
    }

    /** Ответ получен: 429 включает кулдаун для фона и обнуляет бюджет. */
    fun onResponse(code: Int, retryAfterSeconds: Long?, nowMs: Long = System.currentTimeMillis()) {
        if (code != HTTP_TOO_MANY_REQUESTS) return
        val cooldown = ((retryAfterSeconds ?: 0L) * 1000L)
                .coerceAtLeast(MIN_COOLDOWN_MS)
                .coerceAtMost(MAX_COOLDOWN_MS)
        synchronized(lock) {
            tokens = 0.0
            lastRefillMs = nowMs
        }
        cooldownUntilMs = maxOf(cooldownUntilMs, nowMs + cooldown)
        Timber.w("4pda rate limit hit, background paused for %d ms", cooldown)
    }

    /** Только для тестов: вернуть регулятор в исходное состояние. */
    fun resetForTest() {
        synchronized(lock) {
            tokens = CAPACITY
            lastRefillMs = 0L
        }
        cooldownUntilMs = 0L
    }

    private fun tryTake(priority: RequestPriority, nowMs: Long): Boolean = synchronized(lock) {
        refillLocked(nowMs)
        val required = if (priority == RequestPriority.USER) 1.0 else 1.0 + BACKGROUND_RESERVE
        if (tokens < required) return false
        tokens -= 1.0
        true
    }

    private fun refillLocked(nowMs: Long) {
        if (lastRefillMs == 0L) {
            lastRefillMs = nowMs
            return
        }
        val elapsed = nowMs - lastRefillMs
        if (elapsed <= 0L) return
        tokens = (tokens + elapsed / 1000.0 * REFILL_PER_SECOND).coerceAtMost(CAPACITY)
        lastRefillMs = nowMs
    }

    private fun sleepStep() {
        try {
            Thread.sleep(SLEEP_STEP_MS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for request budget", interrupted)
        }
    }

    const val HTTP_TOO_MANY_REQUESTS = 429
}
