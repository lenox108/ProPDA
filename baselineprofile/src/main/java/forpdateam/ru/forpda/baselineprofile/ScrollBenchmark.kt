package forpdateam.ru.forpda.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Меряет джанк скролла ленты новостей: [FrameTimingMetric] отдаёт перцентили
 * длительности кадра (P50/P90/P99) — объективный «дёргается или нет».
 *
 * [scrollFeedNoCompilation] всегда исполним (без профиля);
 * [scrollFeedBaselineProfile] показывает эффект профиля (требует сгенерированного).
 * Запуск — как у StartupBenchmark.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollFeedNoCompilation() = measure(CompilationMode.None())

    @Test
    fun scrollFeedBaselineProfile() =
            measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(compilationMode: CompilationMode) = rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
    ) {
        val feed = device.wait(Until.findObject(By.scrollable(true)), FEED_TIMEOUT_MS)
                ?: return@measureRepeated
        feed.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
        repeat(SCROLLS) {
            feed.fling(Direction.DOWN)
            device.waitForIdle()
        }
        feed.fling(Direction.UP)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "ru.forpdateam.forpda.parallel"
        const val ITERATIONS = 8
        const val SCROLLS = 3
        const val FEED_TIMEOUT_MS = 10_000L
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
