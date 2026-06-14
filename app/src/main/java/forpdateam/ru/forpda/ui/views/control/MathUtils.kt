package forpdateam.ru.forpda.ui.views.control

/**
 * Created by fedor on 21.03.2017.
 */

internal object MathUtils {
    @JvmStatic
    fun constrain(amount: Int, low: Int, high: Int): Int {
        return if (amount < low) low else if (amount > high) high else amount
    }

    @JvmStatic
    fun constrain(amount: Float, low: Float, high: Float): Float {
        return if (amount < low) low else if (amount > high) high else amount
    }
}
