package forpdateam.ru.forpda.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Меряет холодный старт (timeToInitialDisplay) в миллисекундах, воспроизводимо.
 *
 * Две компиляции для сравнения эффекта baseline-профиля:
 *  - [startupCompilationNone] — без AOT (нижняя граница, «как без профиля»);
 *  - [startupCompilationBaselineProfile] — с bundled baseline-профилем (то, что
 *    получает пользователь). Разница между ними = практический выигрыш профиля.
 *
 * Запуск (нужен подключённый девайс/эмулятор API 28+, release-подобная сборка):
 *   ./gradlew :baselineprofile:connectedStableBenchmarkAndroidTest \
 *       -Pforpda.allowDebugSignedStable=true
 * Второй метод требует уже сгенерированного профиля
 * (:app:generateStableReleaseBaselineProfile) — иначе Require упадёт; для «до
 * профиля» гоняйте только *None.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = measure(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() =
            measure(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measure(compilationMode: CompilationMode) = rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val PACKAGE_NAME = "ru.forpdateam.forpda.parallel"
        const val ITERATIONS = 10
    }
}
