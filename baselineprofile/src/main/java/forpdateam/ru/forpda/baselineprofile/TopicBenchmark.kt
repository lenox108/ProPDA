package forpdateam.ru.forpda.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "До"-замеры для roadmap `native-topic-renderer.md` (нативный рендер темы вместо
 * WebView). Обе величины из его Фазы 0: TTFP открытия темы и джанк скролла темы.
 *
 * Заходим напрямую deep-link'ом ("https://4pda.to/forum/index.php?showtopic=…") —
 * приложение объявляет BROWSABLE intent-filter на 4pda.to (см. `AndroidManifest.xml`),
 * так что [startActivityAndWait] с этим Uri стартует прямо в теме, минуя хрупкую
 * UI-навигацию по вкладкам/спискам (которая потребовала бы конкретных селекторов,
 * непроверяемых без подключённого устройства).
 *
 * [topicOpenCompilationNone]/[topicScrollNoCompilation] — величины "до" (без профиля,
 * как в [StartupBenchmark]/[ScrollBenchmark]); *BaselineProfile-варианты — тот же
 * сценарий с профилем, для сравнения после генерации.
 *
 * ВАЖНО (честно об ограничениях):
 * - [StartupTimingMetric] в режиме COLD меряет время до ПЕРВОГО КАДРА запущенной
 *   Activity — это НЕ гарантия, что посты темы уже отрисованы (WebView грузит контент
 *   асинхронно уже после первого кадра). Это тот же самый компромисс, что и в
 *   [StartupBenchmark] для домашнего экрана — здесь для консистентности сохранён
 *   такой же контракт метрики, а не выдумана более точная (которая потребовала бы
 *   трейс-меток в самом `ThemeFragmentWeb`, а его reveal-логика — самая хрупкая часть
 *   экрана, см. §2 плана; трогать её ради Фазы 0 избыточный риск).
 * - Тестовая тема (см. [TEST_TOPIC_URL]) — реальная (взята из живого капчура,
 *   `app/src/test/resources/parser/theme/topic_deep_prepended_hat.html`): богатый
 *   контент (спойлеры/код/картинки-вложения), т.е. репрезентативна для перфа. Если
 *   тема станет недоступна (удалена/перенесена) — заменить на другую живую тему,
 *   константа единственная.
 * - Предполагается публичный просмотр темы без логина (как обычно у форумов); если
 *   4pda.to начнёт требовать авторизацию для чтения — потребуется предзалогиненный
 *   профиль устройства (это забота того, кто запускает бенч, а не самого теста).
 * - Требует подключённого устройства/эмулятора (см. README у [StartupBenchmark]);
 *   на эмуляторе — только индикативно (см. `suppressErrors=EMULATOR` в build.gradle).
 *
 * Запуск (как у [ScrollBenchmark]):
 *   ./gradlew :baselineprofile:connectedStableBenchmarkAndroidTest \
 *       -Pforpda.allowDebugSignedStable=true
 */
@RunWith(AndroidJUnit4::class)
class TopicBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun topicOpenCompilationNone() = measureOpen(CompilationMode.None())

    @Test
    fun topicOpenBaselineProfile() =
            measureOpen(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun topicScrollNoCompilation() = measureScroll(CompilationMode.None())

    @Test
    fun topicScrollBaselineProfile() =
            measureScroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureOpen(compilationMode: CompilationMode) = rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait(topicIntent())
    }

    private fun measureScroll(compilationMode: CompilationMode) = rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
                startActivityAndWait(topicIntent())
                // Даём WebView время догрузить и отрисовать пост(ы) до начала замера скролла —
                // иначе первые кадры "скролла" на самом деле замеряют догрузку контента.
                device.waitForIdle(CONTENT_SETTLE_TIMEOUT_MS)
            },
    ) {
        val topic = device.wait(Until.findObject(By.scrollable(true)), TOPIC_TIMEOUT_MS)
                ?: return@measureRepeated
        topic.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
        repeat(SCROLLS) {
            topic.fling(Direction.DOWN)
            device.waitForIdle()
        }
        topic.fling(Direction.UP)
        device.waitForIdle()
    }

    private fun topicIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(TEST_TOPIC_URL)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private companion object {
        const val PACKAGE_NAME = "ru.forpdateam.forpda.parallel"
        const val TEST_TOPIC_URL = "https://4pda.to/forum/index.php?showtopic=1115315"
        const val ITERATIONS = 8
        const val SCROLLS = 3
        const val TOPIC_TIMEOUT_MS = 10_000L
        const val CONTENT_SETTLE_TIMEOUT_MS = 3_000L
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
