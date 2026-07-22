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

    /**
     * Открыть ссылку «как ожидает пользователь при обычном тапе»: СНАЧАЛА нативным приложением,
     * которое выбрано/верифицировано для этого хоста (App Links / «Открывать поддерживаемые ссылки»),
     * и только если такого приложения нет — в браузере через [open].
     *
     * Отличие от [open]: [open] всегда целится в БРАУЗЕР (роль BROWSER + перечисление браузеров) и
     * применяется для явного пункта меню «Открыть в браузере». Обычный же тап по внешней ссылке
     * (например, `https://ali.click/…` → AliExpress) должен уходить в профильное приложение, если оно
     * назначено обработчиком таких ссылок. Раньше и тап уходил в [open] → всегда браузер.
     */
    fun openPreferringApp(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        // Только http/https конкурируют с браузером за обработку. Прочие схемы (mailto:, tel:,
        // intent:, market: и т.п.) не «браузерные» — отдаём их системе как есть.
        if (scheme == "http" || scheme == "https") {
            resolveNativeAppIntent(context, uri)?.let { appIntent ->
                if (launchExplicit(context, appIntent)) {
                    Log.i(LOG_TAG, "opened native app ${appIntent.component?.packageName} for $uri")
                    return true
                }
            }
        }
        return open(context, url)
    }

    /**
     * Если для этого КОНКРЕТНОГО URL выбран (или верифицирован через App Links) нативный обработчик
     * — не браузер и не мы, — вернуть явный интент на него. Иначе null (→ откат в браузер).
     *
     * resolveActivity(MATCH_DEFAULT_ONLY) уважает пользовательский выбор «Открывать по умолчанию»:
     *  • верифицированный App Links-обработчик хоста имеет приоритет над браузером по умолчанию;
     *  • пользовательский дефолт для домена возвращается тоже;
     *  • если дефолт не задан — вернётся браузер по умолчанию (его отсекаем) либо ResolverActivity.
     * Так «в первую очередь своё приложение, если оно выбрано, иначе браузер» соблюдается точно.
     */
    private fun resolveNativeAppIntent(context: Context, uri: Uri): Intent? {
        val packageManager = context.packageManager
        val realIntent = createBrowserViewIntent(uri)
        val resolved = packageManager
                .resolveActivity(realIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?: return null
        if (!resolved.isLaunchableNativeApp(context, collectBrowserPackages(context))) return null
        Log.i(LOG_TAG, "native handler for $uri: ${resolved.packageName}/${resolved.name}")
        return resolved.toExplicitBrowserIntent(realIntent)
    }

    /** Пакеты, которые отвечают на нейтральную браузер-пробу = настоящие браузеры (их исключаем). */
    private fun collectBrowserPackages(context: Context): Set<String> {
        val packageManager = context.packageManager
        val browsers = HashSet<String>(KNOWN_BROWSER_PACKAGES)
        for (probe in BROWSER_PROBE_URIS) {
            val probeIntent = createBrowserViewIntent(Uri.parse(probe))
            packageManager.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
                    .mapNotNull { it.activityInfo?.packageName }
                    .forEach { browsers.add(it) }
        }
        return browsers
    }

    private fun ActivityInfo.isLaunchableNativeApp(context: Context, browserPackages: Set<String>): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (packageName == "android") return false // ResolverActivity — дефолт не выбран
        if (name?.contains("ResolverActivity", ignoreCase = true) == true) return false
        if (packageName == context.packageName || packageName == BASE_PACKAGE_NAME) return false
        if (name?.startsWith("$BASE_PACKAGE_NAME.") == true) return false
        return packageName !in browserPackages
    }

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
