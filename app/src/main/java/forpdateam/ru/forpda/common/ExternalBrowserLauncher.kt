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

    private fun queryExternalBrowserIntents(context: Context, baseIntent: Intent): List<Intent> {
        val packageManager = context.packageManager
        // MATCH_ALL, а не MATCH_DEFAULT_ONLY: базовый интент уже несёт CATEGORY_BROWSABLE, поэтому
        // в выборку попадают только браузеры. MATCH_DEFAULT_ONLY дополнительно требует у активити
        // CATEGORY_DEFAULT, а часть браузеров (Soul и др.) объявляют VIEW-фильтр лишь с BROWSABLE
        // (этого достаточно, чтобы открывать ссылки) — их отсекало, и при единственном таком браузере
        // список кандидатов оказывался пустым → «внешний браузер не найден». Chrome объявляет DEFAULT,
        // поэтому раньше работал только он.
        val resolvedActivities = packageManager
                .queryIntentActivities(baseIntent, PackageManager.MATCH_ALL)
                .asSequence()
                .mapNotNull { it.activityInfo }
                .filterExternalBrowserActivity(context)
                .toList()

        Log.i(LOG_TAG, "resolved=${resolvedActivities.describeActivities()}")

        val fallbackActivities = KNOWN_BROWSER_PACKAGES
                .asSequence()
                .flatMap { browserPackage ->
                    packageManager
                            .queryIntentActivities(
                                    Intent(baseIntent).setPackage(browserPackage),
                                    PackageManager.MATCH_ALL
                            )
                            .asSequence()
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
