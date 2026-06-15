-optimizationpasses 5
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
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
