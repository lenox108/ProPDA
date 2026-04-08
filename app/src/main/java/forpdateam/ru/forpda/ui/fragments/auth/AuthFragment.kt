package forpdateam.ru.forpda.ui.fragments.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.rahatarmanahmed.cpv.CircularProgressView
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.simple.SimpleAnimationListener
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.presentation.auth.AuthPresenter
import forpdateam.ru.forpda.presentation.auth.AuthView
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import moxy.presenter.InjectPresenter
import moxy.presenter.ProvidePresenter

/**
 * Created by radiationx on 29.07.16.
 */
class AuthFragment : TabFragment(), AuthView {

    private lateinit var nick: EditText
    private lateinit var password: EditText
    private lateinit var captcha: EditText
    private lateinit var captchaImage: ImageView
    private lateinit var avatar: ImageView
    private lateinit var sendButton: Button
    private lateinit var skipButton: Button
    private lateinit var regButton: Button
    private lateinit var loginProgress: ProgressBar
    private lateinit var captchaProgress: ProgressBar
    private lateinit var hiddenAuth: CheckBox

    private lateinit var mainForm: LinearLayout
    private lateinit var complete: RelativeLayout
    private lateinit var completeText: TextView
    private lateinit var progressView: CircularProgressView
    private lateinit var authTopButtons: View

    private val loginTextWatcher = object : SimpleTextWatcher() {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            val filled = !nick.text.toString().isEmpty() && !password.text.toString().isEmpty() && captcha.text.toString().length == 4
            presenter.setFieldsFilled(filled)
        }
    }

    @InjectPresenter
    lateinit var presenter: AuthPresenter

    @ProvidePresenter
    internal fun providePresenter(): AuthPresenter = AuthPresenter(
            App.get().Di().authRepository,
            App.get().Di().profileRepository,
            App.get().Di().router,
            App.get().Di().schedulers,
            App.get().Di().authHolder,
            App.get().Di().errorHandler,
            App.get().Di().systemLinkHandler
    )

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_auth)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_auth)
        nick = findViewById(R.id.auth_login) as EditText
        password = findViewById(R.id.auth_password) as EditText
        captcha = findViewById(R.id.auth_captcha) as EditText
        captchaImage = findViewById(R.id.captchaImage) as ImageView
        captchaProgress = findViewById(R.id.captcha_progress) as ProgressBar
        avatar = findViewById(R.id.auth_avatar) as ImageView
        mainForm = findViewById(R.id.auth_main_form) as LinearLayout
        complete = findViewById(R.id.auth_complete) as RelativeLayout
        completeText = findViewById(R.id.auth_complete_text) as TextView
        progressView = findViewById(R.id.auth_progress) as CircularProgressView
        loginProgress = findViewById(R.id.login_progress) as ProgressBar
        hiddenAuth = findViewById(R.id.auth_hidden) as CheckBox
        sendButton = findViewById(R.id.auth_send) as Button
        skipButton = findViewById(R.id.auth_skip) as Button
        regButton = findViewById(R.id.auth_reg) as Button
        authTopButtons = findViewById(R.id.auth_top_buttons)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListsBackground()
        skipButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Без авторизации будут недоступны некоторые функции приложения.")
                    .setPositiveButton(R.string.ok) { _, _ -> presenter.onClickSkip() }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
        }
        regButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Процесс регистрации включает в себя множество шагов, поэтому рекомендуем зарегистрироваться через браузер.")
                    .setPositiveButton(R.string.ok) { _, _ -> presenter.onRegistrationClick() }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
        }
        appBarLayout.visibility = View.GONE
        sendButton.setOnClickListener { tryLogin() }
        nick.addTextChangedListener(loginTextWatcher)
        password.addTextChangedListener(loginTextWatcher)
        captcha.addTextChangedListener(loginTextWatcher)
        fragmentContainer.fitsSystemWindows = true
        fragmentContent.fitsSystemWindows = true

        captcha.setOnEditorActionListener(OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (sendButton.isEnabled) {
                    tryLogin()
                }
                return@OnEditorActionListener true
            }
            false
        })
    }

    override fun setSendEnabled(isEnabled: Boolean) {
        sendButton.isEnabled = isEnabled
    }

    override fun setSendRefreshing(isRefreshing: Boolean) {
        if (isRefreshing) {
            loginProgress.visibility = View.VISIBLE
            sendButton.visibility = View.INVISIBLE
        } else {
            loginProgress.visibility = View.INVISIBLE
            sendButton.visibility = View.VISIBLE
        }
    }

    override fun onFormLoaded(authForm: AuthForm) {
        nick.setText(authForm.nick)
        password.setText(authForm.password)
        captcha.setText(authForm.captcha)
        hiddenAuth.isChecked = authForm.isHidden

        captchaImage.visibility = View.GONE
        captchaProgress.visibility = View.VISIBLE
        val captchaUrl = authForm.captchaImageUrl?.let { ForPdaCoil.normalizeData(it) } ?: return
        val req = ImageRequest.Builder(requireContext())
                .data(captchaUrl)
                .target(captchaImage)
                .listener(object : ImageRequest.Listener {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        captchaImage.visibility = View.VISIBLE
                        captchaProgress.visibility = View.GONE
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        captchaProgress.visibility = View.GONE
                    }

                    override fun onCancel(request: ImageRequest) {
                        captchaProgress.visibility = View.GONE
                    }
                })
                .build()
        ForPdaCoil.imageLoader.enqueue(req)
    }

    private fun tryLogin() {
        hideKeyboard()
        presenter.signIn(
                nick.text.toString(),
                password.text.toString(),
                captcha.text.toString(),
                hiddenAuth.isChecked
        )
    }

    override fun onSuccessAuth() {
        mainForm.startAnimation(AlphaAnimation(1.0f, 0.0f).apply {
            duration = 225
            setAnimationListener(object : SimpleAnimationListener() {
                override fun onAnimationEnd(animation: Animation) {
                    mainForm.visibility = View.GONE
                }
            })
        })
        authTopButtons.startAnimation(AlphaAnimation(1.0f, 0.0f).apply {
            duration = 225
            setAnimationListener(object : SimpleAnimationListener() {
                override fun onAnimationEnd(animation: Animation) {
                    authTopButtons.visibility = View.GONE
                }
            })
        })
        complete.visibility = View.VISIBLE
        complete.startAnimation(AlphaAnimation(0.0f, 1.0f).apply {
            duration = 375
        })
    }

    override fun showProfile(profile: ProfileModel) {
        ForPdaCoil.loadInto(avatar, profile.avatar)
        completeText.text = ApiUtils.spannedFromHtml(String.format("%s, <b>%s</b>!", getString(R.string.auth_hello), profile.nick))
        completeText.visibility = View.VISIBLE

        completeText.startAnimation(AlphaAnimation(0.0f, 1.0f).apply {
            duration = 1000
        })

        progressView.startAnimation(AlphaAnimation(1.0f, 0.0f).apply {
            duration = 225
            setAnimationListener(object : SimpleAnimationListener() {
                override fun onAnimationEnd(animation: Animation) {
                    progressView.visibility = View.GONE
                    progressView.stopAnimation()
                }
            })
        })
    }
}
