package forpdateam.ru.forpda.ui

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.view.updateLayoutParams
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder

enum class UiDensity {
    COMPACT,
    COMFORTABLE,
}

data class UiDensityValues(
        val itemHorizontalPaddingPx: Int,
        val itemVerticalPaddingPx: Int,
        val cardVerticalPaddingPx: Int,
        val titleTextSizePx: Float,
        val subtitleTextSizePx: Float,
        val metadataTextSizePx: Float,
        val sectionHeaderPaddingTopPx: Int,
        val sectionHeaderPaddingBottomPx: Int,
        val dividerSpacingPx: Int,
        val qmsThemeRowHeightPx: Int,
        val qmsContactMinHeightPx: Int,
)

fun Preferences.Main.TopicPostDensity.toUiDensity(): UiDensity = when (this) {
    // Native lists must not follow topic post density; keep legacy callers visually unchanged.
    Preferences.Main.TopicPostDensity.COMPACT,
    Preferences.Main.TopicPostDensity.SUPER_COMPACT -> UiDensity.COMFORTABLE
    Preferences.Main.TopicPostDensity.COMFORTABLE -> UiDensity.COMFORTABLE
}

fun Context.currentUiDensity(): UiDensity =
        MainPreferencesHolder(applicationContext).getTopicPostDensity().toUiDensity()

fun Context.currentUiDensityValues(): UiDensityValues =
        resources.uiDensityValues(currentUiDensity())

fun Resources.uiDensityValues(density: UiDensity): UiDensityValues {
    val compact = density == UiDensity.COMPACT
    fun px(@DimenRes compactRes: Int, @DimenRes comfortableRes: Int): Int =
            getDimensionPixelSize(if (compact) compactRes else comfortableRes)

    fun textPx(@DimenRes compactRes: Int, @DimenRes comfortableRes: Int): Float =
            getDimension(if (compact) compactRes else comfortableRes)

    return UiDensityValues(
            itemHorizontalPaddingPx = px(
                    R.dimen.ui_density_compact_item_padding_horizontal,
                    R.dimen.ui_density_comfortable_item_padding_horizontal
            ),
            itemVerticalPaddingPx = px(
                    R.dimen.ui_density_compact_item_padding_vertical,
                    R.dimen.ui_density_comfortable_item_padding_vertical
            ),
            cardVerticalPaddingPx = px(
                    R.dimen.ui_density_compact_card_padding_vertical,
                    R.dimen.ui_density_comfortable_card_padding_vertical
            ),
            titleTextSizePx = textPx(
                    R.dimen.ui_density_compact_title_text_size,
                    R.dimen.ui_density_comfortable_title_text_size
            ),
            subtitleTextSizePx = textPx(
                    R.dimen.ui_density_compact_subtitle_text_size,
                    R.dimen.ui_density_comfortable_subtitle_text_size
            ),
            metadataTextSizePx = textPx(
                    R.dimen.ui_density_compact_metadata_text_size,
                    R.dimen.ui_density_comfortable_metadata_text_size
            ),
            sectionHeaderPaddingTopPx = px(
                    R.dimen.ui_density_compact_section_header_padding_top,
                    R.dimen.ui_density_comfortable_section_header_padding_top
            ),
            sectionHeaderPaddingBottomPx = px(
                    R.dimen.ui_density_compact_section_header_padding_bottom,
                    R.dimen.ui_density_comfortable_section_header_padding_bottom
            ),
            dividerSpacingPx = px(
                    R.dimen.ui_density_compact_divider_spacing,
                    R.dimen.ui_density_comfortable_divider_spacing
            ),
            qmsThemeRowHeightPx = px(
                    R.dimen.ui_density_compact_qms_theme_row_height,
                    R.dimen.ui_density_comfortable_qms_theme_row_height
            ),
            qmsContactMinHeightPx = px(
                    R.dimen.ui_density_compact_qms_contact_min_height,
                    R.dimen.ui_density_comfortable_qms_contact_min_height
            ),
    )
}

fun TextView.setTextSizePx(sizePx: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
}

fun View.applyUiDensityPadding(values: UiDensityValues, horizontal: Boolean = true, vertical: Boolean = true) {
    setPaddingRelative(
            if (horizontal) values.itemHorizontalPaddingPx else paddingStart,
            if (vertical) values.itemVerticalPaddingPx else paddingTop,
            if (horizontal) values.itemHorizontalPaddingPx else paddingEnd,
            if (vertical) values.itemVerticalPaddingPx else paddingBottom
    )
}

fun View.updateHeightPx(heightPx: Int) {
    updateLayoutParams<ViewGroup.LayoutParams> {
        height = heightPx
    }
}
