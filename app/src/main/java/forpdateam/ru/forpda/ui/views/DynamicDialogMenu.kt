package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Динамическое диалоговое меню.
 * 
 * Улучшения в Kotlin-версии:
 * - MutableList вместо ArrayList
 * - Функциональный тип для OnClickListener
 * - Упрощенная работа с generic-типами
 */
class DynamicDialogMenu<T, E> {
    private val allItems = mutableListOf<MenuItem<T, E>>()
    private val allowedItems = mutableListOf<MenuItem<T, E>>()
    private var dialog: AlertDialog? = null

    data class Style(
        val titleTextSizeSp: Float,
        val itemTextSizeSp: Float,
        val itemMinHeightDp: Int,
        val contentVerticalPaddingDp: Int,
        val itemVerticalPaddingDp: Int,
        val titleBottomPaddingDp: Int
    )

    fun addItem(title: CharSequence, listener: OnClickListener<T, E>): MenuItem<T, E> {
        val item = MenuItem(title, listener)
        allItems.add(item)
        return item
    }

    fun addItem(title: CharSequence): MenuItem<T, E> {
        val item = MenuItem<T, E>(title)
        allItems.add(item)
        return item
    }

    fun allow(index: Int) {
        allow(get(index))
    }

    fun allow(item: MenuItem<T, E>) {
        allowedItems.add(item)
    }

    fun allowAll() {
        allowedItems.addAll(allItems)
    }

    fun disallowAll() {
        allowedItems.clear()
    }

    fun getAllItems(): List<MenuItem<T, E>> = allItems

    fun get(index: Int): MenuItem<T, E> = allItems[index]

    fun containsIndex(title: CharSequence): Int {
        return allItems.indexOfFirst { it.title == title }
    }

    fun changeTitle(i: Int, title: CharSequence) {
        allItems[i].setTitle(title)
    }

    fun getTitles(): Array<CharSequence> {
        return allowedItems.map { it.title }.toTypedArray()
    }

    @JvmOverloads
    fun show(uiContext: Context, context: T, data: E, title: String? = null, style: Style? = null) {
        if (dialog?.isShowing == true) return
        // Overlay зануляет windowMinWidthMinor/Major, которые AlertController иначе держит ~95% экрана
        // (растягивая меню на всю ширину). Накладывается поверх активной materialAlertDialogTheme, так
        // что цвета/скругления темы сохраняются — меняется только минимальная ширина.
        val builder = MaterialAlertDialogBuilder(uiContext, R.style.ThemeOverlay_ForPDA_CompactMenuDialog)
        if (style == null) {
            title?.let { builder.setTitle(it) }
        }

        val menuView = createMenuView(builder.context, context, data, title.takeIf { style != null }, style)
        builder.setView(menuView)

        // Ширину до показа задаёт showWithStyledButtons (сжатие по контенту customPanel ДО show()),
        // поэтому меню сразу появляется в нужной ширине и не «прыгает» вправо с ре-центрированием.
        dialog = builder.showWithStyledButtons().also { shownDialog ->
            shownDialog.setOnDismissListener { dialog = null }
        }
    }

    fun onClick(i: Int, context: T, data: E) {
        allowedItems[i].onClick(context, data)
    }

    /**
     * Элемент меню.
     */
    class MenuItem<T, E>(
        var title: CharSequence,
        private var listener: OnClickListener<T, E>? = null
    ) {
        fun setTitle(title: CharSequence): MenuItem<T, E> {
            this.title = title
            return this
        }

        fun setListener(listener: OnClickListener<T, E>): MenuItem<T, E> {
            this.listener = listener
            return this
        }

        fun onClick(context: T, data: E) {
            listener?.onClick(context, data)
        }
    }

    private fun createMenuView(uiContext: Context, context: T, data: E, title: String?, style: Style?): View {
        return LinearLayout(uiContext).apply {
            orientation = LinearLayout.VERTICAL
            val verticalPadding = style?.contentVerticalPaddingDp ?: 8
            setPadding(0, if (title == null) dp(verticalPadding) else 0, 0, dp(verticalPadding))
            // WRAP_CONTENT (not MATCH_PARENT) so the menu is only as wide as its longest row needs,
            // with a sensible floor so short menus don't collapse to a sliver. Paired with the window
            // setLayout(WRAP_CONTENT) in show(), this keeps action menus compact instead of full-width.
            minimumWidth = dp(240)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            if (title != null && style != null) {
                addView(createTitleView(uiContext, title, style))
            }

            allowedItems.forEachIndexed { index, item ->
                addView(createMenuItemView(uiContext, item.title, style).apply {
                    setOnClickListener {
                        dialog?.dismiss()
                        onClick(index, context, data)
                    }
                })
            }
        }
    }

    private fun createTitleView(context: Context, title: CharSequence, style: Style): View {
        return MaterialTextView(context).apply {
            text = title
            gravity = Gravity.CENTER_VERTICAL
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(resolveColorStateList(context, com.google.android.material.R.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.titleTextSizeSp)
            includeFontPadding = true
            setPadding(dp(24), dp(style.contentVerticalPaddingDp), dp(24), dp(style.titleBottomPaddingDp))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createMenuItemView(context: Context, title: CharSequence, style: Style?): View {
        val rippleAttr = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleAttr, true)

        return MaterialTextView(context).apply {
            text = title
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(style?.itemMinHeightDp ?: 48)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(resolveColorStateList(context, com.google.android.material.R.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style?.itemTextSizeSp ?: 16f)
            includeFontPadding = false
            setPadding(dp(24), dp(style?.itemVerticalPaddingDp ?: 8), dp(24), dp(style?.itemVerticalPaddingDp ?: 8))
            setBackgroundResource(rippleAttr.resourceId)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun View.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun resolveColorStateList(context: Context, attr: Int): ColorStateList {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ColorStateList.valueOf(ContextCompat.getColor(context, typedValue.resourceId))
        } else {
            ColorStateList.valueOf(typedValue.data)
        }
    }

    /**
     * Функциональный интерфейс для обработки кликов.
     */
    fun interface OnClickListener<T, E> {
        fun onClick(context: T, data: E)
    }
}
