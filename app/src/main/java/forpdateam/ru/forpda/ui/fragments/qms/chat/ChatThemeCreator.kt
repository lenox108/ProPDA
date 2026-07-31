package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.view.View
import android.view.ViewStub
import android.widget.ArrayAdapter
import forpdateam.ru.forpda.common.showSnackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatViewModel

/**
 * Created by radiationx on 11.06.17.
 */
class ChatThemeCreator(
    private val fragment: QmsChatFragment,
    private val presenter: QmsChatViewModel
) {
    private val viewStub: ViewStub
    private val creatorRoot: View
    private val nickBlock: TextInputLayout
    private val nickField: MaterialAutoCompleteTextView
    private val titleField: TextInputEditText
    private var userNick: String? = presenter.nick
    private var themeTitle: String? = presenter.title
    /** Ранее введённые ники — подсказки для нового диалога. */
    private val recentNicks: List<String> = presenter.recentNicks()
    private val basePaddingTop: Int
    /** Ввод из [applyIdentity] не должен считаться правкой пользователя (поиск по нику, заголовок). */
    private var bindingIdentity = false
    /** Штатный keyListener поля: при известном нике его снимают, при неизвестном — возвращают. */
    private val defaultNickKeyListener: android.text.method.KeyListener?

    init {
        viewStub = fragment.findViewById(R.id.toolbar_content) as ViewStub
        viewStub.layoutResource = R.layout.toolbar_qms_new_theme
        // Инфлейтим форму в контексте активити (текущая палитра), а НЕ в дефолтном
        // контексте ViewStub — это область тулбара с AppBarOverlay, где colorSurface —
        // passthrough ?attr/colorSurface (перебивает тёмный ActionBar-оверлей). Из-за
        // этого ?attr-цвета в layout становятся 2-уровневыми, и android:background роняет
        // getDrawable при инфляции → InflateException (живой краш под Material You /
        // AMOLED-палитрами, v3.1.9/3.2.2). В контексте активити colorSurface конкретный.
        viewStub.layoutInflater = android.view.LayoutInflater.from(fragment.requireContext())
        creatorRoot = viewStub.inflate()
        basePaddingTop = creatorRoot.paddingTop
        nickBlock = fragment.findViewById(R.id.qms_theme_nick_block) as TextInputLayout
        nickField = fragment.findViewById(R.id.qms_theme_nick_field) as MaterialAutoCompleteTextView
        titleField = fragment.findViewById(R.id.qms_theme_title_field) as TextInputEditText
        defaultNickKeyListener = nickField.keyListener
        applyDynamicTopInset()
        initCreatorViews()
    }

    private fun applyDynamicTopInset() {
        val toolbar: View? = fragment.findViewById(R.id.toolbar)
        if (toolbar == null) return
        toolbar.post {
            val h = toolbar.height
            if (h <= 0) return@post
            creatorRoot.setPadding(
                creatorRoot.paddingLeft,
                basePaddingTop + h,
                creatorRoot.paddingRight,
                creatorRoot.paddingBottom
            )
        }
    }

    private fun searchUser(nick: String) {
        presenter.findUser(nick)
    }

    fun onShowSearchRes(res: List<ForumUser>) {
        val prefix = nickField.text?.toString().orEmpty()
        // Ники из истории, подходящие под ввод, идут первыми: по ним чаще всего и пишут повторно.
        val nicks = LinkedHashSet<String>()
        nicks.addAll(recentNicks.filter { it.startsWith(prefix, ignoreCase = true) })
        res.forEach { user -> user.nick?.let { nicks.add(it) } }
        setSuggestions(nicks.toList())
    }

    private fun setSuggestions(nicks: List<String>) {
        nickField.setAdapter(ArrayAdapter(nickField.context, R.layout.item_qms_nick_suggestion, nicks))
    }

    /** Пустое поле + непустая история → показываем список ранее введённых ников целиком. */
    private fun showHistoryDropdown() {
        if (recentNicks.isEmpty()) return
        if (!nickField.text.isNullOrBlank()) return
        setSuggestions(recentNicks)
        nickField.post { if (nickField.isAttachedToWindow) nickField.showDropDown() }
    }

    private fun initCreatorViews() {
        nickField.visibility = View.VISIBLE
        if (recentNicks.isNotEmpty()) {
            // Стрелка справа раскрывает историю ников, даже когда поле пустое
            // (autocomplete сам по себе срабатывает только на ввод).
            setSuggestions(recentNicks)
            nickField.setOnClickListener { showHistoryDropdown() }
            nickField.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showHistoryDropdown()
            }
        }
        nickField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (bindingIdentity) return
                userNick = s.toString()
                if (s.isBlank()) {
                    setSuggestions(recentNicks)
                } else {
                    searchUser(s.toString())
                }
                fragment.setSubtitle(userNick)
            }
        })
        titleField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (bindingIdentity) return
                themeTitle = s.toString()
                fragment.setTitle(themeTitle)
            }
        })
        applyIdentity(userNick, themeTitle)
    }

    /**
     * Переиспользуемая вкладка QMS-чата одна на всё приложение, а форма создания диалога живёт
     * ровно один инфлейт ViewStub'а. Если её не перепривязывать, «новый диалог с B», открытый
     * после экрана A, показывал бы ник A — и сообщение ушло бы А, ведь [sendNewTheme] берёт ник
     * из этого поля. Зовётся только при реальной смене экрана (см. `creatorBoundKey` в
     * [QmsChatFragment]): обычный возврат на ту же форму не должен стирать введённое.
     */
    fun rebindIdentity(nick: String?, title: String?) {
        applyIdentity(nick, title)
    }

    private fun applyIdentity(nick: String?, title: String?) {
        userNick = nick
        themeTitle = title
        bindingIdentity = true
        try {
            nickField.setText(nick.orEmpty())
            titleField.setText(title.orEmpty())
        } finally {
            bindingIdentity = false
        }

        val hasNick = !nick.isNullOrBlank()
        if (hasNick) {
            // Собеседник уже известен — поле только для чтения. isEnabled=false гасило бы его
            // до 38% альфы (M3 disabled), поэтому снимаем ввод, а вид оставляем обычным;
            // замок на конце поля объясняет, почему ник не редактируется.
            nickField.keyListener = null
            nickField.isCursorVisible = false
            nickField.isFocusable = false
            nickField.isFocusableInTouchMode = false
            nickField.isClickable = false
            nickBlock.endIconMode = TextInputLayout.END_ICON_CUSTOM
            nickBlock.setEndIconDrawable(R.drawable.ic_lock)
            nickBlock.setEndIconTintList(nickBlock.hintTextColor)
            nickBlock.setEndIconOnClickListener(null)
            nickBlock.isEndIconVisible = true
            titleField.requestFocus()
        } else {
            nickField.keyListener = defaultNickKeyListener
            nickField.isCursorVisible = true
            nickField.isEnabled = true
            nickField.isFocusable = true
            nickField.isFocusableInTouchMode = true
            nickField.isClickable = true
            nickBlock.endIconMode = if (recentNicks.isNotEmpty()) {
                nickBlock.setEndIconContentDescription(R.string.qms_recent_nicks)
                TextInputLayout.END_ICON_DROPDOWN_MENU
            } else {
                TextInputLayout.END_ICON_NONE
            }
        }
        fragment.setSubtitle(userNick)
        fragment.setTitle(themeTitle)
    }

    fun sendNewTheme() {
        if (userNick.isNullOrEmpty()) {
            fragment.showSnackbar(R.string.chat_creator_enter_nick)
        } else if (titleField.text.toString().isEmpty()) {
            fragment.showSnackbar(R.string.chat_creator_enter_title)
        } else if (fragment.messagePanel.message.isEmpty()) {
            fragment.showSnackbar(R.string.chat_creator_enter_message)
        } else {
            fragment.onCreateNewTheme(userNick!!, titleField.text.toString(), fragment.messagePanel.message)
        }
    }

    fun setVisible(isVisible: Boolean) {
        viewStub.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (isVisible) {
            applyDynamicTopInset()
        }
    }

    interface ThemeCreatorInterface {
        fun onCreateNewTheme(nick: String, title: String, message: String)
    }
}
