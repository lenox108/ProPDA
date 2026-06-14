package forpdateam.ru.forpda.common

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.R
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
    makeSnackbarAboveSystemBars(message, duration).show()
}

fun View.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    makeSnackbarAboveSystemBars(messageRes, duration).show()
}

fun Fragment.showSnackbarAboveSystemBars(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(message, duration)
}

fun Fragment.showSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(messageRes, duration)
}

fun View.showSnackbarAboveSystemBars(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    makeSnackbarAboveSystemBars(message, duration).show()
}

fun View.showSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    makeSnackbarAboveSystemBars(messageRes, duration).show()
}

fun View.makeSnackbarAboveSystemBars(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
    return Snackbar.make(this, message, duration).applyNavigationBarInset(this)
}

fun View.makeSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
    return Snackbar.make(this, messageRes, duration).applyNavigationBarInset(this)
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
