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

    /** Только для имён тем, попавших в список до того, как мы стали их сохранять. */
    @Inject lateinit var historyDao: forpdateam.ru.forpda.entity.db.history.HistoryItemDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_proxy)
        configureProEntry()
        configureTextFields()
        configurePasswordField()
        configureProbe()
        configureBlockedTopicsList()
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title
    }

    override fun onResume() {
        super.onResume()
        // Ключ могли ввести на экране уведомлений — статус пересобираем при каждом возврате.
        updateProState()
        refreshBlockedTopics()
    }

    /**
     * Прокси — платная функция ProPDA Pro, и ключ ТОТ ЖЕ, что и для push: активировал одно —
     * доступно и другое, отдельной покупки нет. Здесь только UI-часть: не даём включить тумблер
     * без ключа и сразу открываем окно активации. Сам маршрут закрыт независимо — в
     * [ProxySettings.config] и в [forpdateam.ru.forpda.client.Client].
     */
    private fun configureProEntry() {
        findPreference<Preference>(KEY_PRO_ENTRY)?.setOnPreferenceClickListener {
            openProScreen()
            true
        }
        findPreference<Preference>(ProxySettings.KEY_ENABLED)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue != true || proxySettings.isUnlocked()) return@OnPreferenceChangeListener true
                    Toast.makeText(requireContext(), R.string.pref_proxy_pro_required, Toast.LENGTH_SHORT).show()
                    openProScreen()
                    false // тумблер не включаем: без ключа он всё равно ничего не даст
                }
        updateProState()
    }

    /** Активация живёт отдельным разделом — там же написано, что именно она открывает. */
    private fun openProScreen() {
        startActivity(android.content.Intent(requireContext(), SettingsActivity::class.java)
                .putExtra(SettingsActivity.ARG_NEW_PREFERENCE_SCREEN, ProSettingsFragment.PREFERENCE_SCREEN_NAME))
    }

    /**
     * Пока ключа нет, платные пункты гасим — иначе экран выглядит рабочим, а прокси молча не
     * поднимается. Поля адреса остаются доступными: заполнить их заранее не мешает.
     */
    private fun updateProState() {
        val context = context ?: return
        val unlocked = proxySettings.isUnlocked()
        findPreference<Preference>(KEY_PRO_ENTRY)?.summary = ProDialog.statusSummary(context)
        findPreference<Preference>(ProxySettings.KEY_ENABLED)?.apply {
            if (!unlocked) {
                summary = getString(R.string.pref_summary_proxy_pro_locked)
                // Значение могло остаться с активного ключа — не показываем «включено» вхолостую.
                (this as? androidx.preference.TwoStatePreference)?.isChecked = false
            } else {
                summary = getString(R.string.pref_summary_proxy_enabled)
            }
        }
        findPreference<Preference>(KEY_TEST)?.isEnabled = unlocked
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
            if (blockedTopics.size() > 0) showBlockedTopicsDialog()
            true
        }
        updateBlockedTopicsSummary()
    }

    /**
     * Полный список отдельным диалогом: названия тем длинные, в summary они не помещаются, а по
     * одному номеру («Тема №1050118») понять, что это за тема, невозможно. Строим свои View, а не
     * `setItems`: тот режет названия в одну строку многоточием — ровно то, чего здесь нельзя.
     */
    private fun showBlockedTopicsDialog() {
        val context = context ?: return
        val topics = blockedTopics.topics()
        if (topics.isEmpty()) return

        val density = resources.displayMetrics.density
        val pad = (density * 16).toInt()
        val rows = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val content = android.widget.ScrollView(context).apply { addView(rows) }

        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_proxy_blocked_list)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.pref_proxy_blocked_list_clear_all) { _, _ ->
                    blockedTopics.clear()
                    updateBlockedTopicsSummary()
                    Toast.makeText(context, R.string.pref_proxy_blocked_list_cleared, Toast.LENGTH_SHORT).show()
                }
                .showWithStyledButtons()

        rows.addView(android.widget.TextView(context).apply {
            text = getString(R.string.pref_proxy_blocked_list_hint)
            textSize = 12f
            alpha = 0.7f
            setPadding(pad, pad / 2, pad, pad / 2)
        })
        topics.forEach { topic ->
            rows.addView(blockedTopicRow(context, topic, pad, density) { row ->
                blockedTopics.forget(topic.id)
                rows.removeView(row)
                updateBlockedTopicsSummary()
                Toast.makeText(
                        context,
                        getString(R.string.pref_proxy_blocked_topic_removed, topic.displayName()),
                        Toast.LENGTH_SHORT,
                ).show()
                if (blockedTopics.size() == 0) dialog.dismiss()
            })
        }
    }

    /** Строка списка: полное название в несколько строк + номер темы под ним. */
    private fun blockedTopicRow(
            context: android.content.Context,
            topic: BlockedTopicRegistry.BlockedTopic,
            pad: Int,
            density: Float,
            onRemove: (android.view.View) -> Unit,
    ): android.view.View {
        val title = android.widget.TextView(context).apply {
            text = topic.displayName()
            textSize = 16f
        }
        val row = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (density * 12).toInt(), pad, (density * 12).toInt())
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            addView(title)
            // Номер показываем только рядом с названием: без названия он и так в заголовке строки.
            if (topic.title != null) {
                addView(android.widget.TextView(context).apply {
                    text = getString(R.string.pref_proxy_blocked_topic_unnamed, topic.id)
                    textSize = 12f
                    alpha = 0.7f
                })
            }
        }
        row.setOnClickListener { onRemove(row) }
        return row
    }

    /** Темы из старых версий сохранены без имени — показываем хотя бы номер. */
    private fun BlockedTopicRegistry.BlockedTopic.displayName(): String =
            title ?: getString(R.string.pref_proxy_blocked_topic_unnamed, id)

    /**
     * Имя темы сохраняется с версии, где появился этот список, — у тех, кто пользовался прокси
     * раньше, в списке остались одни номера. Достаём названия из «Истории»: закрытую тему
     * пользователь открывал сам, иначе она бы в список не попала, поэтому запись там почти всегда
     * есть. Сеть не трогаем.
     */
    private fun refreshBlockedTopics() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                blockedTopics.topics()
                        .filter { it.title == null }
                        .forEach { topic ->
                            val title = runCatching { historyDao.getHistoryById(topic.id)?.title }.getOrNull()
                            blockedTopics.rememberTitle(topic.id, title)
                        }
            }
            updateBlockedTopicsSummary()
        }
    }

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
        private const val KEY_PRO_ENTRY = "pro.license_entry"
        private const val KEY_TEST = "net.proxy.test"
        private const val KEY_BLOCKED_LIST = "net.proxy.blocked_list"

        /** Сколько имён показывать в summary, пока строка не превратилась в простыню. */
        private const val SUMMARY_TOPICS = 3
    }
}
