-optimizationpasses 5
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
# Свой код оставляем читаемым в стектрейсах крашей без mapping.txt/retrace —
# отчёты из Telegram-бота сразу понятны. Библиотеки при этом продолжают
# обфусцироваться и перепаковываться (-repackageclasses ниже), поэтому
# org.ccil.cowan.tagsoup уезжает с родного имени и НЕ коллизит с системной
# копией TagSoup в /system/framework/ext.jar на Samsung/OEM (иначе фатальный
# NoSuchMethodError при старте App.onCreate). НЕ возвращать -dontobfuscate.
-keepnames class forpdateam.ru.forpda.** { *; }
# Prevent crashes on API < 30 where getWindowInsetsController() doesn't exist.
-keepclassmembers class android.view.View {
    public android.view.WindowInsetsController getWindowInsetsController();
}
-keepclassmembers class android.webkit.WebView {
    public android.view.WindowInsetsController getWindowInsetsController();
}
-repackageclasses ''
-adaptclassstrings


-dontnote **
-dontwarn forpdateam.ru.forpda.**

# =============================================================================
# Приложение forpdateam.ru.forpda — точечные правила (без -keep всего пакета).
# R8 может удалять неиспользуемый код и обфусцировать имена, кроме перечисленного.
# =============================================================================

# Точка входа Application
-keep public class forpdateam.ru.forpda.App {
    public <init>();
}

# WebView: методы, вызываемые из JS по имени
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# News article WebView bridge (INews) — must survive R8 in stableRelease
-keep class forpdateam.ru.forpda.ui.fragments.news.details.ArticleCommentsNativeBar { *; }
-keep class forpdateam.ru.forpda.ui.fragments.news.details.ArticleContentFragment {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin: метаданные (sealed, inline, reflection в библиотеках)
-keepattributes kotlin.Metadata
-keep class kotlin.Metadata { *; }

# Cicerone: Screen.getKey() = simpleName вложенных классов — стабильные ключи навигации
-keepnames class forpdateam.ru.forpda.presentation.Screen$* {
    *;
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Realm: модели БД (дополнительно к правилу * extends RealmObject ниже)
-keep class forpdateam.ru.forpda.entity.db.** { *; }

-keepattributes SourceFile,LineNumberTable

# okio
-keep class sun.misc.Unsafe { *; }
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**
-dontnote okio.**


# OkHttp - optimized: keepnames instead of keep
-keepattributes Signature
-keepattributes *Annotation*
-keepnames class okhttp3.** { *; }
-keepnames interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep public class androidx.browser.customtabs.CustomTabsService

# AppMetrica
-keep class io.appmetrica.** { *; }
-dontwarn io.appmetrica.**

# WorkManager - keepnames for shrinking
-keepnames class androidx.work.** { *; }
-dontwarn androidx.work.**

# Coil - optimized: keepnames instead of keep
-keepnames class coil.** { *; }
-keepnames interface coil.** { *; }
-dontwarn coil.**

# Cicerone - keepnames for shrinking
-keepnames class com.github.terrakok.cicerone.** { *; }
-dontwarn com.github.terrakok.cicerone.**

-keep class **.R
-keep class **.R$* {
    <fields>;
}


# В search fragment юзается с рефлексией, поэтому нужно исключить
-keep public class androidx.swiperefreshlayout.widget.SwipeRefreshLayout { *; }


# ============================================================================
# PRODUCTION CLEANUP: вырезать ВСЁ логирование/диагностику из release.
# ============================================================================
# Работает только в minify-сборках (release). В DEBUG minify выключен → R8 не
# запускается → всё логирование остаётся для отладки. Так «логи только в DEBUG,
# release чистый» достигается без ветвлений в исходниках.
#
# -assumenosideeffects: R8 считает метод без сайд-эффектов и удаляет ВЫЗОВ, если
# результат не используется (void-логгеры — всегда). Удаляется и построение
# аргументов (map'ы, sanitizeUrl(), форматирование id) — R8 видит их результат
# «мёртвым» после удаления вызова и вычищает транзитивно. НЕ трогаем
# CrashReporter/CrashTelegramUploader (прод-обработка краша) и ColdStartTracer
# (копит тайминги) — у них есть нужные сайд-эффекты, они бьются по терминальным
# синкам ниже только в части эмиссии Timber, не по своей логике.

# 1) android.util.Log — сырые вызовы (в т.ч. ~26 негейченных, писавших в logcat в проде).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
    public static *** isLoggable(...);
}

# 2) Timber — в release дерево не сажается (DebugTree только в DEBUG), но вызовы+строки
#    остаются в байткоде. Timber* покрывает Timber/$Forest/$Tree/$DebugTree.
-assumenosideeffects class timber.log.Timber* {
    public *** v(...);
    public *** d(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
    public *** wtf(...);
    public *** tag(...);
    public *** log(...);
}

# 3) FpdaDebugLog — единый sink структурной диагностики (FPDA_* теги). Удаление его
#    void-методов каскадом вычищает построение аргументов во всех 17 трейс-классах
#    (ThemePostReadStateDiagnostics, FavoritesUnreadTrace, TopicHighlightDiagnostics, …),
#    которые все финишируют здесь. Value-хелперы (sanitizeUrl/classifyHtml/newTraceId)
#    не перечислены — они удалятся сами, когда их результат станет мёртвым.
-assumenosideeffects class forpdateam.ru.forpda.diagnostic.FpdaDebugLog {
    public void log(...);
    public void warn(...);
    public void logQms(...);
    public void logTheme(...);
    public void logSmartButton(...);
    public void logArticle(...);
}
