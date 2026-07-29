package forpdateam.ru.forpda.ui.fragments.other

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import forpdateam.ru.forpda.databinding.ItemQuickSettingRowBinding
import forpdateam.ru.forpda.databinding.SheetQuickSettingsBinding
import forpdateam.ru.forpda.entity.app.other.QuickSetting

/**
 * Лист быстрых настроек: раньше это был ряд чипсов, который занимал две строки и не показывал
 * значений («Тема» вместо «Тема · AMOLED»). Теперь в меню одна строка со сводкой, а сам список
 * с текущими значениями живёт здесь — тем же приёмом, что и на переделанном экране настроек.
 *
 * Действия не дублируются: клик по строке отдаёт тот же [QuickSetting] в обработчик экрана меню.
 */
object QuickSettingsSheet {

    fun show(
            context: Context,
            items: List<QuickSetting>,
            valueProvider: (QuickSetting) -> String?,
            onPick: (QuickSetting) -> Unit,
            onEditComposition: () -> Unit,
            onAllSettings: () -> Unit,
    ) {
        val inflater = LayoutInflater.from(context)
        val binding = SheetQuickSettingsBinding.inflate(inflater)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        items.forEach { setting ->
            val row = ItemQuickSettingRowBinding.inflate(inflater, binding.quickSettingsContainer, false)
            row.quickSettingTitle.setText(quickSettingTitle(setting))
            val value = valueProvider(setting)
            row.quickSettingValue.text = value.orEmpty()
            row.quickSettingValue.visibility =
                    if (value.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            row.root.setOnClickListener {
                dialog.dismiss()
                onPick(setting)
            }
            binding.quickSettingsContainer.addView(row.root)
        }

        binding.quickSettingsEdit.setOnClickListener {
            dialog.dismiss()
            onEditComposition()
        }
        binding.quickSettingsAll.setOnClickListener {
            dialog.dismiss()
            onAllSettings()
        }
        dialog.show()
    }
}
