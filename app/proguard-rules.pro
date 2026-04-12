-optimizationpasses 5
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-dontpreverify
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

# Moxy (kapt): биндеры и ViewState — иначе чёрный экран / краш при открытии экранов
-keep class **$$PresentersBinder { *; }
-keep class **$$ViewStateProvider { *; }
-keep class **$$State { *; }

# WebView: методы, вызываемые из JS по имени
-keepclassmembers class * {
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


# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep public class androidx.browser.customtabs.CustomTabsService

# AppMetrica
-keep class io.appmetrica.** { *; }
-dontwarn io.appmetrica.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Coil
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# Cicerone
-keep class com.github.terrakok.cicerone.** { *; }
-dontwarn com.github.terrakok.cicerone.**

# -keep сlass com.lapism.searchview.** { *; }

-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class *
-dontwarn javax.**
-dontwarn io.realm.**

-keep public class * extends io.realm.RealmObject { *; }
-keepnames public class * extends io.realm.RealmObject

-keep class **.R
-keep class **.R$* {
    <fields>;
}

-keep public class com.unnamed.b.atv.model.TreeNode
-keep public class * extends com.unnamed.b.atv.model.TreeNode { *; }
-keep public class com.unnamed.b.atv.model.TreeNode.BaseNodeViewHolder
-keep public class * extends com.unnamed.b.atv.model.TreeNode.BaseNodeViewHolder { *; }

# В search fragment юзается с рефлексией, поэтому нужно исключить
-keep public class androidx.swiperefreshlayout.widget.SwipeRefreshLayout { *; }

