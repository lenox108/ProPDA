package forpdateam.ru.forpda.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import forpdateam.ru.forpda.R

object ExternalBrowserLauncher {
    private const val LOG_TAG = "ExternalBrowser"
    private const val BASE_PACKAGE_NAME = "forpdateam.ru.forpda"
    /** @see Intent.EXTRA_INTENTS — explicit chooser targets only (API 24+). */
    private const val EXTRA_INTENTS = "android.intent.extra.INTENTS"
    /**
     * Нейтральные host-пробы для перечисления браузеров. Хост (example.com) не заявлен ни одним
     * не-браузерным обработчиком, поэтому по такой пробе матчатся ТОЛЬКО настоящие браузеры
     * (заявившие любой https/http-хост), а host-специфичные BROWSABLE-активити — менеджер загрузок
     * на прямых файловых ссылках, deep-link'и youtube/4pda и т.п. — не попадают. Реальный URL
     * подставляется уже в явный интент запуска.
     */
    private val BROWSER_PROBE_URIS = listOf("https://www.example.com", "http://www.example.com")
    private val KNOWN_BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.yandex.browser"
    )

    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val baseIntent = createBrowserViewIntent(uri)

        // 1) Приоритет — ЯВНО открыть браузер по умолчанию (роль BROWSER). Это ровно то, что
        //    ожидает пользователь («открой в моём браузере»), и явный интент не перехватывается
        //    оболочкой (на MIUI/HyperOS неявный VIEW даёт системный тост «Браузер по умолчанию
        //    не найден»). resolveActivity(MATCH_DEFAULT_ONLY) — ОТДЕЛЬНЫЙ механизм резолва от
        //    queryIntentActivities ниже: если один на конкретной прошивке капризничает, срабатывает
        //    другой. Браузеры без CATEGORY_DEFAULT (Soul) сюда не попадут — их подхватит перечисление.
        resolveDefaultBrowserIntent(context, baseIntent)?.let { defaultIntent ->
            if (launchExplicit(context, defaultIntent)) {
                Log.i(LOG_TAG, "opened default browser ${defaultIntent.component?.packageName}")
                return true
            }
        }

        val candidates = queryExternalBrowserIntents(context, baseIntent)
        // ACTION_CHOOSER needs EXTRA_INTENT (primary target); EXTRA_INTENTS alone is ignored on many devices.
        // createChooser(primary, …) also re-resolves the URL and duplicates implicit browser handlers.
        val launchIntent = when {
            candidates.size == 1 -> candidates.first()
            candidates.size > 1 -> Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_TITLE, context.getString(R.string.open_with))
                putExtra(Intent.EXTRA_INTENT, candidates.first())
                putExtra(EXTRA_INTENTS, candidates.drop(1).toTypedArray())
            }
            else -> null
        }
        val selectedPackage = candidates.firstOrNull()?.component?.packageName

        Log.i(LOG_TAG, "url=$uri candidates=${candidates.describeComponents()} selected=$selectedPackage")

        if (launchIntent == null) {
            // Перечисление браузеров вернуло пусто — это не обязательно значит, что браузера нет.
            // На нестандартных прошивках (MIUI/HyperOS, рабочий профиль, кастомные лаунчеры-браузеры)
            // host-проба может не сматчить дефолтный браузер, хотя система его знает. Прежде чем
            // показывать тост «не найдено», отдаём ссылку системе обычным неявным VIEW —
            // Android сам откроет браузер по умолчанию.
            if (launchViaSystemDefault(context, baseIntent)) {
                return true
            }
            Toast.makeText(context, R.string.external_browser_not_found, Toast.LENGTH_SHORT).show()
            return false
        }

        if (context !is Activity) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(launchIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "Chooser failed, falling back to first browser", e)
            val fallback = candidates.firstOrNull() ?: return false
            if (context !is Activity) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            return true
        }
    }

    /**
     * Резолвит браузер по умолчанию (роль BROWSER) через resolveActivity(MATCH_DEFAULT_ONLY) и
     * возвращает ЯВНЫЙ интент на него. null, если дефолт не задан (система вернула ResolverActivity)
     * либо резолв указал на не-браузер / наш пакет.
     */
    private fun resolveDefaultBrowserIntent(context: Context, baseIntent: Intent): Intent? {
        val packageManager = context.packageManager
        for (probe in BROWSER_PROBE_URIS) {
            val probeIntent = createBrowserViewIntent(Uri.parse(probe))
            val resolved = packageManager.resolveActivity(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val info = resolved?.activityInfo ?: continue
            val pkg = info.packageName
            // "android"/ResolverActivity = дефолт не выбран, система показала бы свой выбор — пропускаем.
            if (pkg.isNullOrEmpty() || pkg == "android") continue
            if (info.name?.contains("ResolverActivity", ignoreCase = true) == true) continue
            if (pkg == context.packageName || pkg == BASE_PACKAGE_NAME) continue
            if (info.name?.startsWith("$BASE_PACKAGE_NAME.") == true) continue
            Log.i(LOG_TAG, "default browser resolved: $pkg/${info.name}")
            return info.toExplicitBrowserIntent(baseIntent)
        }
        return null
    }

    private fun launchExplicit(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "explicit launch failed for ${intent.component?.packageName}", e)
            false
        }
    }

    /**
     * Последний рубеж: неявный VIEW-интент без явного компонента. Пусть система сама разрешит
     * браузер по умолчанию. Используется, только когда наше перечисление вернуло пусто.
     */
    private fun launchViaSystemDefault(context: Context, baseIntent: Intent): Boolean {
        val systemIntent = Intent(baseIntent).apply {
            component = null
            `package` = null
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(systemIntent)
            Log.i(LOG_TAG, "opened via system default resolver")
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "system default resolver found no browser", e)
            false
        }
    }

    private fun queryExternalBrowserIntents(context: Context, baseIntent: Intent): List<Intent> {
        val packageManager = context.packageManager
        // Перечисляем браузеры по нейтральным host-пробам (BROWSER_PROBE_URIS), а НЕ по реальному URL:
        // по реальной ссылке в кандидаты лезли host-специфичные обработчики (например, менеджер загрузок
        // на прямых файловых ссылках) — и launcher открывал их вместо браузера. По нейтральному хосту
        // матчатся только настоящие браузеры.
        // MATCH_ALL, а не MATCH_DEFAULT_ONLY: часть браузеров (Soul и др.) объявляют VIEW-фильтр лишь с
        // CATEGORY_BROWSABLE без CATEGORY_DEFAULT — MATCH_DEFAULT_ONLY их отсекал.
        val probeIntents = BROWSER_PROBE_URIS.map { createBrowserViewIntent(Uri.parse(it)) }

        val resolvedActivities = probeIntents
                .asSequence()
                .flatMap { packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL).asSequence() }
                .mapNotNull { it.activityInfo }
                .filterExternalBrowserActivity(context)
                .toList()

        Log.i(LOG_TAG, "resolved=${resolvedActivities.describeActivities()}")

        val fallbackActivities = KNOWN_BROWSER_PACKAGES
                .asSequence()
                .flatMap { browserPackage ->
                    probeIntents.asSequence().flatMap { probe ->
                        packageManager
                                .queryIntentActivities(
                                        Intent(probe).setPackage(browserPackage),
                                        PackageManager.MATCH_ALL
                                )
                                .asSequence()
                    }
                }
                .mapNotNull { it.activityInfo }
                .filterExternalBrowserActivity(context)
                .toList()

        Log.i(LOG_TAG, "knownFallback=${fallbackActivities.describeActivities()}")

        val activityByPackage = LinkedHashMap<String, ActivityInfo>()
        for (activity in resolvedActivities) {
            activityByPackage.putIfAbsent(activity.packageName, activity)
        }
        for (activity in fallbackActivities) {
            activityByPackage.putIfAbsent(activity.packageName, activity)
        }

        return activityByPackage.values
                .map { it.toExplicitBrowserIntent(baseIntent) }
                .toList()
    }

    private fun Sequence<ActivityInfo>.filterExternalBrowserActivity(context: Context): Sequence<ActivityInfo> {
        val ownPackage = context.packageName
        val ownUid = context.applicationInfo.uid
        return this
                .filter { it.packageName != ownPackage }
                .filter { it.packageName != BASE_PACKAGE_NAME }
                .filter { !it.name.startsWith("$BASE_PACKAGE_NAME.") }
                .filter { it.applicationInfo.uid != ownUid }
                .distinctBy { "${it.packageName}/${it.name}" }
    }

    private fun createBrowserViewIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    private fun ActivityInfo.toExplicitBrowserIntent(baseIntent: Intent): Intent {
        return Intent(baseIntent).apply {
            setClassName(packageName, name)
        }
    }

    private fun List<Intent>.describeComponents(): String {
        return joinToString(prefix = "[", postfix = "]") { intent ->
            intent.component?.let { "${it.packageName}/${it.className}" } ?: intent.`package`.orEmpty()
        }
    }

    private fun List<ActivityInfo>.describeActivities(): String {
        return joinToString(prefix = "[", postfix = "]") { "${it.packageName}/${it.name}" }
    }
}
