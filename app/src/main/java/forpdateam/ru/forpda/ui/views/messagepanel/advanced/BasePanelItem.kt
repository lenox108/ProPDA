package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
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
        addView(recyclerView)
    }

}
