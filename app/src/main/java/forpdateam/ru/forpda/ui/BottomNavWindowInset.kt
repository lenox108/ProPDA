package forpdateam.ru.forpda.ui

import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Единая точка расчёта нижнего «хрома» под полоску вкладок приложения.
 *
 * [WindowInsetsCompat.Type.navigationBars] — то, что система отдаёт и для трёх кнопок, и для
 * жестовой полоски (высота зависит от OEM / версии). Совпадает с тем, что использует BottomSheet
 * при [setGestureInsetBottomIgnored(true)] — без двойного учёта mandatory gesture inset.
 */
object BottomNavWindowInset {

    /** Нижний inset системной панели навигации (px), ≥ 0. */
    @JvmStatic
    fun navigationBarsBottomPx(insets: WindowInsetsCompat?): Int {
        if (insets == null) return 0
        return max(0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
    }

    /**
     * Padding снизу для [fragments_container]: высота полоски вкладок + системная навигация.
     * Должен совпадать с peek нижнего BottomSheet (см. [BottomDrawer]).
     */
    @JvmStatic
    fun fragmentsBottomPaddingPx(
            baseTabBarPx: Int,
            rootInsets: WindowInsetsCompat?,
            fallbackNavBottomPx: Int
    ): Int {
        val nav = if (rootInsets != null) {
            navigationBarsBottomPx(rootInsets)
        } else {
            max(0, fallbackNavBottomPx)
        }
        return baseTabBarPx + nav
    }
}
