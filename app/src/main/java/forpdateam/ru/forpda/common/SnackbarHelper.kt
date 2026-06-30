package forpdateam.ru.forpda.common

import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.R
import timber.log.Timber
import kotlin.math.max

/**
 * Единый helper для показа Snackbar вместо Toast.
 * Snackbar привязан к текущему View и не перекрывает контент.
 */
fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbar(message, duration)
}

fun Fragment.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbar(messageRes, duration)
}

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(message, duration)
}

fun View.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(context.getString(messageRes), duration)
}

fun Fragment.showSnackbarAboveSystemBars(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(message, duration)
}

fun Fragment.showSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(messageRes, duration)
}

fun View.showSnackbarAboveSystemBars(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(message, duration)
}

fun View.showSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(context.getString(messageRes), duration)
}

/**
 * Показ Snackbar, который НЕ роняет приложение.
 *
 * На некоторых устройствах/темах (зафиксирован краш на Samsung Galaxy S25 / Android 16) контекст
 * подходящего родителя Snackbar не содержит Material-атрибут `colorOnSurface`, и инфляция
 * `Snackbar$SnackbarLayout` падает с InflateException. [makeSnackbarAboveSystemBars] уже пытается
 * пересобрать снэк на корневом content-view (его тема — гарантированно Material). Здесь — последний
 * рубеж: если и это не помогло, тихо деградируем до Toast вместо вылета.
 */
private fun View.showSnackbarSafely(message: CharSequence, duration: Int) {
    try {
        makeSnackbarAboveSystemBars(message, duration).show()
    } catch (e: Throwable) {
        Timber.w(e, "Snackbar inflate/show failed; falling back to Toast")
        runCatching { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}

fun View.makeSnackbarAboveSystemBars(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
    return runCatching {
        Snackbar.make(this, message, duration).applyNavigationBarInset(this)
    }.getOrElse { e ->
        // Тема контекста этого view не отдала Material-атрибуты — пробуем content-view Activity,
        // у него тема приложения (Material3, colorOnSurface есть). Если и тут провал — пробрасываем,
        // верхний showSnackbarSafely уйдёт в Toast.
        Timber.w(e, "Snackbar make failed on view context; retrying on activity content view")
        val content = rootView?.findViewById<View>(android.R.id.content) ?: rootView ?: throw e
        Snackbar.make(content, message, duration).applyNavigationBarInset(content)
    }
}

fun View.makeSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
    return makeSnackbarAboveSystemBars(context.getString(messageRes), duration)
}

private fun Snackbar.applyNavigationBarInset(anchor: View): Snackbar {
    view.doOnAttach { snackbarView ->
        val bottomInset = anchor.transientMessageBottomOffsetPx()
        if (bottomInset <= 0) return@doOnAttach

        snackbarView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += bottomInset
        }
    }
    return this
}

private fun View.transientMessageBottomOffsetPx(): Int {
    val rootInsets = ViewCompat.getRootWindowInsets(this)
    val navigationBottom = rootInsets
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?.coerceAtLeast(0)
            ?: 0
    val imeBottom = rootInsets
            ?.takeIf { it.isVisible(WindowInsetsCompat.Type.ime()) }
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom
            ?: 0
    val safeSpacing = (8f * resources.displayMetrics.density).toInt()
    val bottomChrome = rootView
            ?.findViewById<View>(R.id.bottomMenuRecycler)
            ?.takeIf { it.isShown }
            ?.let { recycler ->
                max(recycler.height, recycler.layoutParams?.height ?: 0)
            }
            ?: 0

    return if (imeBottom > 0) {
        max(imeBottom, bottomChrome + navigationBottom) + safeSpacing
    } else {
        bottomChrome + navigationBottom + safeSpacing
    }
}
