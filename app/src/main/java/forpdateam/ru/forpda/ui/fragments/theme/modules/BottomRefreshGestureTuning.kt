package forpdateam.ru.forpda.ui.fragments.theme.modules

import kotlin.math.max
import kotlin.math.min

/**
 * Единые константы жеста «обновление свайпом снизу» для обоих движков: нативного списка
 * (`NativeTopicFragment.installBottomRefreshDetector`) и легаси-WebView
 * ([BottomRefreshGestureController]). Раньше числа были продублированы в двух файлах и уже разъехались
 * (230dp против 220dp, 240мс против 260мс).
 *
 * **Почему числа именно такие.** Жалоба с устройств ~6.9″: жест слишком длинный, палец приходится
 * тянуть слишком высоко и перехватывать телефон. Защита от случайного срабатывания держалась ЦЕЛИКОМ
 * на ходе пальца — 230dp ≈ 3.6 см от точки касания. Ход урезан примерно вдвое, а защита перенесена на
 * признаки намеренности, которые ничего не стоят в эргономике:
 *  - [maxReleaseVelocityPx] 1450 → 700dp/s: докрут скролла отпускают быстро, осознанный тяг — почти в
 *    ноль. Прежний потолок пропускал практически любой свайп и защитой по сути не был;
 *  - [MIN_CONTROLLED_DURATION_MS] 240 → 300мс;
 *  - [REST_BEFORE_ARM_MS] — новый гард: жест не взводится, пока список ещё катится или только что
 *    остановился. Это ровно тот случайный сценарий, от которого защищаемся, — «долистал до низа и по
 *    инерции провёл ещё раз», — и он не отсекался вообще.
 *
 * Порог [triggerDistancePx] адаптивен по точке касания: палец не уезжает выше ~⅓ пути к верху, и порог
 * всегда достижим. Фиксированные 230dp этого не гарантировали: касание выше 230dp от верха области
 * делало жест невыполнимым в принципе.
 */
object BottomRefreshGestureTuning {

    /** Доля расстояния «точка касания → верх области», которую занимает полный ход жеста. */
    private const val REACH_FRACTION = 0.35f
    private const val MIN_TRIGGER_DP = 88f
    private const val MAX_TRIGGER_DP = 128f

    /** Мёртвая зона до захвата: короче прежних 48dp, чтобы индикатор оживал в первой трети хода. */
    private const val CAPTURE_DP = 32f
    private const val CAPTURE_SLOP_FACTOR = 2

    /** Запас до верхней кромки, чтобы порог не совпадал с самым краем области. */
    private const val EDGE_MARGIN_DP = 8f

    /** Сколько нужно протянуть ПОСЛЕ захвата даже при касании у самого верха. */
    private const val MIN_PULL_AFTER_CAPTURE_DP = 24f

    private const val MAX_RELEASE_VELOCITY_DP = 700f

    /** Жест засчитывается только как контролируемое движение, не как остаток флинга. */
    const val MIN_CONTROLLED_DURATION_MS = 300L

    /** Список должен быть в покое хотя бы столько миллисекунд к моменту касания. */
    const val REST_BEFORE_ARM_MS = 200L

    fun captureDistancePx(density: Float, touchSlop: Int): Float =
            max(touchSlop * CAPTURE_SLOP_FACTOR.toFloat(), CAPTURE_DP * density)

    /**
     * Полный ход жеста для касания на высоте [downY] (в пикселях от верха области). Зажат между
     * [MIN_TRIGGER_DP] и [MAX_TRIGGER_DP] и дополнительно ограничен доступным ходом до верхней кромки.
     */
    fun triggerDistancePx(density: Float, downY: Float, captureDistancePx: Float): Float {
        val adaptive = (downY * REACH_FRACTION)
                .coerceIn(MIN_TRIGGER_DP * density, MAX_TRIGGER_DP * density)
        val reachable = downY - EDGE_MARGIN_DP * density
        val floor = captureDistancePx + MIN_PULL_AFTER_CAPTURE_DP * density
        return max(floor, min(adaptive, reachable))
    }

    fun maxReleaseVelocityPx(density: Float): Float = MAX_RELEASE_VELOCITY_DP * density

    /**
     * Прогресс индикатора отсчитывается ОТ точки захвата, а не от касания: иначе первые
     * [captureDistancePx] пикселей полоса стоит на нуле и пользователь тянет с запасом «на всякий
     * случай» — половина жалобы «жест слишком длинный» именно про это.
     */
    fun progress(distance: Float, captureDistancePx: Float, triggerDistancePx: Float): Float {
        val span = max(1f, triggerDistancePx - captureDistancePx)
        return min(1f, max(0f, (distance - captureDistancePx) / span))
    }
}
