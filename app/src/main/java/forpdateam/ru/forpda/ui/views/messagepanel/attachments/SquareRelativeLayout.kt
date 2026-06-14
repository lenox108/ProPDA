package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout

/**
 * Created by radiationx on 09.01.17.
 */
class SquareRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr, defStyleRes) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
