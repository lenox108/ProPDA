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
import com.google.android.material.progressindicator.CircularProgressIndicator
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.FragmentAuthBinding
import forpdateam.ru.forpda.common.simple.SimpleAnimationListener
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.presentation.auth.AuthFragmentCallbacks
import forpdateam.ru.forpda.presentation.auth.AuthViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 29.07.16.
 */
@AndroidEntryPoint
class AuthFragment : TabFragment(), AuthFragmentCallbacks {

    private var _authBinding: FragmentAuthBinding? = null
    private val authBinding get() = checkNotNull(_authBinding) { "Binding accessed after onDestroyView" }

    // Legacy field accessors for backward compatibility
    private val nick: EditText get() = authBinding.authLogin
    private val password: EditText get() = authBinding.authPassword
    private val captcha: EditText get() = authBinding.authCaptcha
    private val captchaImage: ImageView get() = authBinding.captchaImage
    private val avatar: ImageView get() = authBinding.authAvatar
    private val sendButton: Button get() = authBinding.authSend
    private val skipButton: Button get() = authBinding.authSkip
    private val regButton: Button get() = authBinding.authReg
    private val loginProgress: com.google.android.material.progressindicator.CircularProgressIndicator get() = authBinding.loginProgress
    private val captchaProgress: com.google.android.material.progressindicator.CircularProgressIndicator get() = authBinding.captchaProgress
    private val hiddenAuth: CheckBox get() = authBinding.authHidden
    private val mainForm: LinearLayout get() = authBinding.authMainForm
    private val complete: RelativeLayout get() = authBinding.authComplete
    private val completeText: TextView get() = authBinding.authCompleteText
    private val progressView: CircularProgressIndicator get() = authBinding.authProgress
    private val authTopButtons: View get() = authBinding.authTopButtons

    private val presenter: AuthViewModel by viewModels()

    private val loginTextWatcher = object : SimpleTextWatcher() {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            val filled = !nick.text.toString().isEmpty() && !password.text.toString().isEmpty() && captcha.text.toString().length == 4
            presenter.setFieldsFilled(filled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_auth)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _authBinding = FragmentAuthBinding.inflate(inflater, fragmentContent, true)
        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _authBinding = null
        super.onDestroyViewBinding()
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

        presenter.attachView(this)
        presenter.start()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
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
                }
            })
        })
    }
}
