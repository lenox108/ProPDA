package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.widget.FrameLayout
import forpdateam.ru.forpda.ui.views.messagepanel.AutoFitRecyclerView
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel

/**
 * Created by radiationx on 08.01.17.
 */
@SuppressLint("ViewConstructor")
open class BasePanelItem @JvmOverloads constructor(
    context: Context,
    protected val messagePanel: MessagePanel,
    val title: String
) : FrameLayout(context) {
    protected val recyclerView: AutoFitRecyclerView = AutoFitRecyclerView(context)

    init {
        // Нижний ряд сетки (последние кнопки BBCode «Сверху/Куратор») упирался в системную панель
        // навигации/жестов: скролл «не доходил» и последний ряд оставался под баром. Резервируем
        // снизу высоту нав-бара (clipToPadding=false — контент скроллится в этот отступ), чтобы
        // последний ряд можно было прокрутить над баром на любом устройстве.
        recyclerView.clipToPadding = false
        addView(recyclerView)
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (v.paddingBottom != navBottom) {
                v.updatePadding(bottom = navBottom)
            }
            insets
        }
    }

}
