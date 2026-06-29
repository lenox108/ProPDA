package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.usercp.ForumSettings
import forpdateam.ru.forpda.model.repository.usercp.UserCpRepository
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Экран раздела «Настройки форума» (UserCP) полной версии 4PDA.
 *
 * В отличие от остальных экранов настроек, значения здесь не локальные, а серверные:
 * грузятся GET-ом и сохраняются POST-ом через [UserCpRepository]. Все Preference
 * объявлены non-persistent, поэтому SharedPreferences не используется — источник
 * истины только сервер.
 *
 * Сохранение оптимистичное: переключатель применяется в UI сразу, в фоне уходит
 * POST всей формы; при ошибке состояние откатывается к серверному.
 */
@AndroidEntryPoint
class ForumSettingsFragment : BaseSettingFragment() {

    @Inject
    lateinit var repository: UserCpRepository

    /** Последнее подтверждённое сервером состояние всех 15 полей формы. */
    private var current: ForumSettings? = null

    /** true, пока программно проставляем значения — чтобы не реагировать на собственные изменения. */
    private var applyingServerState = false

    private var saveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_forum_settings)
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title

        attachChangeListeners()
        setControlsEnabled(false)
        loadSettings()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val loaded = runCatching { repository.load() }
                    .onFailure { Timber.e(it, "UserCP load failed") }
                    .getOrNull()
            if (loaded == null) {
                toast(R.string.forum_settings_load_error)
                return@launch
            }
            current = loaded
            applyToUi(loaded)
            setControlsEnabled(true)
        }
    }

    private fun attachChangeListeners() {
        for (key in EDITABLE_KEYS) {
            findPreference<Preference>(key)?.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, newValue ->
                        if (applyingServerState) return@OnPreferenceChangeListener true
                        onUserChanged(key, newValue)
                        true
                    }
        }
    }

    private fun onUserChanged(key: String, newValue: Any?) {
        val base = current ?: return
        val updated = base.applyChange(key, newValue)
        current = updated
        // Зависимость как на сайте: при автоустановке часового пояса ручные поля недоступны.
        if (key == KEY_TZ_AUTOSET) updateTimezoneDependency(updated)
        save(updated, previous = base)
    }

    private fun save(settings: ForumSettings, previous: ForumSettings) {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            val confirmed = runCatching { repository.save(settings) }
                    .onFailure { Timber.e(it, "UserCP save failed") }
                    .getOrNull()
            if (confirmed == null) {
                toast(R.string.forum_settings_save_error)
                current = previous
                applyToUi(previous)
                return@launch
            }
            current = confirmed
            applyToUi(confirmed)
        }
    }

    /** Программно отражает состояние сервера в контролах, не вызывая слушателей. */
    private fun applyToUi(settings: ForumSettings) {
        applyingServerState = true
        setList(KEY_TOPIC_PAGE, settings.topicPage)
        setList(KEY_POST_PAGE, settings.postPage)
        setSwitch(KEY_AUTO_TRACK, settings.autoTrack)
        setList(KEY_TRACK_CHOICE, settings.trackChoice)
        setSwitch(KEY_MENTION_NOTIFY, settings.mentionNotify)
        setSwitch(KEY_REP_NOTIFY, settings.repNotify)
        setSwitch(KEY_SEND_FULL_MSG, settings.sendFullMsg)
        setSwitch(KEY_TZ_AUTOSET, settings.tzAutoset)
        setList(KEY_TIME_OFFSET, settings.timeOffset)
        setSwitch(KEY_DST, settings.dstInUse)
        updateTimezoneDependency(settings)
        applyingServerState = false
    }

    private fun updateTimezoneDependency(settings: ForumSettings) {
        val manualEnabled = !settings.tzAutoset
        findPreference<Preference>(KEY_TIME_OFFSET)?.isEnabled = manualEnabled
        findPreference<Preference>(KEY_DST)?.isEnabled = manualEnabled
    }

    private fun setControlsEnabled(enabled: Boolean) {
        for (key in EDITABLE_KEYS) {
            findPreference<Preference>(key)?.isEnabled = enabled
        }
        // Зависимые поля доуточняются актуальным состоянием после загрузки.
        current?.let { if (enabled) updateTimezoneDependency(it) }
    }

    private fun setSwitch(key: String, value: Boolean) {
        findPreference<TwoStatePreference>(key)?.isChecked = value
    }

    private fun setList(key: String, value: String) {
        findPreference<ListPreference>(key)?.value = value
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val PREFERENCE_SCREEN_NAME = "forum_settings"

        private const val KEY_TOPIC_PAGE = "topicpage"
        private const val KEY_POST_PAGE = "postpage"
        private const val KEY_AUTO_TRACK = "auto-track"
        private const val KEY_TRACK_CHOICE = "trackchoice"
        private const val KEY_MENTION_NOTIFY = "mention-notify"
        private const val KEY_REP_NOTIFY = "rep-notify"
        private const val KEY_SEND_FULL_MSG = "send-full-msg"
        private const val KEY_TZ_AUTOSET = "tz-autoset"
        private const val KEY_TIME_OFFSET = "time-offset"
        private const val KEY_DST = "dst-in-use"

        private val EDITABLE_KEYS = listOf(
                KEY_TOPIC_PAGE, KEY_POST_PAGE,
                KEY_AUTO_TRACK, KEY_TRACK_CHOICE,
                KEY_MENTION_NOTIFY, KEY_REP_NOTIFY, KEY_SEND_FULL_MSG,
                KEY_TZ_AUTOSET, KEY_TIME_OFFSET, KEY_DST
        )
    }
}

/** Возвращает копию настроек с применённым изменением одного поля экрана. */
private fun ForumSettings.applyChange(key: String, newValue: Any?): ForumSettings {
    val boolValue = newValue as? Boolean ?: false
    val strValue = newValue as? String ?: ""
    return when (key) {
        "topicpage" -> copy(topicPage = strValue)
        "postpage" -> copy(postPage = strValue)
        "auto-track" -> copy(autoTrack = boolValue)
        "trackchoice" -> copy(trackChoice = strValue)
        "mention-notify" -> copy(mentionNotify = boolValue)
        "rep-notify" -> copy(repNotify = boolValue)
        "send-full-msg" -> copy(sendFullMsg = boolValue)
        "tz-autoset" -> copy(tzAutoset = boolValue)
        "time-offset" -> copy(timeOffset = strValue)
        "dst-in-use" -> copy(dstInUse = boolValue)
        else -> this
    }
}
