package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.view.View
import android.view.ViewStub
import android.widget.ArrayAdapter
import forpdateam.ru.forpda.common.showSnackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
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
    private val nickField: MaterialAutoCompleteTextView
    private val titleField: TextInputEditText
    private var userNick: String? = presenter.nick
    private var themeTitle: String? = presenter.title
    private val basePaddingTop: Int

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
        nickField = fragment.findViewById(R.id.qms_theme_nick_field) as MaterialAutoCompleteTextView
        titleField = fragment.findViewById(R.id.qms_theme_title_field) as TextInputEditText
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
        val nicks = ArrayList<String>()
        for (user in res) {
            user.nick?.let { nicks.add(it) }
        }
        nickField.setAdapter(ArrayAdapter(nickField.context, android.R.layout.simple_dropdown_item_1line, nicks))
    }

    private fun initCreatorViews() {
        val hasNick = !userNick.isNullOrBlank()
        if (hasNick) {
            nickField.visibility = View.VISIBLE
            nickField.setText(userNick)
            nickField.isEnabled = false
            nickField.isFocusable = false
            nickField.isClickable = false
            fragment.setSubtitle(userNick)
        } else {
            nickField.visibility = View.VISIBLE
            nickField.isEnabled = true
            nickField.isFocusable = true
            nickField.isFocusableInTouchMode = true
            nickField.isClickable = true
            nickField.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    userNick = s.toString()
                    userNick?.let { searchUser(it) }
                    fragment.setSubtitle(userNick)
                }
            })
        }

        if (!themeTitle.isNullOrBlank()) {
            titleField.setText(themeTitle)
            fragment.setTitle(themeTitle)
        }
        titleField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                themeTitle = s.toString()
                fragment.setTitle(themeTitle)
            }
        })
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
