package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.Client
import forpdateam.ru.forpda.client.proxy.BlockedTopicRegistry
import forpdateam.ru.forpda.client.proxy.ProxySettings
import forpdateam.ru.forpda.client.proxy.WebViewProxy
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Экран «Настройки → Прокси».
 *
 * Зачем он вообще: часть тем 4PDA закрыта для российских IP — сервер отдаёт заглушку «Ошибка 404».
 * Вернуть контент может только запрос с другого адреса, но системный VPN для этого не нужен:
 * достаточно пустить через прокси трафик одного приложения, а по умолчанию — вообще только те темы,
 * которые реально закрыты (см. [forpdateam.ru.forpda.client.proxy.ProxyRouter] и автоповтор в
 * [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi]).
 */
@AndroidEntryPoint
class ProxySettingsFragment : BaseSettingFragment() {

    @Inject lateinit var webClient: IWebClient
    @Inject lateinit var proxySettings: ProxySettings
    @Inject lateinit var blockedTopics: BlockedTopicRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_proxy)
        configureTextFields()
        configurePasswordField()
        configureProbe()
        configureBlockedTopicsList()
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title
    }

    override fun onResume() {
        super.onResume()
        updateBlockedTopicsSummary()
    }

    /**
     * Маршрут WebView (новости, проверка Cloudflare) переключается на весь процесс, поэтому
     * применяем его сразу при уходе с экрана: иначе выключенный прокси остался бы в WebView до
     * следующего запуска.
     */
    override fun onPause() {
        super.onPause()
        WebViewProxy.applyIfNeeded(requireContext())
    }

    /** Показываем введённое значение в summary (иначе поле выглядит пустым) и правим клавиатуру порта. */
    private fun configureTextFields() {
        findPreference<EditTextPreference>(ProxySettings.KEY_HOST)?.applyValueSummary()
        findPreference<EditTextPreference>(ProxySettings.KEY_LOGIN)?.applyValueSummary()
        findPreference<EditTextPreference>(ProxySettings.KEY_PORT)?.apply {
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
            applyValueSummary()
        }
    }

    private fun EditTextPreference.applyValueSummary() {
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            pref.text?.takeIf { it.isNotBlank() } ?: getString(R.string.pref_proxy_not_set)
        }
    }

    /**
     * Пароль живёт не в общих prefs, а в зашифрованном хранилище ([ProxySettings.writePassword]),
     * поэтому подменяем источник данных именно у этого поля. В summary — только факт «задан».
     */
    private fun configurePasswordField() {
        findPreference<EditTextPreference>(ProxySettings.KEY_PASSWORD)?.apply {
            preferenceDataStore = object : PreferenceDataStore() {
                override fun getString(key: String, defValue: String?): String = proxySettings.readPassword()
                override fun putString(key: String, value: String?) = proxySettings.writePassword(value.orEmpty())
            }
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                if (proxySettings.readPassword().isEmpty()) getString(R.string.pref_proxy_not_set)
                else getString(R.string.pref_proxy_password_set)
            }
        }
    }

    /**
     * «Проверить» ходит через ЕЩЁ НЕ включённый прокси тоже: иначе пришлось бы включать вслепую,
     * ломая себе загрузку всего остального, если адрес указан неверно.
     */
    private fun configureProbe() {
        val probe = findPreference<Preference>(KEY_TEST) ?: return
        probe.setOnPreferenceClickListener {
            val config = proxySettings.configIgnoringEnabled()
            if (config == null) {
                probe.summary = getString(R.string.pref_proxy_test_incomplete)
                return@setOnPreferenceClickListener true
            }
            probe.summary = getString(R.string.pref_proxy_test_running)
            probe.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val client = webClient as? Client
                val result = if (client == null) null else withContext(Dispatchers.IO) { client.probeProxy(config) }
                probe.isEnabled = true
                probe.summary = when {
                    result == null -> getString(R.string.pref_proxy_test_failed, "no client")
                    result.ok -> getString(R.string.pref_proxy_test_ok, result.code, result.elapsedMs)
                    result.error != null -> getString(R.string.pref_proxy_test_failed, result.error)
                    else -> getString(R.string.pref_proxy_test_http_error, result.code)
                }
            }
            true
        }
    }

    /**
     * Список ведётся автоматически, поэтому пользователю важно видеть НЕ количество, а какие именно
     * темы ходят мимо прямого маршрута: только так понятно, за что отвечает прокси и не осталось ли
     * в списке лишнего. Имена показываем прямо в summary, полный список с удалением — по нажатию.
     */
    private fun configureBlockedTopicsList() {
        findPreference<Preference>(KEY_BLOCKED_LIST)?.setOnPreferenceClickListener {
            val topics = blockedTopics.topics()
            if (topics.isEmpty()) return@setOnPreferenceClickListener true
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pref_title_proxy_blocked_list)
                    // Нажатие по теме убирает её из списка. Подтверждения нет намеренно: если тема
                    // и правда закрыта, следующий заход по заглушке вернёт её обратно сам.
                    .setItems(topics.map { it.displayName() }.toTypedArray()) { _, which ->
                        val topic = topics[which]
                        blockedTopics.forget(topic.id)
                        updateBlockedTopicsSummary()
                        Toast.makeText(
                                requireContext(),
                                getString(R.string.pref_proxy_blocked_topic_removed, topic.displayName()),
                                Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.pref_proxy_blocked_list_clear_all) { _, _ ->
                        blockedTopics.clear()
                        updateBlockedTopicsSummary()
                        Toast.makeText(requireContext(), R.string.pref_proxy_blocked_list_cleared, Toast.LENGTH_SHORT).show()
                    }
                    .showWithStyledButtons()
            true
        }
        updateBlockedTopicsSummary()
    }

    /** Темы из старых версий сохранены без имени — показываем хотя бы номер. */
    private fun BlockedTopicRegistry.BlockedTopic.displayName(): String =
            title ?: getString(R.string.pref_proxy_blocked_topic_unnamed, id)

    private fun updateBlockedTopicsSummary() {
        val preference = findPreference<Preference>(KEY_BLOCKED_LIST) ?: return
        val topics = blockedTopics.topics()
        preference.summary = when {
            topics.isEmpty() -> getString(R.string.pref_summary_proxy_blocked_list_empty)
            topics.size <= SUMMARY_TOPICS -> topics.joinToString { it.displayName() }
            else -> getString(
                    R.string.pref_summary_proxy_blocked_list_more,
                    topics.take(SUMMARY_TOPICS).joinToString { it.displayName() },
                    topics.size - SUMMARY_TOPICS,
            )
        }
    }

    companion object {
        const val PREFERENCE_SCREEN_NAME = "proxy"
        private const val KEY_TEST = "net.proxy.test"
        private const val KEY_BLOCKED_LIST = "net.proxy.blocked_list"

        /** Сколько имён показывать в summary, пока строка не превратилась в простыню. */
        private const val SUMMARY_TOPICS = 3
    }
}
