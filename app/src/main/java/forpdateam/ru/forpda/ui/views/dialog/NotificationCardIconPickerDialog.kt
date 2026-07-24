package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.AppIconManager
import forpdateam.ru.forpda.common.appicon.AppIcons
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/** Полноценный ручной выбор цветной large icon для карточки уведомления. */
object NotificationCardIconPickerDialog {

    fun show(context: Context, current: String, onPick: (String) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(value: Int) = (value * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant =
                context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val surface =
                context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)

        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        fun addCard(
                value: String,
                @DrawableRes previewRes: Int,
                @StringRes titleRes: Int,
                @StringRes subtitleRes: Int? = null,
                tinted: Boolean = false,
        ) {
            val selected = value == current
            val title = context.getString(titleRes)
            val preview = ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, previewRes))
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (tinted) imageTintList = ColorStateList.valueOf(onSurface)
                contentDescription = title
            }
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    setTextColor(onSurface)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                subtitleRes?.let {
                    addView(TextView(context).apply {
                        setText(it)
                        setTextColor(onSurfaceVariant)
                        textSize = 12f
                        setPadding(0, px(2), 0, 0)
                    })
                }
            }
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(14), px(14), px(14), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = px(16).toFloat()
                    setColor(surface)
                    setStroke(
                            if (selected) px(3) else px(1),
                            if (selected) selectedRing else outline,
                    )
                }
                addView(preview, LinearLayout.LayoutParams(px(48), px(48)))
                addView(
                        texts,
                        LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f,
                        ).apply { marginStart = px(14) },
                )
                isClickable = true
                setOnClickListener {
                    dialog.dismiss()
                    onPick(value)
                }
            }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = px(6)
                bottomMargin = px(6)
            })
        }

        addCard(
                AppIcons.NOTIFICATION_CARD_ICON_AUTO,
                R.drawable.ic_notify_favorites,
                R.string.notification_card_icon_auto,
                R.string.notification_card_icon_auto_desc,
                tinted = true,
        )
        addCard(
                AppIcons.NOTIFICATION_CARD_ICON_APP,
                AppIconManager.selected(context).iconRes,
                R.string.notification_card_icon_app,
                R.string.notification_card_icon_app_desc,
        )
        AppIcons.variants.forEach { variant ->
            addCard(variant.id, variant.iconRes, variant.titleRes)
        }

        val hint = TextView(context).apply {
            setText(R.string.notification_card_icon_hint)
            setTextColor(onSurfaceVariant)
            textSize = 12f
            setPadding(px(16), px(4), px(16), px(12))
        }
        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_notification_card_icon)
                .setView(ScrollView(context).apply {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(hint)
                        addView(list)
                    })
                })
                .setNegativeButton(android.R.string.cancel, null)
                .showWithStyledButtons(compact = false)
    }
}
