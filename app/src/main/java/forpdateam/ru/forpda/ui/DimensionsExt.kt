package forpdateam.ru.forpda.ui

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import forpdateam.ru.forpda.R

/**
 * Extension-функции для получения размеров в пикселях из dimen ресурсов.
 * 
 * Использование:
 * - В Context: context.dp16 или requireContext().dp16
 * - В Fragment: dp16 (напрямую)
 * - В View: view.dp16
 * 
 * Заменяет устаревший подход с App.px*
 */

// Context extensions
val Context.dp2: Int get() = resources.getDimensionPixelSize(R.dimen.dp2)
val Context.dp4: Int get() = resources.getDimensionPixelSize(R.dimen.dp4)
val Context.dp6: Int get() = resources.getDimensionPixelSize(R.dimen.dp6)
val Context.dp8: Int get() = resources.getDimensionPixelSize(R.dimen.dp8)
val Context.dp12: Int get() = resources.getDimensionPixelSize(R.dimen.dp12)
val Context.dp14: Int get() = resources.getDimensionPixelSize(R.dimen.dp14)
val Context.dp16: Int get() = resources.getDimensionPixelSize(R.dimen.dp16)
val Context.dp20: Int get() = resources.getDimensionPixelSize(R.dimen.dp20)
val Context.dp24: Int get() = resources.getDimensionPixelSize(R.dimen.dp24)
val Context.dp32: Int get() = resources.getDimensionPixelSize(R.dimen.dp32)
val Context.dp36: Int get() = resources.getDimensionPixelSize(R.dimen.dp36)
val Context.dp40: Int get() = resources.getDimensionPixelSize(R.dimen.dp40)
val Context.dp48: Int get() = resources.getDimensionPixelSize(R.dimen.dp48)
val Context.dp56: Int get() = resources.getDimensionPixelSize(R.dimen.dp56)
val Context.dp64: Int get() = resources.getDimensionPixelSize(R.dimen.dp64)

// Fragment extensions - делегируют в Context
val Fragment.dp2: Int get() = requireContext().dp2
val Fragment.dp4: Int get() = requireContext().dp4
val Fragment.dp6: Int get() = requireContext().dp6
val Fragment.dp8: Int get() = requireContext().dp8
val Fragment.dp12: Int get() = requireContext().dp12
val Fragment.dp14: Int get() = requireContext().dp14
val Fragment.dp16: Int get() = requireContext().dp16
val Fragment.dp20: Int get() = requireContext().dp20
val Fragment.dp24: Int get() = requireContext().dp24
val Fragment.dp32: Int get() = requireContext().dp32
val Fragment.dp36: Int get() = requireContext().dp36
val Fragment.dp40: Int get() = requireContext().dp40
val Fragment.dp48: Int get() = requireContext().dp48
val Fragment.dp56: Int get() = requireContext().dp56
val Fragment.dp64: Int get() = requireContext().dp64

// View extensions - делегируют в Context
val View.dp2: Int get() = context.dp2
val View.dp4: Int get() = context.dp4
val View.dp6: Int get() = context.dp6
val View.dp8: Int get() = context.dp8
val View.dp12: Int get() = context.dp12
val View.dp14: Int get() = context.dp14
val View.dp16: Int get() = context.dp16
val View.dp20: Int get() = context.dp20
val View.dp24: Int get() = context.dp24
val View.dp32: Int get() = context.dp32
val View.dp36: Int get() = context.dp36
val View.dp40: Int get() = context.dp40
val View.dp48: Int get() = context.dp48
val View.dp56: Int get() = context.dp56
val View.dp64: Int get() = context.dp64

/**
 * Получает размер в пикселях по dp значению.
 * Например: context.getDp(16) вернёт размер в px для 16dp
 */
fun Context.getDp(dp: Int): Int = when (dp) {
    2 -> dp2
    4 -> dp4
    6 -> dp6
    8 -> dp8
    12 -> dp12
    14 -> dp14
    16 -> dp16
    20 -> dp20
    24 -> dp24
    32 -> dp32
    36 -> dp36
    40 -> dp40
    48 -> dp48
    56 -> dp56
    64 -> dp64
    else -> (dp * resources.displayMetrics.density).toInt()
}

fun Fragment.getDp(dp: Int): Int = requireContext().getDp(dp)
fun View.getDp(dp: Int): Int = context.getDp(dp)
