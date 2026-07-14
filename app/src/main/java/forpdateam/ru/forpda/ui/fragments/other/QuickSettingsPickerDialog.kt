package forpdateam.ru.forpda.ui.fragments.other

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.other.QuickSetting

/**
 * Выбор состава ряда «Быстрые настройки». Порядок чипов — канонический (порядок объявления
 * [QuickSetting]), а не порядок нажатий: так ряд не прыгает при каждом изменении набора.
 */
object QuickSettingsPickerDialog {

    fun show(
            context: Context,
            selected: List<QuickSetting>,
            onPicked: (List<QuickSetting>) -> Unit
    ) {
        val all = QuickSetting.entries
        val titles = all.map { context.getString(quickSettingTitle(it)) }.toTypedArray()
        val checked = all.map { selected.contains(it) }.toBooleanArray()

        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.other_menu_quick_settings_picker_title)
                .setMultiChoiceItems(titles, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onPicked(all.filterIndexed { index, _ -> checked[index] })
                }
                .show()
    }
}
