package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.pro.ProLicense
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Экран «Настройки → Дополнительные функции».
 *
 * Ключ активации ОДИН на все платные возможности (push-уведомления и прокси), поэтому и место у
 * него одно — раньше он был спрятан внутри уведомлений, и из прокси было непонятно, что там
 * активировать. Здесь же написано, что именно даёт активация: на самих экранах функций для этого
 * нет места.
 */
class ProSettingsFragment : BaseSettingFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_pro)
        findPreference<Preference>(KEY_MEMBER_ID)?.setOnPreferenceClickListener {
            val memberId = ProLicense.currentMemberId(requireContext())
            if (memberId == null) {
                Toast.makeText(requireContext(), R.string.pro_status_not_logged, Toast.LENGTH_SHORT).show()
            } else {
                ProDialog.copyMemberId(requireContext(), memberId)
            }
            true
        }
        findPreference<Preference>(KEY_ENTER_KEY)?.setOnPreferenceClickListener {
            ProDialog.show(requireContext(), onChanged = { updateState() })
            true
        }
        findPreference<Preference>(KEY_REMOVE_KEY)?.setOnPreferenceClickListener {
            ProDialog.confirmRemoveKey(requireContext(), onChanged = { updateState() })
            true
        }
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title
    }

    override fun onResume() {
        super.onResume()
        // Ключ могли ввести на другом экране — статус пересобираем при каждом возврате.
        updateState()
    }

    private fun updateState() {
        val context = context ?: return
        findPreference<Preference>(KEY_STATUS)?.summary = ProDialog.statusSummary(context)
        findPreference<Preference>(KEY_MEMBER_ID)?.summary =
                ProLicense.currentMemberId(context)
                        ?.let { getString(R.string.pref_summary_pro_member_id, it) }
                        ?: getString(R.string.pro_status_not_logged)
        // Удалять нечего, пока ключа нет — пункт только путал бы.
        findPreference<Preference>(KEY_REMOVE_KEY)?.isVisible = ProLicense.isUnlocked(context)
    }

    override fun searchSection(): SettingsSection = SettingsSection.PRO

    companion object {
        const val PREFERENCE_SCREEN_NAME = "pro"
        private const val KEY_STATUS = "pro.status"
        private const val KEY_MEMBER_ID = "pro.member_id"
        private const val KEY_ENTER_KEY = "pro.enter_key"
        private const val KEY_REMOVE_KEY = "pro.remove_key"
    }
}
