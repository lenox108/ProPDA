package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import androidx.annotation.DrawableRes

/**
 * Created by radiationx on 08.01.17.
 */
class ButtonData {
    val text: String
    val icon: String?
    @DrawableRes
    val iconRes: Int
    val title: String?
    val listener: ClickListener?

    interface ClickListener {
        fun onClick(data: ButtonData)
    }

    constructor(text: String, icon: String) {
        this.text = text
        this.icon = icon
        this.iconRes = 0
        this.title = null
        this.listener = null
    }

    constructor(text: String, @DrawableRes iconRes: Int) {
        this.text = text
        this.icon = null
        this.iconRes = iconRes
        this.title = null
        this.listener = null
    }

    constructor(text: String, @DrawableRes iconRes: Int, title: String?) {
        this.text = text
        this.icon = null
        this.iconRes = iconRes
        this.title = title
        this.listener = null
    }

    constructor(text: String, @DrawableRes iconRes: Int, listener: ClickListener?) {
        this.text = text
        this.icon = null
        this.iconRes = iconRes
        this.title = null
        this.listener = listener
    }
}
