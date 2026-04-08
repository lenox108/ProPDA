package forpdateam.ru.forpda.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/** Прокрутка и обновление списков без лишних анимаций смены элементов — быстрее на длинных лентах. */
fun RecyclerView.tuneForListPerformance() {
    setHasFixedSize(true)
    setItemViewCacheSize(12)
    (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
}
