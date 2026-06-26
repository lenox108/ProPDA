package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * Process-wide [Context] lookup for code paths that historically reached for
 * [forpdateam.ru.forpda.App.instance] but are themselves called from static
 * facades (e.g. [Html.fromHtml]) that have no DI-friendly caller in scope.
 *
 * The [Context] is bound once in [forpdateam.ru.forpda.App.onCreate] from the
 * Hilt-injected `@ApplicationContext`. Tests may call [bind] / [unbind] with
 * a Robolectric context to avoid the [forpdateam.ru.forpda.App.instance] `!!`
 * pattern that fires before `onCreate` (broadcast receivers, content providers).
 */
object ContextImageLookup {
    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    fun unbind() {
        appContext = null
    }

    /**
     * Returns a [Drawable] resolved from the bound context, or `null` if no
     * context has been bound yet. Callers are expected to fall back to a
     * placeholder when this returns null (the same behavior the old
     * `App.instance!!` path had, with the only difference being that this
     * does not crash on cold-start receivers).
     */
    fun requireDrawable(@DrawableRes resId: Int): Drawable? {
        val ctx = appContext ?: return null
        return ContextCompat.getDrawable(ctx, resId)
    }
}
