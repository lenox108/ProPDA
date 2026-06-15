package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import timber.log.Timber
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.RenderProcessGoneDetail
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.common.makeSnackbarAboveSystemBars
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.databinding.FragmentQmsChatBinding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.diagnostic.FpdaPipelineLog
import forpdateam.ru.forpda.diagnostic.StateRaceTrace
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.temp.TempHelper
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatUiEvent
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatLinkNavigation
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatViewModel
import forpdateam.ru.forpda.presentation.qms.chat.QmsLoadErrorKind
import forpdateam.ru.forpda.presentation.qms.chat.formatQmsDebugSnackbarMessage
import forpdateam.ru.forpda.presentation.qms.chat.QmsThreadUiState
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.presentation.qms.chat.QmsOpenTiming
import forpdateam.ru.forpda.presentation.qms.chat.QmsWebRenderPolicy
import forpdateam.ru.forpda.presentation.qms.chat.QmsWebRenderProbe
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.WebViewSecurityProfile
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import java.util.*
import java.util.regex.Pattern
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatTemplate
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

/**
 * Created by radiationx on 25.08.16.
 *
 * Fragment для QMS чата.
 *
 * JS Bridge обоснование:
 * - IBase: базовый интерфейс для обратных вызовов из JS (domContentLoaded, onPageComplete) необходим для очереди pendingQmsJs
 * - Контент: локально генерируемый HTML из API QMS сообщений (доверенный шаблонный контент)
 * - Профиль безопасности: TRUSTED_LOCAL_TEMPLATE (JS включён, базовый bridge разрешён)
 */
@AndroidEntryPoint
class QmsChatFragment : TabFragment(), ChatThemeCreator.ThemeCreatorInterface, ExtendedWebView.JsLifeCycleListener, TabTopScroller {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var qmsChatTemplate: QmsChatTemplate
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository


    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(requireContext(), data))
    }

    private lateinit var blackListMenuItem: MenuItem
    private lateinit var noteMenuItem: MenuItem
    private lateinit var toDialogsMenuItem: MenuItem
    private lateinit var refreshMenuItem: MenuItem
    private var themeCreator: ChatThemeCreator? = null
    private lateinit var webView: ExtendedWebView
    private var _chatBinding: FragmentQmsChatBinding? = null
    private val chatBinding get() = checkNotNull(_chatBinding) { "Binding accessed after onDestroyView" }
    lateinit var messagePanel: MessagePanel
        private set
    private var attachmentsPopup: AttachmentsPopup? = null
    private var clearMessagePanelTextDialog: AlertDialog? = null
    private lateinit var jsInterface: QmsChatJsInterface

    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false
    private var visibleFrameLayoutObserver: ViewTreeObserver? = null
    private val imeInsetsController: QmsChatImeInsetsController by lazy {
        QmsChatImeInsetsController(
                messagePanelHost = messagePanelHost,
                messagePanel = messagePanel,
                dimensionsProvider = dimensionsProvider,
                densityPx = resources.displayMetrics.density,
                viewReadyProvider = { isViewReadyForCallbacks() },
                visibleFrameProvider = {
                    val rect = Rect()
                    val decor = activity?.window?.decorView ?: fragmentContainer.rootView
                    decor.getWindowVisibleDisplayFrame(rect)
                    rect
                }
        )
    }
    private val visibleFrameLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (!isViewReadyForCallbacks()) return@OnGlobalLayoutListener
        imeInsetsController.reapply()
    }

    private lateinit var topScroller: WebViewTopScroller


    private val presenter: QmsChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_chat)
        Timber.d("QmsChatFragment created")
        arguments?.apply {
            val rawUserId = getInt(USER_ID_ARG, QmsChatModel.NOT_CREATED)
            val rawThemeId = getInt(THEME_ID_ARG, QmsChatModel.NOT_CREATED)
            presenter.userId = rawUserId
            presenter.themeId = rawThemeId
            presenter.title = getString(THEME_TITLE_ARG)
            presenter.avatarUrl = getString(USER_AVATAR_ARG)
            presenter.nick = getString(USER_NICK_ARG)
        } ?: run {
            Timber.e("QmsChatFragment.onCreate: arguments is null!")
        }
        logQmsChat(
                "lifecycle_on_create",
                mapOf(
                        "savedState" to (savedInstanceState != null),
                        "themeId" to presenter.themeId,
                        "userId" to presenter.userId
                )
        )
    }

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun useTopBarRoundedBottomCorners(): Boolean = false

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _chatBinding = FragmentQmsChatBinding.inflate(inflater, fragmentContent, true)
        messagePanel = MessagePanel(requireContext(), fragmentContainer, messagePanelHost, false, mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder)
        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            // Без init() + enableBaseBridge() не регистрируется IBase JS-интерфейс → IBase.domContentLoaded() из main.js
            // не вызывается → onDomContentComplete не срабатывает → очередь pendingQmsJs не сбрасывается
            it.init(WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE)
            it.enableBaseBridge()
        }
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                linkHandler,
                systemLinkHandler,
                router,
                clipboardHelper
        ))
        attachWebView(webView)
        chatBinding.qmsChatContainer.addView(webView, 0)
        attachmentsPopup = messagePanel.attachmentsPopup
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()
        appBarLayout.setExpanded(true, false)
        ensureOpaquePinnedToolbarUnderlay()
        setListsBackground()
        messagePanelHost.setBackgroundColor(requireContext().getColorFromAttr(R.attr.background_base))

        jsInterface = QmsChatJsInterface(presenter)
        webView.setJsLifeCycleListener(this)
        webView.addJavascriptInterface(jsInterface, JS_INTERFACE)
        registerForContextMenu(webView)
        webView.webViewClient = QmsChatWebViewClient()
        webView.webChromeClient = CustomWebChromeClient()
        loadBaseWebContainer()

        attachmentsPopup?.setEnabledTextControls(false)
        attachmentsPopup?.setAddOnClickListener { tryPickFile() }
        attachmentsPopup?.setRetryUploadListener(object : AttachmentsPopup.OnRetryUploadListener {
            override fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>) {
                enqueueUpload(files, pending)
            }
        })
        attachmentsPopup?.setDeleteOnClickListener {
            attachmentsPopup?.preDeleteFiles()
            val selectedFiles = attachmentsPopup?.getSelected() ?: emptyList()
            for (item in selectedFiles) {
                item.status = AttachmentItem.STATUS_REMOVED
            }
            attachmentsPopup?.onDeleteFiles(selectedFiles)
        }


        /*attachmentsPopup.setInsertAttachmentListener { item ->
            String.format(Locale.getDefault(),
                    "\n[url=%s]Файл: %s, Размер: %s, Thumb: %s[/url]\n",
                    item.url,
                    item.name,
                    item.weight,
                    item.imageUrl)
        }*/

        messagePanel.addSendOnClickListener { presenter.onSendClick() }
        messagePanel.setClearMessageClickListener { requestClearMessagePanelText() }


        messagePanel.heightChangeListener = MessagePanel.HeightChangeListener {
            webView.setPaddingBottom(it)
        }

        messagePanel.disableBehavior()
        ViewCompat.setElevation(webView, 0f)
        ViewCompat.setTranslationZ(webView, 0f)
        ViewCompat.setElevation(fab, 12f * resources.displayMetrics.density)
        ViewCompat.setOnApplyWindowInsetsListener(coordinatorLayout) { v, insets ->
            // Hand the inset to the controller so it can reapply it later
            // (e.g. on global layout) and compute the panel bottom margin.
            applyMessagePanelImeInsets(insets)
            // MessagePanelHost is moved by MessagePanelHelper via bottomMargin. Do not add the
            // same IME height as inner padding here, or the compact editor gets a blank IME-sized
            // area between the input field and its controls.
            if (messagePanelHost.paddingBottom != 0) {
                messagePanelHost.setPadding(0, 0, 0, 0)
            }

            v.post {
                if (!isViewReadyForCallbacks()) return@post
                v.requestLayout()
                messagePanel.requestLayout()
                chatBinding.qmsChatContainer.requestLayout()
                val panelHeight = if (messagePanel.visibility != View.VISIBLE) 0 else messagePanel.height
                webView.setPaddingBottom(panelHeight)
            }
            insets
        }
        attachVisibleFrameLayoutListener()

        topScroller = WebViewTopScroller(webView, appBarLayout)

        observeViewModel()
    }

    private fun applyMessagePanelImeInsets(insets: WindowInsetsCompat?) {
        imeInsetsController.applyMessagePanelImeInsets(insets)
    }

    private fun resolveImeBottom(insets: WindowInsetsCompat?): Int =
            imeInsetsController.resolveImeBottom(insets)

    private fun attachVisibleFrameLayoutListener() {
        if (visibleFrameLayoutObserver != null) return
        visibleFrameLayoutObserver = fragmentContainer.viewTreeObserver.also { observer ->
            observer.addOnGlobalLayoutListener(visibleFrameLayoutListener)
        }
    }

    private fun detachVisibleFrameLayoutListener() {
        val observer = visibleFrameLayoutObserver
        if (observer?.isAlive == true) {
            observer.removeOnGlobalLayoutListener(visibleFrameLayoutListener)
        }
        visibleFrameLayoutObserver = null
    }

    private fun isViewReadyForCallbacks(): Boolean {
        if (_chatBinding == null || !::messagePanel.isInitialized) return false
        return runCatching {
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)
        }.getOrDefault(false)
    }

    override fun onDestroyView() {
        detachVisibleFrameLayoutListener()
        imeInsetsController.detach(coordinatorLayout)
        messagePanel.heightChangeListener = null
        attachmentsPopup = null
        clearMessagePanelTextDialog?.dismiss()
        clearMessagePanelTextDialog = null
        if (::jsInterface.isInitialized) jsInterface.cancel()
        cancelQmsPendingWebCallbacks()
        detachQmsLayoutResyncListener()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface(JS_INTERFACE)
            webView.removeBaseBridge()
            webView.setJsLifeCycleListener(null)
            webView.endWork()
        }
        _chatBinding = null
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }

    /**
     * Rebinds the alone QMS tab to another dialog (navigator reuses one [QmsChatFragment] instance).
     */
    fun applyChatScreenFromNavigator(screen: Screen.QmsChat) {
        val prevKey = qmsChatKey(presenter.userId, presenter.themeId)
        bindPresenterFromScreen(screen)
        bindArgumentsFromScreen(screen)
        val newKey = qmsChatKey(presenter.userId, presenter.themeId)
        val identityChanged = prevKey != newKey
        logQmsChat(
                "apply_screen",
                mapOf("prevKey" to prevKey, "newKey" to newKey, "changed" to identityChanged)
        )
        if (_chatBinding == null) {
            if (identityChanged) {
                presenter.onChatIdentityChanged()
            }
            return
        }
        val webStale = !qmsWebMessagesApplied || !qmsDomReady ||
                (::webView.isInitialized && !webView.isJsReady)
        if (identityChanged) {
            cancelQmsWebRenderTimeout()
            if (canReuseQmsWebShellForDialogSwitch()) {
                prepareQmsChatSwitchWithoutReload()
            } else {
                loadBaseWebContainer()
            }
            presenter.onChatIdentityChanged()
            return
        }
        if (webStale) {
            loadBaseWebContainer()
            if (presenter.hasLoadedMessages()) {
                presenter.syncLoadedChatToUi(clearExisting = true)
            }
        }
    }

    private fun bindPresenterFromScreen(screen: Screen.QmsChat) {
        presenter.userId = screen.userId
        presenter.themeId = screen.themeId
        presenter.title = screen.themeTitle
        presenter.nick = screen.userNick
        presenter.avatarUrl = screen.avatarUrl
    }

    private fun bindArgumentsFromScreen(screen: Screen.QmsChat) {
        val args = arguments ?: Bundle().also { arguments = it }
        args.putInt(THEME_ID_ARG, screen.themeId)
        args.putInt(USER_ID_ARG, screen.userId)
        args.putString(USER_NICK_ARG, screen.userNick)
        args.putString(USER_AVATAR_ARG, screen.avatarUrl)
        args.putString(THEME_TITLE_ARG, screen.themeTitle)
        screen.screenTitle?.let { args.putString(TabFragment.ARG_TITLE, it) }
        screen.screenSubTitle?.let { args.putString(TabFragment.ARG_SUBTITLE, it) }
    }

    private fun qmsChatKey(userId: Int, themeId: Int) = "$userId:$themeId"

    private fun isQmsTabShown(): Boolean =
            isAdded &&
                    !isHidden &&
                    view != null &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
                launch {
                    presenter.threadState.collect { state ->
                        bindQmsThreadState(state)
                        if (state is QmsThreadUiState.Content) {
                            scheduleQmsContentWatchdog()
                        }
                    }
                }
                yield()
                presenter.start()
                if (presenter.hasLoadedMessages() && !qmsWebMessagesApplied) {
                    logQmsChat("ui_sync_loaded", mapOf("domReady" to qmsDomReady))
                    ensureQmsMessagesVisible("post_start_sync")
                }
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        setRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.messageRefreshing.collect { isRefreshing ->
                        setMessageRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.newMessagesRefreshing.collect { isRefreshing ->
                        setNewMessagesRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.chatMode.collect { mode ->
                        setChatMode(mode)
                    }
                }
                launch {
                    var pollIntervalMs = QMS_AUTO_REFRESH_INTERVAL_MS
                    while (isActive) {
                        delay(pollIntervalMs)
                        if (!isActive || !networkState.getState()) continue
                        if (presenter.shouldSkipAutoRefreshPoll()) {
                            pollIntervalMs = (pollIntervalMs * 2).coerceAtMost(QMS_AUTO_REFRESH_MAX_INTERVAL_MS)
                            continue
                        }
                        pollIntervalMs = QMS_AUTO_REFRESH_INTERVAL_MS
                        presenter.checkNewMessagesSilently()
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: QmsChatUiEvent) {
        when (event) {
            is QmsChatUiEvent.SetFontSize -> setFontSize(event.fontSize)
            is QmsChatUiEvent.SetAppFontMode -> setAppFontMode(event.mode)
            is QmsChatUiEvent.SetStyleType -> setStyleType(event.styleType)
            is QmsChatUiEvent.SetTitles -> setTitles(event.title, event.nick)
            is QmsChatUiEvent.SetChatMode -> setChatMode(event.mode)
            is QmsChatUiEvent.OnShowSearchRes -> onShowSearchRes(event.result)
            is QmsChatUiEvent.ShowChat -> showChat(event.chat)
            is QmsChatUiEvent.OnNewThemeCreate -> onNewThemeCreate(event.chat)
            is QmsChatUiEvent.OnSentMessage -> onSentMessage(event.messages)
            is QmsChatUiEvent.OnBlockUser -> onBlockUser(event.isBlocked)
            is QmsChatUiEvent.ShowAvatar -> showAvatar(event.url)
            is QmsChatUiEvent.OnUploadFiles -> onUploadFiles(event.files)
            is QmsChatUiEvent.MakeAllRead -> makeAllRead()
            is QmsChatUiEvent.OnNewMessages -> onNewMessages(event.messages, event.forceScroll)
            is QmsChatUiEvent.ShowInitialMessages -> showInitialMessages(event.messages)
            is QmsChatUiEvent.ResetAndShowMessages -> resetAndShowMessages(event.messages, event.clearExisting)
            is QmsChatUiEvent.ShowCreateNote -> showCreateNote(event.title, event.nick, event.url)
            is QmsChatUiEvent.TempSendNewTheme -> temp_sendNewTheme()
            is QmsChatUiEvent.TempSendMessage -> temp_sendMessage()
            is QmsChatUiEvent.ShowMoreMessages -> showMoreMessages(event.messages, event.startIndex, event.endIndex)
            is QmsChatUiEvent.LoadFailed -> showQmsLoadError(event.kind, event.message, event.canRetry)
            is QmsChatUiEvent.LoadWarning -> showQmsCacheRefreshWarning(event.kind, event.cacheAgeMinutes)
        }
    }

    private fun showQmsCacheRefreshWarning(kind: QmsLoadErrorKind, cacheAgeMinutes: Int?) {
        val base = getString(R.string.qms_refresh_failed_keep_cached)
        val ageSuffix = cacheAgeMinutes?.takeIf { it >= 0 }?.let { minutes ->
            getString(R.string.qms_cache_age_minutes, minutes)
        }
        val message = if (ageSuffix != null) "$base ($ageSuffix)" else base
        if (BuildConfig.DEBUG && kind != QmsLoadErrorKind.UNKNOWN) {
            showSnackbar("$message · ${kind.name.lowercase()}")
        } else {
            showSnackbar(message)
        }
    }

    private fun showQmsLoadError(kind: QmsLoadErrorKind, failureDetail: String, canRetry: Boolean) {
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        qmsPendingWebRender = false
        updateLoadingIndicator()
        showQmsPersistentError(kind, failureDetail, canRetry)
    }

    private fun bindQmsThreadState(state: QmsThreadUiState) {
        if (_chatBinding == null || presenter.chatMode.value != QmsChatViewModel.MODE_CHAT) return
        when (state) {
            is QmsThreadUiState.Idle -> hideQmsPersistentOverlays()
            is QmsThreadUiState.Loading -> {
                hideQmsPersistentOverlays(exceptLoading = true)
                if (!presenter.hasLoadedMessages()) {
                    contentController.startRefreshing()
                }
            }
            is QmsThreadUiState.Empty -> {
                hideQmsPersistentOverlays()
                showQmsPersistentEmpty()
            }
            is QmsThreadUiState.Error -> {
                hideQmsPersistentOverlays()
                showQmsPersistentError(state.kind, state.message, state.canRetry)
            }
            is QmsThreadUiState.Content -> {
                hideQmsPersistentOverlays()
                syncQmsWebAfterThreadContent()
            }
        }
    }

    /** Thread data is ready but WebView may still be blank (hidden tab, stale bridge, lost UI events). */
    private fun syncQmsWebAfterThreadContent() {
        if (_chatBinding == null || !::webView.isInitialized) return
        ensureQmsMessagesVisible("thread_content")
    }

    private fun hideQmsPersistentOverlays(exceptLoading: Boolean = false) {
        if (!exceptLoading) {
            contentController.stopRefreshing()
        }
        contentController.hideContent(TAG_QMS_EMPTY)
        contentController.hideContent(TAG_QMS_ERROR)
    }

    private fun showQmsPersistentEmpty() {
        if (!contentController.contains(TAG_QMS_EMPTY)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_notifications)
                    .setTitle(R.string.qms_empty_title)
                    .setDesc(R.string.qms_empty_desc)
                    .addAction(R.string.refresh) { presenter.retryLoadChat() }
            contentController.addContent(funnyContent, TAG_QMS_EMPTY)
        }
        contentController.showContent(TAG_QMS_EMPTY)
    }

    private fun showQmsPersistentError(
            kind: QmsLoadErrorKind,
            failureDetail: String,
            canRetry: Boolean,
            titleResOverride: Int? = null,
            descResOverride: Int? = null
    ) {
        val messageRes = when (kind) {
            QmsLoadErrorKind.NETWORK -> R.string.no_network
            QmsLoadErrorKind.SESSION -> R.string.qms_error_session
            QmsLoadErrorKind.CAPTCHA -> R.string.qms_error_captcha
            QmsLoadErrorKind.SERVER -> R.string.qms_error_server
            QmsLoadErrorKind.PARSER -> R.string.qms_error_parser
            QmsLoadErrorKind.UNKNOWN -> R.string.qms_error_unknown
        }
        val titleRes = titleResOverride ?: messageRes
        val descRes = descResOverride ?: messageRes
        val base = getString(titleRes)
        val snackbarMessage = formatQmsDebugSnackbarMessage(
                kind = kind,
                failureDetail = failureDetail,
                baseMessage = base,
                traceId = presenter.traceIdForDiagnostics(),
        )
        if (!contentController.contains(TAG_QMS_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_notifications)
                    .setTitle(titleRes)
                    .setDesc(descRes)
            if (canRetry) {
                funnyContent.addAction(R.string.refresh) { presenter.retryLoadChat() }
            }
            contentController.addContent(funnyContent, TAG_QMS_ERROR)
        }
        contentController.showContent(TAG_QMS_ERROR)
        if (snackbarMessage != null) {
            view?.makeSnackbarAboveSystemBars(snackbarMessage, Snackbar.LENGTH_SHORT)?.show()
        }
    }

    private fun showQmsWebViewPersistentError(detail: String = "", reasonCode: String = "unknown") {
        if (qmsWebRenderErrorShownGeneration == qmsLoadGeneration) {
            contentController.showContent(TAG_QMS_ERROR)
            return
        }
        qmsWebRenderErrorShownGeneration = qmsLoadGeneration
        FpdaPipelineLog.qmsWebViewWarn(
                "render_verify_failed_pre",
                qmsWebViewErrorFields(reasonCode, detail) + mapOf(
                        "errorMappedToUserMessage" to "qms_error_webview",
                )
        )
        showQmsPersistentError(
                QmsLoadErrorKind.UNKNOWN,
                detail,
                canRetry = true,
                titleResOverride = R.string.qms_error_webview,
                descResOverride = R.string.qms_error_webview
        )
        FpdaPipelineLog.qmsWebViewWarn(
                "render_verify_failed_shown",
                qmsWebViewErrorFields(reasonCode, detail) + mapOf(
                        "userMessage" to getString(R.string.qms_error_webview),
                        "errorMappedToUserMessage" to "qms_error_webview",
                )
        )
    }

    private fun shouldShowQmsWebRenderError(): Boolean =
            QmsWebRenderPolicy.shouldShowWebRenderError(
                    hasLoadedMessages = presenter.hasLoadedMessages(),
                    messagesApplied = qmsWebMessagesApplied,
                    contentWatchdogAttempt = qmsContentWatchdogAttempt,
                    recoveryAttemptCount = qmsRecoveryAttemptCount,
                    maxRecoveryPerGeneration = MAX_QMS_RECOVERY_PER_GENERATION,
                    errorAlreadyShownForGeneration = qmsWebRenderErrorShownGeneration == qmsLoadGeneration,
            )

    private fun qmsWebViewErrorFields(reasonCode: String, detail: String): Map<String, Any?> =
            qmsLogFields(
                    mapOf(
                            "reasonCode" to reasonCode,
                            "detail" to detail.take(120),
                            "traceId" to presenter.traceIdForDiagnostics(),
                            "dialogId" to presenter.themeId,
                            "requestId" to qmsLoadGeneration,
                            "expectedContainers" to presenter.expectedVisibleMessContainerCount(),
                            "domReady" to qmsDomReady,
                            "jsReady" to (::webView.isInitialized && webView.isJsReady),
                            "pendingRender" to qmsPendingWebRender,
                            "messagesApplied" to qmsWebMessagesApplied,
                    )
            )

    private fun setFontSize(size: Int) {
        webView.setRelativeFontSize(size)
    }

    private fun setAppFontMode(mode: forpdateam.ru.forpda.ui.AppFontMode) {
        webView.setAppFontMode(mode)
    }

    private fun addUnusedAttachments() {
        try {
            val matcher = attachmentPattern.matcher(messagePanel.message)
            val attachmentsUrls = ArrayList<String>()
            while (matcher.find()) {
                matcher.group(1)?.let { attachmentsUrls.add(it) }
            }
            val notAttached = ArrayList<AttachmentItem>()
            for (item in attachmentsPopup?.getAttachments() ?: emptyList()) {
                if (!attachmentsUrls.contains(item.url)) {
                    notAttached.add(item)
                }
            }
            messagePanel.messageField?.setSelection(messagePanel.messageField?.text?.length ?: 0)
            attachmentsPopup?.insertAttachment(notAttached, false)
        } catch (ignore: Exception) {
        }

    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        refreshMenuItem = menu
                .add(Menu.NONE, R.id.action_qms_chat_refresh, Menu.NONE, R.string.refresh)
                .setIcon(R.drawable.ic_toolbar_refresh)
                .setOnMenuItemClickListener {
                    presenter.checkNewMessages()
                    true
                }
        refreshMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        blackListMenuItem = menu
                .add(R.string.add_to_blacklist)
                .setOnMenuItemClickListener {
                    presenter.blockUser()
                    false
                }
        noteMenuItem = menu
                .add(R.string.create_note)
                .setOnMenuItemClickListener {
                    presenter.createThemeNote()
                    true
                }
        toDialogsMenuItem = menu
                .add(R.string.to_dialogs)
                .setOnMenuItemClickListener {
                    presenter.openDialogs()
                    true
                }
        refreshToolbarMenuItems(false)
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            blackListMenuItem.isEnabled = true
            noteMenuItem.isEnabled = true
            toDialogsMenuItem.isEnabled = true
            refreshMenuItem.isEnabled = true
        } else {
            blackListMenuItem.isEnabled = false
            noteMenuItem.isEnabled = false
            toDialogsMenuItem.isEnabled = false
            refreshMenuItem.isEnabled = false
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        isNetworkRefreshing = isRefreshing
        updateLoadingIndicator()
        refreshToolbarMenuItems(!isRefreshing && !qmsPendingWebRender)
        if (!isRefreshing) {
            if (qmsPendingWebRender) {
                scheduleQmsWebRenderTimeout()
                if (qmsDomReady) {
                    verifyQmsRenderedOrRetry()
                }
            } else if (QmsWebRenderPolicy.shouldForceMessageResync(
                            presenter.hasLoadedMessages(),
                            qmsWebMessagesApplied
                    )
            ) {
                ensureQmsMessagesVisible("refresh_end")
            }
        }
    }

    private fun updateLoadingIndicator() {
        if (_chatBinding == null) return
        val awaitingWeb = qmsPendingWebRender && presenter.expectedVisibleMessageCount() > 0
        val show = isNetworkRefreshing || awaitingWeb
        chatBinding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }


    private fun setChatMode(mode: String) {
        if (mode == QmsChatViewModel.MODE_CHAT) {
            themeCreator?.setVisible(false)
            clearToolbarScrollFlags()
            appBarLayout.setExpanded(true, false)
        } else if (mode == QmsChatViewModel.MODE_CREATING) {
            if (themeCreator == null) {
                themeCreator = ChatThemeCreator(this, presenter)
            }
            themeCreator?.setVisible(true)
            // В режиме создания чата AppBar не должен схлопываться/наезжать на поля ввода.
            clearToolbarScrollFlags()
            appBarLayout.setExpanded(true, false)
        }
    }

    //From theme creator
    override fun onCreateNewTheme(nick: String, title: String, message: String) {
        //addUnusedAttachments()
        presenter.sendNewTheme(nick, title, message, attachmentsPopup?.getAttachments() ?: emptyList())
    }

    private fun temp_sendMessage() {
        sendMessage()
    }

    private fun temp_sendNewTheme() {
        themeCreator?.sendNewTheme()
    }

    private fun onShowSearchRes(res: List<ForumUser>) {
        themeCreator?.onShowSearchRes(res)
    }

    private fun setStyleType(type: String) {
        webView.evalJs("changeStyleType(\"$type\")")
    }

    private fun showChat(data: QmsChatModel) {
        refreshToolbarMenuItems(true)
        setTitles(data.title.orEmpty(), data.nick.orEmpty())
        if (presenter.expectedVisibleMessageCount() <= 0) {
            qmsPendingWebRender = false
            cancelQmsWebRenderTimeout()
        }
        updateLoadingIndicator()
    }

    private fun setTitles(title: String, nick: String) {
        setSubtitle(nick)
        setTitle(title)
        setTabTitle(String.format(getString(R.string.fragment_tab_title_chat), title, nick))
    }

    private fun onNewThemeCreate(data: QmsChatModel) {
        messagePanel.clearMessage()
        messagePanel.clearAttachments()
    }

    private fun canReuseQmsWebShellForDialogSwitch(): Boolean {
        if (_chatBinding == null || !::webView.isInitialized || !isAdded || view == null) {
            return false
        }
        reconcileQmsDomReadyWithBridge()
        return qmsDomReady && webView.isJsReady
    }

    /**
     * Switches dialog without reloading the HTML shell (saves ~1–2s vs [loadBaseWebContainer]).
     * Clears the previous thread via JS; new messages are injected when [loadChat] completes.
     */
    private fun prepareQmsChatSwitchWithoutReload() {
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        if (::webView.isInitialized) {
            // Drop any watchdog/dom-probe runnables queued for the previous generation —
            // they would otherwise fire after the dialog switch and strand `domReady=false`
            // on the new generation (see log 14_06-16-33: 319 dom_not_ready events).
            webView.removeCallbacks(qmsContentWatchdogRunnable)
        }
        qmsLoadGeneration++
        val generation = qmsLoadGeneration
        qmsLoadChatKey = currentQmsChatKey()
        qmsRecoveryAttemptCount = 0
        qmsBlankRetryCount = 0
        qmsLayoutWaitAttempts = 0
        qmsDomReady = false
        qmsWebMessagesApplied = false
        qmsDomProbeSucceededPendingFlush = false
        qmsContentWatchdogAttempt = 0
        qmsContentWatchdogGeneration = qmsLoadGeneration
        qmsScrollToBottomOnRender = false
        qmsPendingWebRender = presenter.themeId > 0 &&
                presenter.userId != QmsChatModel.NOT_CREATED
        synchronized(pendingQmsJs) {
            pendingQmsJs.clear()
        }
        pendingQmsShowMessages = null
        qmsShowMessagesFlushPosted = false
        updateLoadingIndicator()
        if (qmsPendingWebRender) {
            scheduleQmsWebRenderTimeout()
        }
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearQueuedJs()
            webView.scrollTo(0, 0)
        }
        logQmsWebView("switch_fast", generation, mapOf("themeId" to presenter.themeId))
        FpdaPipelineLog.qmsWebViewStage(
                stage = "webview_switch_fast",
                traceId = presenter.traceIdForDiagnostics(),
                dialogId = presenter.themeId,
                userId = presenter.userId,
                requestId = generation,
                extra = mapOf("pendingRender" to qmsPendingWebRender)
        )
        evalJsWhenReady(
                """(function(){
                    if(typeof resetQmsMessageList==='function'){resetQmsMessageList();}
                    if(typeof resetQmsScrollPosition==='function'){resetQmsScrollPosition();}
                })();"""
        )
        verifyQmsDomReady(generation, "switch_fast")
        if (qmsPendingWebRender) {
            scheduleQmsRenderVerify()
        }
    }

    //Chat
    private fun loadBaseWebContainer() {
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        qmsLoadGeneration++
        val generation = qmsLoadGeneration
        qmsLoadChatKey = currentQmsChatKey()
        qmsDomReady = false
        qmsBasePageFinished = false
        qmsShellLoadDispatched = false
        qmsWebMessagesApplied = false
        qmsDomProbeSucceededPendingFlush = false
        qmsContentWatchdogAttempt = 0
        qmsPendingWebRender = presenter.hasLoadedMessages() && presenter.expectedVisibleMessageCount() > 0
        qmsBlankRetryCount = 0
        qmsLayoutWaitAttempts = 0
        qmsRecoveryAttemptCount = 0
        qmsWebRenderErrorShownGeneration = -1
        qmsScrollToBottomOnRender = false
        updateLoadingIndicator()
        if (qmsPendingWebRender) {
            scheduleQmsWebRenderTimeout()
        }
        synchronized(pendingQmsJs) {
            pendingQmsJs.clear()
        }
        pendingQmsShowMessages = null
        qmsShowMessagesFlushPosted = false
        detachQmsLayoutResyncListener()
        detachQmsShellLayoutDispatchListener()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.clearQueuedJs()
            webView.scrollTo(0, 0)
        }
        evalJsWhenReady("if(typeof resetQmsScrollPosition==='function'){resetQmsScrollPosition();}")
        logQmsWebView("load_base_container", generation, mapOf("themeId" to presenter.themeId))
        FpdaPipelineLog.qmsWebViewStage(
                stage = "webview_load_started",
                traceId = presenter.traceIdForDiagnostics(),
                dialogId = presenter.themeId,
                userId = presenter.userId,
                requestId = generation,
                extra = mapOf("pendingRender" to qmsPendingWebRender)
        )
        qmsShellHtml = qmsChatTemplate.generateHtmlBase()
        scheduleQmsShellLoad(generation)
        scheduleQmsDomReadyWatchdog(generation)
    }

    private fun scheduleQmsShellLoad(generation: Int) {
        if (!::webView.isInitialized) return
        webView.post { dispatchQmsShellLoad(generation) }
    }

    private fun dispatchQmsShellLoad(generation: Int, forceTabShown: Boolean = false) {
        if (!isCurrentQmsLoad(generation) || !::webView.isInitialized || qmsShellLoadDispatched) return
        if (!isAdded || view == null) {
            webView.post { dispatchQmsShellLoad(generation, forceTabShown) }
            return
        }
        if (!forceTabShown && QmsWebRenderPolicy.shouldDeferShellLoadUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            logQmsChat("defer_shell_load", mapOf("generation" to generation))
            webView.post { dispatchQmsShellLoad(generation, forceTabShown) }
            return
        }
        if (WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webView.width, webView.height)) {
            scheduleQmsShellLayoutDispatch(generation)
            return
        }
        qmsBasePageFinished = false
        qmsShellLoadDispatched = true
        webView.loadDataWithBaseURL("https://4pda.to/forum/", qmsShellHtml, "text/html", "utf-8", null)
    }

    private var qmsShellLayoutDispatchGeneration = -1
    private var qmsShellLayoutDispatchListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private fun scheduleQmsShellLayoutDispatch(generation: Int) {
        if (!::webView.isInitialized || !isCurrentQmsLoad(generation) || qmsShellLoadDispatched) return
        if (!WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webView.width, webView.height)) {
            dispatchQmsShellLoad(generation)
            return
        }
        if (qmsShellLayoutDispatchGeneration == generation && qmsShellLayoutDispatchListener != null) {
            return
        }
        detachQmsShellLayoutDispatchListener()
        qmsShellLayoutDispatchGeneration = generation
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!::webView.isInitialized || !isCurrentQmsLoad(generation) || qmsShellLoadDispatched) {
                detachQmsShellLayoutDispatchListener()
                return@OnGlobalLayoutListener
            }
            if (WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webView.width, webView.height)) {
                return@OnGlobalLayoutListener
            }
            detachQmsShellLayoutDispatchListener()
            dispatchQmsShellLoad(generation)
        }
        qmsShellLayoutDispatchListener = listener
        webView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun detachQmsShellLayoutDispatchListener() {
        val listener = qmsShellLayoutDispatchListener ?: return
        if (::webView.isInitialized) {
            val observer = webView.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(listener)
            }
        }
        qmsShellLayoutDispatchListener = null
        qmsShellLayoutDispatchGeneration = -1
    }

    private val qmsDomReadyWatchdogRunnable = Runnable {
        val generation = qmsDomReadyWatchdogGeneration
        if (!isAdded || view == null || generation != qmsLoadGeneration || qmsDomReady) return@Runnable
        if (!::webView.isInitialized) return@Runnable
        logQmsWebView(
                "dom_ready_watchdog",
                generation,
                mapOf(
                        "jsReady" to webView.isJsReady,
                        "basePageFinished" to qmsBasePageFinished,
                )
        )
        reconcileQmsDomReadyWithBridge()
        if (QmsWebRenderPolicy.shouldDispatchShellOnDomWatchdog(qmsShellLoadDispatched, qmsDomReady)) {
            dispatchQmsShellLoad(generation)
            scheduleQmsDomReadyWatchdog(generation)
            return@Runnable
        }
        if (QmsWebRenderPolicy.shouldReloadOnDomWatchdog(
                        qmsDomReady,
                        webView.isJsReady,
                        qmsShellLoadDispatched
                ) ||
                QmsWebRenderPolicy.shouldFastReloadOnDomWatchdog(qmsBasePageFinished, qmsDomReady)
        ) {
            tryQmsRenderAutoRecovery(generation, "dom_watchdog_reload")
            return@Runnable
        }
        verifyQmsDomReady(generation, "watchdog")
    }

    private var qmsDomReadyWatchdogGeneration = 0

    private fun scheduleQmsDomReadyWatchdog(generation: Int) {
        if (!::webView.isInitialized) return
        qmsDomReadyWatchdogGeneration = generation
        webView.removeCallbacks(qmsDomReadyWatchdogRunnable)
        webView.postDelayed(qmsDomReadyWatchdogRunnable, QmsWebRenderPolicy.DOM_READY_WATCHDOG_MS)
    }

    private fun cancelQmsPendingWebCallbacks() {
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        if (!::webView.isInitialized) return
        webView.removeCallbacks(qmsDomReadyWatchdogRunnable)
        webView.handler?.removeCallbacksAndMessages(null)
    }


    private fun onNewMessages(items: List<QmsMessage>, forceScroll: Boolean) {
        Timber.d("Returned messages %d", items.size)
        if (items.isEmpty()) return
        showMessagesInWebView(items, forceScroll = forceScroll, clearExisting = false)
    }

    private fun resetAndShowMessages(items: List<QmsMessage>, clearExisting: Boolean) {
        if (items.isEmpty()) return
        showMessagesInWebView(items, forceScroll = true, clearExisting = clearExisting)
    }

    private fun showInitialMessages(items: List<QmsMessage>) {
        if (items.isEmpty()) return
        showMessagesInWebView(items, forceScroll = true, clearExisting = true)
    }

    private fun showMessagesInWebView(items: List<QmsMessage>, forceScroll: Boolean, clearExisting: Boolean) {
        if (items.isEmpty()) return
        val pending = pendingQmsShowMessages
        pendingQmsShowMessages = if (pending == null) {
            PendingQmsShowMessages(items, forceScroll, clearExisting)
        } else {
            PendingQmsShowMessages(
                    items = items,
                    forceScroll = forceScroll || pending.forceScroll,
                    clearExisting = clearExisting || pending.clearExisting,
            )
        }
        scheduleFlushPendingQmsShowMessages()
    }

    private fun scheduleFlushPendingQmsShowMessages() {
        if (!::webView.isInitialized || pendingQmsShowMessages == null) return
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            scheduleQmsContentWatchdog()
            return
        }
        if (qmsShowMessagesFlushPosted) return
        qmsShowMessagesFlushPosted = true
        webView.post {
            qmsShowMessagesFlushPosted = false
            val batch = pendingQmsShowMessages ?: return@post
            pendingQmsShowMessages = null
            injectShowMessagesInWebView(batch.items, batch.forceScroll, batch.clearExisting)
        }
    }

    private fun injectShowMessagesInWebView(items: List<QmsMessage>, forceScroll: Boolean, clearExisting: Boolean) {
        if (items.isEmpty()) return
        qmsPendingWebRender = true
        qmsWebMessagesApplied = false
        if (!isNetworkRefreshing) {
            scheduleQmsWebRenderTimeout()
        }
        updateLoadingIndicator()
        val html = qmsChatTemplate.generate(items)
        FpdaPipelineLog.qmsWebViewStage(
                stage = "dom_inject_started",
                traceId = presenter.traceIdForDiagnostics(),
                dialogId = presenter.themeId,
                userId = presenter.userId,
                requestId = qmsLoadGeneration,
                extra = mapOf(
                        "messageCount" to items.size,
                        "htmlLen" to html.length,
                        "forceScroll" to forceScroll,
                        "clearExisting" to clearExisting,
                )
        )
        if (forceScroll) {
            qmsScrollToBottomOnRender = true
        }
        evalJsWhenReady(buildQmsShowMessagesJs(html, forceScroll, clearExisting))
        if (forceScroll) {
            scheduleScrollQmsToBottom()
        }
        scheduleQmsRenderVerify()
    }

    private fun buildQmsShowMessagesJs(html: String, forceScroll: Boolean, clearExisting: Boolean): String {
        val messagesArg = TempHelper.transformMessageSrc(html)
        return QmsWebRenderProbe.buildShowMessagesScript(messagesArg, forceScroll, clearExisting)
    }

    private fun scheduleScrollQmsToBottom() {
        if (!::webView.isInitialized || !isAdded || view == null) return
        // Reset any stale interrupt: this is a fresh forced scroll the user expects.
        webView.beginAutoScrollToBottom()
        val scrollJs =
                "if(typeof scrollQmsToBottomWithRetries==='function'){scrollQmsToBottomWithRetries();}"
        for (delayMs in QMS_SCROLL_BOTTOM_NATIVE_DELAYS_MS) {
            webView.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                // Stand down the moment the user grabs the scroll, so the multi-stage pass
                // does not fight a manual drag/fling (jitter / "won't scroll" symptom).
                if (webView.isAutoScrollSuppressedByUser()) return@postDelayed
                webView.evalJs(scrollJs)
                scrollQmsWebViewNativeToBottom()
            }, delayMs)
        }
    }

    private fun scrollQmsWebViewNativeToBottom() {
        if (!::webView.isInitialized || !isAdded || view == null) return
        webView.post {
            if (!isAdded || view == null) return@post
            if (webView.isAutoScrollSuppressedByUser()) return@post
            val maxScroll = ((webView.contentHeight * webView.scale).toInt() - webView.height)
                    .coerceAtLeast(0)
            if (maxScroll > 0) {
                webView.scrollTo(0, maxScroll)
            }
        }
    }

    private fun setMessageRefreshing(isRefreshing: Boolean) {
        messagePanel.setProgressState(isRefreshing)
    }

    private fun setNewMessagesRefreshing(isRefreshing: Boolean) {
        if (::refreshMenuItem.isInitialized && !presenter.refreshing.value) {
            refreshMenuItem.isEnabled = !isRefreshing
        }
    }

    private fun onSentMessage(items: List<QmsMessage>) {
        if (!items.isEmpty() && items[0].content != null) {
            //Empty because result returned from websocket
            messagePanel.clearMessage()
            messagePanel.clearAttachments()
        }
    }

    private fun sendMessage() {
        //addUnusedAttachments()
        presenter.sendMessage(messagePanel.message, attachmentsPopup?.getAttachments() ?: emptyList())
    }

    private fun requestClearMessagePanelText() {
        if (!isAdded || view == null || !::messagePanel.isInitialized) return
        if (messagePanel.messageField.visibility != View.VISIBLE || messagePanel.isInputBlocked()) return
        if (messagePanel.message.isBlank()) return
        if (clearMessagePanelTextDialog?.isShowing == true) return

        clearMessagePanelTextDialog = MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.editor_clear_text_confirm_message)
                .setPositiveButton(R.string.editor_clear_text_confirm_positive) { _, _ ->
                    clearMessagePanelTextConfirmed()
                }
                .setNegativeButton(R.string.editor_clear_text_confirm_negative, null)
                .showWithStyledButtons()
                .also { dialog ->
                    dialog.setOnDismissListener {
                        if (clearMessagePanelTextDialog === dialog) {
                            clearMessagePanelTextDialog = null
                        }
                    }
                }
    }

    private fun clearMessagePanelTextConfirmed() {
        if (!isAdded || view == null || !::messagePanel.isInitialized) return
        messagePanel.clearMessage()
    }

    private fun showAvatar(avatarUrl: String) {
        toolbarImageView.contentDescription = getString(R.string.user_avatar)
        toolbarImageView.setOnClickListener { presenter.openProfile() }
        ForPdaCoil.loadInto(toolbarImageView, avatarUrl)
        toolbarImageView.visibility = View.VISIBLE
    }

    private fun onBlockUser(res: Boolean) {
        if (res) {
            showSnackbar(R.string.user_added_to_blacklist)
        }
    }

    private fun showCreateNote(name: String, nick: String, url: String) {
        val title = String.format(getString(R.string.dialog_Title_Nick), name, nick)
        NotesAddPopup.showCreateBookmarkDialog(context, title, url, notesRepository)
    }

    private fun showMoreMessages(items: List<QmsMessage>, startIndex: Int, endIndex: Int) {
        val html = qmsChatTemplate.generate(items, startIndex, endIndex)
        val messagesArg = TempHelper.transformMessageSrc(html)
        evalJsWhenReady("showMoreMess($messagesArg)")
    }

    private fun makeAllRead() {
        evalJsWhenReady("makeAllRead();")
    }

    private var qmsDomReady = false
    private var qmsBasePageFinished = false
    private var qmsShellLoadDispatched = false
    private var qmsShellHtml: String = ""
    private var qmsBlankRetryCount = 0
    private var qmsLayoutWaitAttempts = 0
    private var qmsLoadGeneration = 0
    private var qmsLoadChatKey = ""
    private var qmsWebMessagesApplied = false
    private var qmsPendingWebRender = false
    private var isNetworkRefreshing = false
    /**
     * One-shot: scroll to bottom on the next successful render only for forced loads
     * (initial open, dialog switch, user-sent / explicit new messages). Prevents incidental
     * re-renders (resume, refresh re-verify, recovery) from yanking the user away from
     * messages they scrolled up to read.
     */
    private var qmsScrollToBottomOnRender = false
    private var pendingQmsShowMessages: PendingQmsShowMessages? = null
    private var qmsShowMessagesFlushPosted = false
    /** DOM probe succeeded while tab hidden — flush on [onResumeOrShow], not a second probe. */
    private var qmsDomProbeSucceededPendingFlush = false
    private var qmsContentWatchdogAttempt = 0
    private var qmsContentWatchdogGeneration: Int = 0
    /** Soft resend / reload attempts per [qmsLoadGeneration] before showing «Обновить». */
    private var qmsRecoveryAttemptCount = 0
    /** Suppress duplicate error overlay / snackbar for the same WebView generation. */
    private var qmsWebRenderErrorShownGeneration = -1
    private var qmsDomReadyProbeGeneration: Int? = null
    private var qmsDomReadyProbeSource = ""
    private var qmsDomReadyProbeToken = 0
    private val pendingQmsJs = mutableListOf<PendingQmsJs>()
    private val qmsWebRenderTimeoutRunnable = Runnable { onQmsWebRenderTimeout() }
    private val qmsRenderVerifyRunnable = Runnable { runQmsRenderVerifyPass() }
    private var qmsLayoutResyncListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private val qmsContentWatchdogRunnable = Runnable {
        if (qmsWebMessagesApplied) {
            qmsContentWatchdogAttempt = 0
            return@Runnable
        }
        // Generation guard: a watchdog scheduled for a previous dialog/load must not act on the
        // current one (would loop dom_not_ready forever, see log 14_06-16-33).
        if (qmsContentWatchdogGeneration != qmsLoadGeneration) {
            qmsContentWatchdogAttempt = 0
            return@Runnable
        }
        qmsContentWatchdogAttempt++
        ensureQmsMessagesVisible("content_watchdog")
        if (QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        presenter.hasLoadedMessages(),
                        qmsWebMessagesApplied,
                        qmsContentWatchdogAttempt
                )
        ) {
            scheduleQmsContentWatchdog()
        }
    }

    private fun isCurrentQmsLoad(generation: Int): Boolean {
        return isCurrentQmsLoad(generation, qmsLoadChatKey)
    }

    private fun isCurrentQmsLoad(generation: Int, chatKey: String): Boolean {
        return generation == qmsLoadGeneration &&
                chatKey == qmsLoadChatKey &&
                chatKey == currentQmsChatKey() &&
                isAdded &&
                view != null &&
                ::webView.isInitialized
    }

    private fun evalJsWhenReady(js: String) {
        val generation = qmsLoadGeneration
        val chatKey = currentQmsChatKey()
        val preview = if (js.length > 80) js.substring(0, 80) + "…" else js
        reconcileQmsDomReadyWithBridge()
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            synchronized(pendingQmsJs) {
                pendingQmsJs.add(PendingQmsJs(generation, chatKey, js))
            }
            logQmsChat("defer_web_pipeline_eval", mapOf("queueSize" to pendingQmsJs.size))
            scheduleQmsContentWatchdog()
            return
        }
        if (!qmsDomReady) {
            synchronized(pendingQmsJs) {
                pendingQmsJs.add(PendingQmsJs(generation, chatKey, js))
            }
            FpdaPipelineLog.qmsWebView(
                    "eval_js_queued",
                    mapOf(
                            "generationId" to generation,
                            "reason" to "dom_not_ready",
                            "queueSize" to pendingQmsJs.size,
                            "jsPreview" to preview
                    )
            )
            return
        }
        if (!::webView.isInitialized) return
        if (!webView.isJsReady) {
            acknowledgeQmsJsBridgeFromDomProbe()
            if (webView.isJsReady) {
                execQmsJsAfterLayout(qmsLoadGeneration, js)
                FpdaPipelineLog.qmsWebView(
                        "eval_js_exec",
                        mapOf(
                                "generationId" to generation,
                                "reason" to "bridge_ack_from_probe",
                                "jsPreview" to preview
                        )
                )
                return
            }
            synchronized(pendingQmsJs) {
                pendingQmsJs.add(PendingQmsJs(generation, chatKey, js))
            }
            FpdaPipelineLog.qmsWebView(
                    "eval_js_queued",
                    mapOf(
                            "generationId" to generation,
                            "reason" to "bridge_not_ready",
                            "queueSize" to pendingQmsJs.size,
                            "jsPreview" to preview
                    )
            )
            verifyQmsDomReady(qmsLoadGeneration, "bridge_not_ready_eval")
            webView.syncWithJs { flushPendingQmsJsWhenBridgeReady() }
            return
        }
        FpdaPipelineLog.qmsWebView(
                "eval_js_exec",
                mapOf(
                        "generationId" to generation,
                        "reason" to "ready",
                        "jsPreview" to preview
                )
        )
        execQmsJsAfterLayout(generation, js)
    }

    private fun reconcileQmsDomReadyWithBridge() {
        if (!::webView.isInitialized) return
        val reconciled = QmsWebRenderPolicy.reconcileDomReadyFlag(qmsDomReady, webView.isJsReady)
        if (reconciled != qmsDomReady) {
            logQmsWebView(
                    "dom_ready_stale_bridge",
                    qmsLoadGeneration,
                    mapOf("jsReady" to webView.isJsReady, "wasDomReady" to qmsDomReady)
            )
            qmsDomReady = reconciled
        }
    }

    private fun acknowledgeQmsJsBridgeFromDomProbe() {
        if (!::webView.isInitialized || webView.isJsReady) return
        logQmsWebView("bridge_ack_from_probe", qmsLoadGeneration, emptyMap())
        webView.acknowledgeJsBridgeFromNativeProbe()
    }

    private fun execQmsJsAfterLayout(generation: Int, js: String) {
        if (!::webView.isInitialized || !isAdded || view == null) return
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            synchronized(pendingQmsJs) {
                pendingQmsJs.add(PendingQmsJs(generation, currentQmsChatKey(), js))
            }
            scheduleQmsContentWatchdog()
            return
        }
        val run = Runnable {
            if (!isCurrentQmsLoad(generation)) return@Runnable
            execQmsJsWithInjectFeedback(generation, js)
            if (js.contains("showNewMess(")) {
                scheduleQmsRenderVerify()
            }
        }
        if (!QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(webView.width, webView.height)) {
            run.run()
            return
        }
        logQmsWebView(
                "inject_defer_layout",
                generation,
                mapOf("width" to webView.width, "height" to webView.height)
        )
        webView.post {
            if (!isCurrentQmsLoad(generation)) return@post
            if (!QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(webView.width, webView.height)) {
                run.run()
                return@post
            }
            val observer = webView.viewTreeObserver
            observer.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (webView.width <= 0 || webView.height <= 0) return
                    webView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    run.run()
                }
            })
        }
    }

    private fun execQmsJsWithInjectFeedback(generation: Int, js: String) {
        if (!isCurrentQmsLoad(generation)) return
        if (js.contains("showNewMess(")) {
            webView.evaluateJavascript(js) { raw ->
                if (!isCurrentQmsLoad(generation)) return@evaluateJavascript
                val count = parseJsIntResult(raw)
                val expected = presenter.expectedVisibleMessContainerCount()
                if (count >= 0 && (expected <= 0 || QmsWebRenderProbe.isMessagesRendered(count, expected))) {
                    FpdaPipelineLog.qmsWebViewStage(
                            stage = "dom_inject_success",
                            traceId = presenter.traceIdForDiagnostics(),
                            dialogId = presenter.themeId,
                            userId = presenter.userId,
                            requestId = generation,
                            extra = mapOf("containers" to count, "expectedContainers" to expected)
                    )
                } else {
                    FpdaPipelineLog.qmsWebViewWarn(
                            "dom_inject_failed",
                            qmsLogFields(
                                    mapOf(
                                            "traceId" to presenter.traceIdForDiagnostics(),
                                            "dialogId" to presenter.themeId,
                                            "requestId" to generation,
                                            "containers" to count,
                                            "expectedContainers" to expected,
                                            "reasonCode" to "inject_weak_count",
                                    )
                            )
                    )
                    logQmsWebView(
                            "show_messages_inject_weak",
                            generation,
                            mapOf("containers" to count, "expectedContainers" to expected)
                    )
                    if (QmsWebRenderProbe.shouldResendOnZeroInjectCount(
                                    count,
                                    expected,
                                    presenter.hasLoadedMessages()
                            )
                    ) {
                        logQmsChat(
                                "parse_ok_zero_containers_resend",
                                mapOf("expectedContainers" to expected, "generation" to generation)
                        )
                        presenter.resendVisibleMessagesToWeb(clearExisting = true)
                        scheduleQmsRenderVerify()
                    } else {
                        verifyQmsDomReady(generation, "show_messages_inject_weak")
                    }
                }
            }
            return
        }
        execQmsJs(js)
    }

    private fun flushPendingQmsJsWhenBridgeReady() {
        if (!qmsDomReady || !::webView.isInitialized) return
        acknowledgeQmsJsBridgeFromDomProbe()
        if (!webView.isJsReady) return
        val generation = qmsLoadGeneration
        val chatKey = currentQmsChatKey()
        val pending: List<String>
        synchronized(pendingQmsJs) {
            pending = pendingQmsJs
                    .filter { it.generation == generation && it.chatKey == chatKey }
                    .map { it.js }
            pendingQmsJs.removeAll { it.generation <= generation || it.chatKey != chatKey }
        }
        val scripts = collapsePendingShowNewMessScripts(pending)
        if (scripts.isEmpty()) return
        logQmsChat("flush_pending_js", mapOf("count" to scripts.size, "jsReady" to webView.isJsReady))
        execPendingQmsScriptsAfterLayout(generation, scripts)
    }

    /** Keep only the last full reset batch; incremental OnNewMessages must not be dropped. */
    fun collapsePendingShowNewMessScripts(scripts: List<String>): List<String> =
            QmsWebRenderProbe.collapsePendingFullResetShowNewMessScripts(scripts)

    private fun execPendingQmsScriptsAfterLayout(generation: Int, scripts: List<String>) {
        if (scripts.isEmpty()) return
        execQmsJsAfterLayout(generation, scripts.joinToString(separator = ""))
    }

    private fun execQmsJs(js: String) {
        if (!::webView.isInitialized) return
        if (qmsDomReady) {
            webView.evaluateJavascript(js, null)
            return
        }
        if (webView.isJsReady) {
            webView.evalJs(js)
        } else {
            webView.syncWithJs { webView.evalJs(js) }
        }
        webView.flushQueuedJs()
    }

    override fun onDomContentComplete(actions: ArrayList<String>) {
        val generation = qmsLoadGeneration
        if (isCurrentQmsLoad(generation)) {
            qmsBasePageFinished = true
        }
        verifyQmsDomReady(generation, "domContentLoaded")
    }

    private fun verifyQmsDomReady(generation: Int, source: String, attempt: Int = 0) {
        if (!isCurrentQmsLoad(generation)) {
            StateRaceTrace.log(
                    domain = "qms_webview",
                    event = "stale_ignored",
                    generation = generation,
                    currentGeneration = qmsLoadGeneration,
                    reason = source
            )
            return
        }
        if (qmsDomReady && ::webView.isInitialized && !webView.isJsReady) {
            // WebView resets isJsReady on detach/attach; native qmsDomReady must not block re-sync.
            qmsDomReady = false
        }
        if (!QmsWebRenderPolicy.shouldStartDomReadyProbe(
                        activeGeneration = qmsDomReadyProbeGeneration,
                        requestedGeneration = generation,
                        domReady = qmsDomReady
                )
        ) {
            if (!qmsDomReady && attempt == 0) {
                logQmsWebView(
                        "dom_probe_coalesced",
                        generation,
                        mapOf(
                                "source" to source,
                                "activeSource" to qmsDomReadyProbeSource
                        )
                )
            }
            return
        }
        qmsDomReadyProbeGeneration = generation
        qmsDomReadyProbeSource = source
        qmsDomReadyProbeToken++
        runQmsDomReadyProbe(generation, source, attempt, qmsDomReadyProbeToken)
    }

    private fun runQmsDomReadyProbe(generation: Int, source: String, attempt: Int, token: Int) {
        if (!QmsWebRenderPolicy.isCurrentDomReadyProbe(
                        qmsDomReadyProbeGeneration,
                        qmsDomReadyProbeToken,
                        generation,
                        token
                )
        ) {
            return
        }
        if (!isCurrentQmsLoad(generation)) {
            clearQmsDomReadyProbe(generation, token)
            return
        }
        if (qmsDomReady) {
            clearQmsDomReadyProbe(generation, token)
            return
        }
        webView.evaluateJavascript(QmsWebRenderProbe.domReadyProbeScript()) { raw ->
            if (!QmsWebRenderPolicy.isCurrentDomReadyProbe(
                            qmsDomReadyProbeGeneration,
                            qmsDomReadyProbeToken,
                            generation,
                            token
                    )
            ) {
                return@evaluateJavascript
            }
            if (!isCurrentQmsLoad(generation)) {
                clearQmsDomReadyProbe(generation, token)
                return@evaluateJavascript
            }
            if (!parseJsBooleanResult(raw)) {
                if (attempt > 0 &&
                        attempt % 4 == 0 &&
                        qmsShellLoadDispatched &&
                        qmsBasePageFinished &&
                        !webView.isJsReady
                ) {
                    webView.evaluateJavascript(QmsWebRenderProbe.bootstrapMissedDomContentLoadedScript(), null)
                }
                logQmsWebView(
                        "dom_not_ready",
                        generation,
                        mapOf("source" to source, "attempt" to attempt)
                )
                if (!QmsWebRenderPolicy.shouldGiveUpDomReady(attempt)) {
                    webView.postDelayed({
                        runQmsDomReadyProbe(generation, source, attempt + 1, token)
                    }, QmsWebRenderPolicy.domReadyDelayMs(attempt))
                } else {
                    clearQmsDomReadyProbe(generation, token)
                    if (!tryQmsRenderAutoRecovery(generation, "dom_ready_timeout")) {
                        abandonQmsWebRender(
                                generation,
                                "dom_ready_timeout",
                                showError = true
                        )
                    }
                }
                return@evaluateJavascript
            }
            clearQmsDomReadyProbe(generation, token)
            completeQmsDomReady(generation, source)
        }
    }

    private fun clearQmsDomReadyProbe(generation: Int, token: Int = qmsDomReadyProbeToken) {
        if (qmsDomReadyProbeGeneration == generation && qmsDomReadyProbeToken == token) {
            qmsDomReadyProbeGeneration = null
            qmsDomReadyProbeSource = ""
        }
    }

    /** Drop an in-flight probe so recovery / reload can start a fresh chain. */
    private fun resetQmsDomReadyProbe() {
        qmsDomReadyProbeGeneration = null
        qmsDomReadyProbeSource = ""
        qmsDomReadyProbeToken++
    }

    private fun completeQmsDomReady(generation: Int, source: String) {
        if (!isCurrentQmsLoad(generation) || qmsDomReady) return
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            // Probe succeeded but tab is hidden/paused — do not leave the probe active (coalescing
            // would block re-entry) and flush when the tab is shown instead of spinning DOM retries.
            qmsDomProbeSucceededPendingFlush = true
            clearQmsDomReadyProbe(generation)
            logQmsChat("defer_dom_ready_flush", mapOf("source" to source, "hidden" to isHidden))
            scheduleQmsContentWatchdog()
            return
        }
        acknowledgeQmsJsBridgeFromDomProbe()
        markQmsDomReadyAndFlush(generation, source)
    }

    private fun markQmsDomReadyAndFlush(generation: Int, source: String) {
        if (!isCurrentQmsLoad(generation) || qmsDomReady) return
        qmsDomReady = true
        val chatKey = currentQmsChatKey()
        val pending: List<String>
        synchronized(pendingQmsJs) {
            pending = pendingQmsJs
                    .filter { it.generation == generation && it.chatKey == chatKey }
                    .map { it.js }
            pendingQmsJs.removeAll { it.generation <= generation || it.chatKey != chatKey }
        }
        logQmsWebView(
                "dom_ready",
                generation,
                mapOf(
                        "source" to source,
                        "pendingJs" to pending.size,
                        "expectedMessages" to presenter.expectedVisibleMessageCount(),
                        "expectedContainers" to presenter.expectedVisibleMessContainerCount(),
                        "jsReady" to webView.isJsReady
                )
        )
        if (pendingQmsShowMessages != null) {
            scheduleFlushPendingQmsShowMessages()
        }
        if (pending.isNotEmpty()) {
            execPendingQmsScriptsAfterLayout(generation, pending)
        }
        val pendingHasShowNewMess = pending.any { it.contains("showNewMess(") }
        if (QmsWebRenderPolicy.shouldDomReadyResyncAfterFlush(
                        presenter.hasLoadedMessages(),
                        qmsWebMessagesApplied,
                        pendingShowBatch = pendingQmsShowMessages != null,
                        pendingHasShowNewMess = pendingHasShowNewMess
                )
        ) {
            logQmsChat("dom_ready_resend", mapOf("source" to source, "pendingJs" to pending.size))
            ensureQmsMessagesVisible("dom_ready_$source")
        } else if (qmsPendingWebRender || presenter.hasLoadedMessages()) {
            scheduleQmsRenderVerify()
        }
        if (QmsWebRenderPolicy.shouldForceMessageResync(
                        presenter.hasLoadedMessages(),
                        qmsWebMessagesApplied
                )
        ) {
            scheduleQmsContentWatchdog()
        }
    }

    /**
     * Unified path for «data loaded, WebView still blank» — mirrors alone-tab [applyChatScreenFromNavigator]
     * resync but also runs on first open (where [onResumeOrShow] repair fires before load finishes).
     */
    private fun ensureQmsMessagesVisible(reason: String) {
        if (_chatBinding == null || !::webView.isInitialized || !isAdded || view == null) return
        flushPendingDomReadyIfTabShown(reason)
        if (!QmsWebRenderPolicy.shouldForceMessageResync(
                        presenter.hasLoadedMessages(),
                        qmsWebMessagesApplied
                )
        ) {
            return
        }
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            logQmsChat(
                    "defer_web_pipeline_hidden",
                    mapOf("reason" to reason, "hidden" to isHidden)
            )
            scheduleQmsContentWatchdog()
            return
        }
        if (isNetworkRefreshing) {
            webView.removeCallbacks(qmsContentWatchdogRunnable)
            webView.postDelayed(
                    qmsContentWatchdogRunnable,
                    QmsWebRenderPolicy.NETWORK_IDLE_RESYNC_DELAY_MS
            )
            return
        }
        scheduleFlushPendingQmsShowMessages()
        reconcileQmsDomReadyWithBridge()
        FpdaPipelineLog.qmsWebViewStage(
                stage = "ensure_messages_visible",
                traceId = presenter.traceIdForDiagnostics(),
                dialogId = presenter.themeId,
                userId = presenter.userId,
                requestId = qmsLoadGeneration,
                extra = mapOf(
                        "reason" to reason,
                        "width" to webView.width,
                        "height" to webView.height,
                        "domReady" to qmsDomReady,
                        "jsReady" to webView.isJsReady,
                        "pendingRender" to qmsPendingWebRender,
                )
        )
        if (QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(webView.width, webView.height)) {
            scheduleQmsLayoutResync(reason)
            return
        }
        if (!qmsPendingWebRender) {
            qmsPendingWebRender = true
            updateLoadingIndicator()
            scheduleQmsWebRenderTimeout()
        }
        if (qmsDomReady && webView.isJsReady) {
            presenter.resendVisibleMessagesToWeb(clearExisting = !qmsWebMessagesApplied)
            verifyQmsRenderedOrRetry()
        } else {
            verifyQmsDomReady(qmsLoadGeneration, "ensure_visible_$reason")
        }
    }

    private fun scheduleQmsContentWatchdog() {
        if (!::webView.isInitialized || !isAdded || view == null) return
        if (!QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        presenter.hasLoadedMessages(),
                        qmsWebMessagesApplied,
                        qmsContentWatchdogAttempt
                )
        ) {
            return
        }
        val attempt = qmsContentWatchdogAttempt
        val delayMs = QmsWebRenderPolicy.contentWatchdogDelayMs(attempt)
        qmsContentWatchdogGeneration = qmsLoadGeneration
        webView.removeCallbacks(qmsContentWatchdogRunnable)
        webView.postDelayed(qmsContentWatchdogRunnable, delayMs)
    }

    private fun flushPendingDomReadyIfTabShown(reason: String) {
        if (!qmsDomProbeSucceededPendingFlush || qmsDomReady) return
        if (QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = isAdded,
                        isHidden = isHidden,
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
        ) {
            return
        }
        val generation = qmsLoadGeneration
        if (!isCurrentQmsLoad(generation)) return
        qmsDomProbeSucceededPendingFlush = false
        logQmsChat("dom_ready_flush_on_show", mapOf("reason" to reason))
        acknowledgeQmsJsBridgeFromDomProbe()
        markQmsDomReadyAndFlush(generation, "tab_show_$reason")
    }

    private fun scheduleQmsLayoutResync(reason: String) {
        if (!::webView.isInitialized || qmsLayoutResyncListener != null) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!::webView.isInitialized || !isAdded || view == null) return@OnGlobalLayoutListener
            if (QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(webView.width, webView.height)) {
                return@OnGlobalLayoutListener
            }
            detachQmsLayoutResyncListener()
            logQmsChat(
                    "layout_resync",
                    mapOf(
                            "reason" to reason,
                            "width" to webView.width,
                            "height" to webView.height
                    )
            )
            ensureQmsMessagesVisible("layout_$reason")
        }
        qmsLayoutResyncListener = listener
        webView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun detachQmsLayoutResyncListener() {
        val listener = qmsLayoutResyncListener ?: return
        if (::webView.isInitialized) {
            val observer = webView.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(listener)
            }
            webView.removeCallbacks(qmsContentWatchdogRunnable)
        }
        qmsLayoutResyncListener = null
    }

    private fun scheduleQmsRenderVerify() {
        if (!::webView.isInitialized || !isAdded || view == null) return
        webView.removeCallbacks(qmsRenderVerifyRunnable)
        webView.postDelayed(qmsRenderVerifyRunnable, QmsWebRenderPolicy.verifyDelayMs(qmsBlankRetryCount))
    }

    private fun cancelQmsRenderVerify() {
        if (::webView.isInitialized) {
            webView.removeCallbacks(qmsRenderVerifyRunnable)
        }
    }

    private fun verifyQmsRenderedOrRetry() {
        scheduleQmsRenderVerify()
    }

    private fun runQmsRenderVerifyPass() {
        if (!::webView.isInitialized || !isAdded || view == null) return
        val generation = qmsLoadGeneration
        val expectedContainers = presenter.expectedVisibleMessContainerCount()
        if (expectedContainers <= 0) {
            if (qmsPendingWebRender) {
                qmsPendingWebRender = false
                cancelQmsWebRenderTimeout()
                updateLoadingIndicator()
            }
            return
        }
        reconcileQmsDomReadyWithBridge()
        val deferReason = QmsWebRenderPolicy.shouldDeferVerify(
                networkRefreshing = isNetworkRefreshing,
                webViewWidth = webView.width,
                webViewHeight = webView.height,
                qmsDomReady = qmsDomReady,
                jsBridgeReady = webView.isJsReady,
                layoutWaitAttempts = qmsLayoutWaitAttempts
        )
        if (deferReason != null) {
            when (deferReason) {
                QmsWebRenderPolicy.VerifyDeferReason.WEBVIEW_NOT_LAYED_OUT -> {
                    qmsLayoutWaitAttempts++
                    logQmsWebView(
                            "render_wait_layout",
                            generation,
                            mapOf(
                                    "attempt" to qmsLayoutWaitAttempts,
                                    "width" to webView.width,
                                    "height" to webView.height
                            )
                    )
                    if (QmsWebRenderPolicy.shouldGiveUpLayoutWait(qmsLayoutWaitAttempts) &&
                            tryQmsRenderAutoRecovery(generation, "layout_wait_timeout")
                    ) {
                        return
                    }
                }
                QmsWebRenderPolicy.VerifyDeferReason.DOM_NOT_READY,
                QmsWebRenderPolicy.VerifyDeferReason.JS_BRIDGE_NOT_READY -> {
                    verifyQmsDomReady(generation, "render_verify_wait_${deferReason.name.lowercase()}")
                }
                QmsWebRenderPolicy.VerifyDeferReason.NETWORK_REFRESHING -> Unit
            }
            scheduleQmsRenderVerify()
            return
        }
        qmsLayoutWaitAttempts = 0
        webView.evaluateJavascript(QmsWebRenderProbe.renderProbeScript()) { raw ->
            if (!isCurrentQmsLoad(generation)) return@evaluateJavascript
            if (isNetworkRefreshing) {
                scheduleQmsRenderVerify()
                return@evaluateJavascript
            }
            val probe = QmsWebRenderProbe.parseRenderProbe(raw)
            val count = probe.containerCount
            if (!probe.domReady || !probe.bridgeReady) {
                if (probe.bridgeReady) {
                    acknowledgeQmsJsBridgeFromDomProbe()
                }
                if (!probe.domReady) {
                    qmsDomReady = false
                    verifyQmsDomReady(generation, "render_probe_not_ready")
                    scheduleQmsRenderVerify()
                    return@evaluateJavascript
                }
                if (!probe.bridgeReady) {
                    verifyQmsDomReady(generation, "render_probe_bridge")
                    scheduleQmsRenderVerify()
                    return@evaluateJavascript
                }
            }
            if (QmsWebRenderProbe.isMessagesRendered(count, expectedContainers)) {
                completeQmsRenderSuccess(generation, count, "render_verify")
                return@evaluateJavascript
            }
            if (count == 0 && expectedContainers > 0 && presenter.hasLoadedMessages()) {
                logQmsWebView(
                        "render_zero_containers_resend",
                        generation,
                        mapOf("expectedContainers" to expectedContainers, "retry" to qmsBlankRetryCount)
                )
                if (qmsDomReady && webView.isJsReady) {
                    presenter.resendVisibleMessagesToWeb(clearExisting = true)
                } else {
                    verifyQmsDomReady(generation, "render_zero_containers")
                }
                scheduleQmsRenderVerify()
                return@evaluateJavascript
            }
            if (QmsWebRenderPolicy.shouldGiveUpBlankRender(qmsBlankRetryCount)) {
                FpdaPipelineLog.qmsWebViewWarn(
                        "render_verify_failed",
                        qmsLogFields(
                                mapOf(
                                        "traceId" to presenter.traceIdForDiagnostics(),
                                        "dialogId" to presenter.themeId,
                                        "requestId" to generation,
                                        "reasonCode" to "render_give_up",
                                        "retry" to qmsBlankRetryCount,
                                        "expectedContainers" to expectedContainers,
                                        "domMessages" to count,
                                        "contentHeight" to webView.contentHeight,
                                        "bridgeReady" to probe.bridgeReady,
                                        "messList" to probe.messListPresent,
                                )
                        )
                )
                abandonQmsWebRender(generation, "render_give_up", showError = true)
                logQmsWebView(
                        "render_give_up",
                        generation,
                        mapOf(
                                "retry" to qmsBlankRetryCount,
                                "expectedContainers" to expectedContainers,
                                "contentHeight" to webView.contentHeight,
                                "bridgeReady" to probe.bridgeReady,
                                "messList" to probe.messListPresent
                        )
                )
                return@evaluateJavascript
            }
            qmsBlankRetryCount++
            logQmsWebView(
                    "render_blank_retry",
                    generation,
                    mapOf(
                            "retry" to qmsBlankRetryCount,
                            "expectedContainers" to expectedContainers,
                            "domMessages" to count,
                            "contentHeight" to webView.contentHeight,
                            "domReady" to qmsDomReady,
                            "jsReady" to webView.isJsReady,
                            "bridgeReady" to probe.bridgeReady
                    )
            )
            if (!qmsDomReady || !webView.isJsReady) {
                verifyQmsDomReady(generation, "blankRetry")
            } else {
                presenter.resendVisibleMessagesToWeb(clearExisting = true)
            }
            scheduleQmsRenderVerify()
        }
    }

    private fun scheduleQmsWebRenderTimeout() {
        if (!::webView.isInitialized) return
        webView.removeCallbacks(qmsWebRenderTimeoutRunnable)
        webView.postDelayed(qmsWebRenderTimeoutRunnable, QmsWebRenderPolicy.WEB_RENDER_TIMEOUT_MS)
    }

    private fun cancelQmsWebRenderTimeout() {
        if (::webView.isInitialized) {
            webView.removeCallbacks(qmsWebRenderTimeoutRunnable)
        }
    }

    private fun onQmsWebRenderTimeout() {
        if (!qmsPendingWebRender) return
        val generation = qmsLoadGeneration
        logQmsWebView(
                "render_timeout",
                generation,
                mapOf(
                        "domReady" to qmsDomReady,
                        "applied" to qmsWebMessagesApplied,
                        "networkRefreshing" to isNetworkRefreshing
                )
        )
        if (isNetworkRefreshing) {
            scheduleQmsWebRenderTimeout()
            return
        }
        if (!qmsDomReady) {
            verifyQmsDomReady(generation, "render_timeout")
            scheduleQmsWebRenderTimeout()
            return
        }
        if (!qmsWebMessagesApplied && presenter.hasLoadedMessages() &&
                tryQmsRenderAutoRecovery(generation, "render_timeout")
        ) {
            scheduleQmsWebRenderTimeout()
            return
        }
        abandonQmsWebRender(generation, "render_timeout", showError = true)
    }

    /**
     * @return true if recovery was started or should defer (network still loading).
     */
    private fun tryQmsRenderAutoRecovery(generation: Int, reason: String): Boolean {
        if (!isCurrentQmsLoad(generation)) return true
        if (isNetworkRefreshing) return true
        val shellStuck = QmsWebRenderPolicy.shouldRecoverShellWithoutMessages(
                shellLoadDispatched = qmsShellLoadDispatched,
                basePageFinished = qmsBasePageFinished,
                domReady = qmsDomReady,
                jsBridgeReady = ::webView.isInitialized && webView.isJsReady
        )
        if (!presenter.hasLoadedMessages() && !shellStuck) return false
        if (qmsRecoveryAttemptCount >= MAX_QMS_RECOVERY_PER_GENERATION) return false
        qmsRecoveryAttemptCount++
        qmsBlankRetryCount = 0
        qmsLayoutWaitAttempts = 0
        qmsPendingWebRender = true
        if (!qmsShellLoadDispatched && ::webView.isInitialized) {
            logQmsWebView(
                    "dispatch_deferred_shell",
                    generation,
                    mapOf("reason" to reason, "source" to "auto_recovery")
            )
            dispatchQmsShellLoad(generation)
            scheduleQmsDomReadyWatchdog(generation)
            if (presenter.hasLoadedMessages()) {
                presenter.syncLoadedChatToUi(clearExisting = true)
            }
            scheduleQmsWebRenderTimeout()
            return true
        }
        logQmsChatWarn(
                "auto_recovery",
                mapOf(
                        "reason" to reason,
                        "domReady" to qmsDomReady,
                        "jsReady" to webView.isJsReady,
                        "basePageFinished" to qmsBasePageFinished,
                )
        )
        resetQmsDomReadyProbe()
        if (QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                        qmsBasePageFinished,
                        qmsDomReady,
                        webView.isJsReady
                )
        ) {
            // If the base shell never reached onPageFinished and the content watchdog has already
            // attempted recovery several times, reloading the container again spins the
            // dom_not_ready loop (log 14_06-16-33) without progress. Escalate to the persistent
            // error overlay so the user gets a clear «Обновить» instead of an endless load.
            if (!qmsBasePageFinished && qmsContentWatchdogAttempt > 2) {
                logQmsWebView(
                        "abandon_base_page_finished_timeout",
                        generation,
                        mapOf(
                                "reason" to reason,
                                "watchdogAttempt" to qmsContentWatchdogAttempt
                        )
                )
                abandonQmsWebRender(generation, "base_page_finished_timeout", showError = true)
                return true
            }
            logQmsWebView(
                    "reload_base_container",
                    generation,
                    mapOf("reason" to reason, "source" to "auto_recovery")
            )
            loadBaseWebContainer()
            if (presenter.hasLoadedMessages()) {
                presenter.syncLoadedChatToUi(clearExisting = true)
            }
            return true
        }
        if (!qmsDomReady || !webView.isJsReady) {
            verifyQmsDomReady(qmsLoadGeneration, "auto_recovery_$reason", attempt = 0)
        } else {
            presenter.resendVisibleMessagesToWeb(clearExisting = true)
        }
        scheduleQmsWebRenderTimeout()
        return true
    }

    private fun completeQmsRenderSuccess(generation: Int, containerCount: Int, source: String) {
        if (!isCurrentQmsLoad(generation)) return
        qmsWebRenderErrorShownGeneration = -1
        hideQmsPersistentOverlays()
        acknowledgeQmsJsBridgeFromDomProbe()
        qmsBlankRetryCount = 0
        qmsContentWatchdogAttempt = 0
        qmsDomProbeSucceededPendingFlush = false
        qmsWebMessagesApplied = true
        qmsPendingWebRender = false
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        updateLoadingIndicator()
        refreshToolbarMenuItems(true)
        if (QmsWebRenderPolicy.shouldAutoScrollToBottom(
                        forceScrollRequested = qmsScrollToBottomOnRender,
                        userScrollSuppressed = ::webView.isInitialized && webView.isAutoScrollSuppressedByUser()
                )
        ) {
            qmsScrollToBottomOnRender = false
            scheduleScrollQmsToBottom()
        } else {
            // Consume so a later incidental re-render does not inherit a stale request.
            qmsScrollToBottomOnRender = false
        }
        logQmsWebView(
                "render_ok",
                generation,
                mapOf(
                        "source" to source,
                        "domMessages" to containerCount,
                        "expectedContainers" to presenter.expectedVisibleMessContainerCount(),
                        "contentHeight" to webView.contentHeight
                )
        )
        QmsOpenTiming.logRenderVisible(
                presenter.traceIdForDiagnostics(),
                presenter.themeId,
                presenter.userId,
                generation,
                containerCount
        )
    }

    private fun abandonQmsWebRender(generation: Int, reason: String, showError: Boolean) {
        if (!isCurrentQmsLoad(generation)) return
        if (showError && isNetworkRefreshing) {
            scheduleQmsWebRenderTimeout()
            return
        }
        if (showError && tryQmsRenderAutoRecovery(generation, reason)) {
            return
        }
        if (showError && presenter.hasLoadedMessages() && !shouldShowQmsWebRenderError()) {
            logQmsChat(
                    "defer_render_error",
                    mapOf(
                            "reason" to reason,
                            "watchdogAttempt" to qmsContentWatchdogAttempt,
                            "recoveryAttempt" to qmsRecoveryAttemptCount,
                    )
            )
            scheduleQmsContentWatchdog()
            scheduleQmsWebRenderTimeout()
            return
        }
        qmsPendingWebRender = false
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        updateLoadingIndicator()
        refreshToolbarMenuItems(!isNetworkRefreshing)
        logQmsWebView(reason, generation, mapOf("showError" to showError))
        if (!showError) return
        if (presenter.hasLoadedMessages()) {
            probeQmsRenderBeforeErrorOverlay(generation, reason)
            return
        }
        showQmsWebViewPersistentErrorAfterAbandon(generation, reason)
    }

    private fun probeQmsRenderBeforeErrorOverlay(generation: Int, reason: String) {
        if (!::webView.isInitialized || !isCurrentQmsLoad(generation)) return
        webView.evaluateJavascript(QmsWebRenderProbe.renderProbeScript()) { raw ->
            if (!isCurrentQmsLoad(generation)) return@evaluateJavascript
            val probe = QmsWebRenderProbe.parseRenderProbe(raw)
            val expected = presenter.expectedVisibleMessContainerCount()
            if (QmsWebRenderProbe.isMessagesRendered(probe.containerCount, expected) ||
                    QmsWebRenderProbe.hasVisibleMessages(probe.containerCount)
            ) {
                logQmsChat(
                        "render_abandon_recovered",
                        mapOf(
                                "reason" to reason,
                                "domMessages" to probe.containerCount,
                                "expectedContainers" to expected
                        )
                )
                completeQmsRenderSuccess(generation, probe.containerCount, "abandon_probe_$reason")
                return@evaluateJavascript
            }
            if (tryQmsRenderAutoRecovery(generation, "abandon_probe_$reason")) {
                scheduleQmsContentWatchdog()
                return@evaluateJavascript
            }
            showQmsWebViewPersistentErrorAfterAbandon(generation, reason)
        }
    }

    private fun showQmsWebViewPersistentErrorAfterAbandon(generation: Int, reason: String) {
        if (!isCurrentQmsLoad(generation)) return
        logQmsChatWarn("render_abandon", mapOf("reason" to reason))
        forpdateam.ru.forpda.diagnostic.QmsOpenLog.openError(
                traceId = presenter.traceIdForDiagnostics(),
                dialogId = presenter.themeId,
                userId = presenter.userId,
                requestId = generation,
                phase = "webview_$reason",
                errorReason = "WebViewRender",
                detail = "expected=${presenter.expectedVisibleMessContainerCount()}"
        )
        showQmsWebViewPersistentError(
                if (BuildConfig.DEBUG) {
                    "webview:$reason · expected=${presenter.expectedVisibleMessContainerCount()}"
                } else {
                    ""
                },
                reasonCode = reason,
        )
    }
    private fun parseJsBooleanResult(raw: String?): Boolean {
        return when (raw?.trim()?.removeSurrounding("\"")?.lowercase()) {
            "true", "1" -> true
            else -> false
        }
    }

    private fun parseJsIntResult(raw: String?): Int {
        return raw?.trim()
                ?.removeSurrounding("\"")
                ?.toIntOrNull()
                ?: 0
    }

    private fun logQmsWebView(event: String, generation: Int, extra: Map<String, Any?> = emptyMap()) {
        logQmsChat(event, extra + mapOf("generation" to generation), FpdaDebugLog.TAG_QMS_WEBVIEW)
    }

    private fun logQmsChat(
            event: String,
            extra: Map<String, Any?> = emptyMap(),
            tag: String = FpdaDebugLog.TAG_QMS_CHAT
    ) {
        if (!BuildConfig.DEBUG) return
        FpdaDebugLog.log(
                tag,
                event,
                qmsLogFields(extra)
        )
    }

    private fun logQmsChatWarn(event: String, extra: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.warn(FpdaDebugLog.TAG_QMS_CHAT, event, qmsLogFields(extra))
    }

    private fun qmsLogFields(extra: Map<String, Any?>): Map<String, Any?> = buildMap {
        put("chatId", presenter.themeId)
        put("userId", presenter.userId)
        put("generation", qmsLoadGeneration)
        put("domReady", qmsDomReady)
        putAll(extra)
    }

    private fun currentQmsChatKey(): String = qmsChatKey(presenter.userId, presenter.themeId)

    private fun recoverQmsWebViewAfterRendererGone(didCrash: Boolean) {
        if (_chatBinding == null || !isAdded) return
        cancelQmsWebRenderTimeout()
        cancelQmsRenderVerify()
        qmsLoadGeneration++
        qmsLoadChatKey = currentQmsChatKey()
        qmsDomReady = false
        qmsBasePageFinished = false
        qmsShellLoadDispatched = false
        qmsWebMessagesApplied = false
        qmsDomProbeSucceededPendingFlush = false
        qmsContentWatchdogAttempt = 0
        qmsPendingWebRender = presenter.hasLoadedMessages() && presenter.expectedVisibleMessageCount() > 0
        qmsBlankRetryCount = 0
        qmsLayoutWaitAttempts = 0
        qmsRecoveryAttemptCount = 0
        qmsScrollToBottomOnRender = false
        synchronized(pendingQmsJs) {
            pendingQmsJs.clear()
        }
        updateLoadingIndicator()
        logQmsChatWarn(
                "render_process_gone",
                mapOf("didCrash" to didCrash, "pendingRender" to qmsPendingWebRender)
        )
        if (::webView.isInitialized) {
            unregisterForContextMenu(webView)
            webView.removeJavascriptInterface(JS_INTERFACE)
            webView.removeBaseBridge()
            webView.setJsLifeCycleListener(null)
            chatBinding.qmsChatContainer.removeView(webView)
            webView.destroy()
        }
        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            it.init(WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE)
            it.enableBaseBridge()
        }
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                linkHandler,
                systemLinkHandler,
                router,
                clipboardHelper
        ))
        webView.setJsLifeCycleListener(this)
        webView.addJavascriptInterface(jsInterface, JS_INTERFACE)
        registerForContextMenu(webView)
        webView.webViewClient = QmsChatWebViewClient()
        webView.webChromeClient = CustomWebChromeClient()
        ViewCompat.setElevation(webView, 0f)
        ViewCompat.setTranslationZ(webView, 0f)
        chatBinding.qmsChatContainer.addView(webView, 0)
        topScroller = WebViewTopScroller(webView, appBarLayout)
        loadBaseWebContainer()
        if (presenter.hasLoadedMessages()) {
            presenter.syncLoadedChatToUi(clearExisting = true)
        } else {
            showQmsWebViewPersistentError(reasonCode = "init_no_cached_messages")
        }
    }

    override fun onPageComplete(actions: ArrayList<String>) {
        Timber.d("QMS onPageComplete")
        if (qmsPendingWebRender || presenter.hasLoadedMessages()) {
            scheduleQmsRenderVerify()
        }
    }

    /* ATTACHMENTS LOADER */

    fun uploadFiles(files: List<RequestFile>) {
        val pending = attachmentsPopup?.preUploadFiles(files) ?: emptyList()
        attachmentsPopup?.revealDuringUploadPreview()
        enqueueUpload(files, pending)
    }

    private fun enqueueUpload(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadQueue.addLast(files to pending)
        pumpUploadQueue()
    }

    private fun pumpUploadQueue() {
        if (uploadInProgress) return
        val next = uploadQueue.firstOrNull() ?: return
        uploadInProgress = true
        presenter.uploadFiles(next.first, next.second)
    }

    private fun onUploadFiles(items: List<AttachmentItem>) {
        attachmentsPopup?.onUploadFiles(items)
        uploadInProgress = false
        if (uploadQueue.isNotEmpty()) uploadQueue.removeFirst()
        pumpUploadQueue()
    }

    private fun tryPickFile() {
        pickFileLauncher.launch(FilePickHelper.pickFile(false))
    }

    override fun onBackPressed(): Boolean {
        super.onBackPressed()
        if (::messagePanel.isInitialized && messagePanel.onBackPressed()) {
            return true
        }
        if (attachmentsPopup?.dismiss() == true) {
            return true
        }
        if (::messagePanel.isInitialized && (messagePanel.message.isNotEmpty() || messagePanel.attachments.isNotEmpty())) {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.editpost_lose_changes)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        messagePanel.clearMessage()
                        messagePanel.clearAttachments()
                        router.exit()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showWithStyledButtons()
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized && !qmsShellLoadDispatched) {
            dispatchQmsShellLoad(qmsLoadGeneration, forceTabShown = true)
        }
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        reconcileQmsDomReadyWithBridge()
        flushPendingDomReadyIfTabShown("resume")
        if (!qmsShellLoadDispatched) {
            dispatchQmsShellLoad(qmsLoadGeneration, forceTabShown = true)
        }
        logQmsChat(
                "lifecycle_on_resume",
                mapOf(
                        "domReady" to qmsDomReady,
                        "generation" to qmsLoadGeneration,
                        "messagesApplied" to qmsWebMessagesApplied,
                        "pendingRender" to qmsPendingWebRender,
                        "pendingDomFlush" to qmsDomProbeSucceededPendingFlush,
                        "jsReady" to (::webView.isInitialized && webView.isJsReady),
                )
        )
        if (::messagePanel.isInitialized) {
            messagePanel.onResume()
        }
        if (::webView.isInitialized && presenter.hasLoadedMessages() && !qmsWebMessagesApplied) {
            qmsContentWatchdogAttempt = 0
            flushPendingQmsJsWhenBridgeReady()
            webView.syncWithJs { flushPendingQmsJsWhenBridgeReady() }
        }
        repairQmsWebViewIfNeeded()
        if (presenter.hasLoadedMessages() && !qmsWebMessagesApplied) {
            ensureQmsMessagesVisible("tab_shown")
            scheduleQmsContentWatchdog()
        }
        presenter.checkNewMessages()
    }

    private fun repairQmsWebViewIfNeeded() {
        if (_chatBinding == null || !::webView.isInitialized) return
        if (!presenter.hasLoadedMessages()) return
        if (isNetworkRefreshing) return
        reconcileQmsDomReadyWithBridge()
        // Initial load pipeline: messages queued, DOM shell still loading.
        if (QmsWebRenderPolicy.shouldKickDomVerifyOnRepair(qmsPendingWebRender, qmsDomReady)) {
            verifyQmsDomReady(qmsLoadGeneration, "repair_pending_dom")
            scheduleQmsWebRenderTimeout()
            scheduleQmsContentWatchdog()
            return
        }

        val bridgeOk = QmsWebRenderPolicy.reconcileDomReadyFlag(qmsDomReady, webView.isJsReady)
        if (qmsWebMessagesApplied && bridgeOk) return
        if (qmsPendingWebRender && bridgeOk) {
            verifyQmsRenderedOrRetry()
            return
        }
        if (qmsPendingWebRender) {
            verifyQmsDomReady(qmsLoadGeneration, "repair_pending")
            scheduleQmsWebRenderTimeout()
            return
        }

        logQmsChat(
                "repair_webview",
                mapOf(
                        "jsReady" to webView.isJsReady,
                        "domReady" to qmsDomReady,
                        "applied" to qmsWebMessagesApplied
                )
        )
        if (!bridgeOk) {
            if (QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                            qmsBasePageFinished,
                            qmsDomReady,
                            webView.isJsReady
                    )
            ) {
                loadBaseWebContainer()
            } else {
                verifyQmsDomReady(qmsLoadGeneration, "repair_bridge")
            }
        }
        ensureQmsMessagesVisible("repair_resume")
        scheduleQmsContentWatchdog()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        logQmsChat("lifecycle_on_pause", mapOf("generation" to qmsLoadGeneration))
        if (::messagePanel.isInitialized) {
            messagePanel.onPause()
        }
    }

    override fun onDestroy() {
        logQmsChat("lifecycle_on_destroy", mapOf("generation" to qmsLoadGeneration))
        super.onDestroy()
        if (::messagePanel.isInitialized) {
            messagePanel.onDestroy()
        }
        if (::webView.isInitialized) {
            unregisterForContextMenu(webView)
        }
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        if (::messagePanel.isInitialized) {
            messagePanel.hidePopupWindows()
        }
    }

    companion object {
        private val LOG_TAG = QmsChatFragment::class.java.simpleName
        private const val TAG_QMS_EMPTY = "QMS_EMPTY"
        private const val TAG_QMS_ERROR = "QMS_ERROR"
        private val JS_INTERFACE = "IChat"
        private const val QMS_AUTO_REFRESH_INTERVAL_MS = 60_000L
        private const val QMS_AUTO_REFRESH_MAX_INTERVAL_MS = 240_000L
        private const val MAX_QMS_RECOVERY_PER_GENERATION = 2
        private val QMS_SCROLL_BOTTOM_NATIVE_DELAYS_MS = longArrayOf(
                50L,
                1500L,
        )
        const val USER_ID_ARG = "USER_ID_ARG"
        const val USER_NICK_ARG = "USER_NICK_ARG"
        const val USER_AVATAR_ARG = "USER_AVATAR_ARG"
        const val THEME_ID_ARG = "THEME_ID_ARG"
        const val THEME_TITLE_ARG = "THEME_TITLE_ARG"
        private val attachmentPattern = Pattern.compile("\\[url=(https:\\/\\/.*?\\.ibb\\.co[^\\]]*?)\\]")
    }

    private inner class QmsChatWebViewClient : CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler) {
        override fun handleUri(view: WebView, uri: Uri): Boolean {
            if (super.handleUri(view, uri)) {
                return true
            }
            val resolved = QmsChatLinkNavigation.resolveInAppUrl(uri.toString()) ?: return false
            linkHandler.handle(resolved, router, emptyMap())
            return true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            val generation = qmsLoadGeneration
            if (!QmsWebRenderPolicy.shouldAcceptShellPageFinished(qmsShellLoadDispatched)) {
                logQmsWebView(
                        "page_finished_ignored",
                        generation,
                        mapOf(
                                "urlSanitized" to FpdaDebugLog.sanitizeUrl(url),
                                "shellLoadDispatched" to qmsShellLoadDispatched,
                        )
                )
                return
            }
            if (isCurrentQmsLoad(generation)) {
                qmsBasePageFinished = true
            }
            FpdaPipelineLog.qmsWebViewStage(
                    stage = "webview_page_finished",
                    traceId = presenter.traceIdForDiagnostics(),
                    dialogId = presenter.themeId,
                    userId = presenter.userId,
                    requestId = generation,
                    extra = mapOf(
                            "urlSanitized" to FpdaDebugLog.sanitizeUrl(url),
                            "domReady" to qmsDomReady,
                            "basePageFinished" to qmsBasePageFinished,
                    )
            )
            if (qmsDomReady || !isCurrentQmsLoad(generation)) return
            if (!webView.isJsReady) {
                webView.evaluateJavascript(
                        QmsWebRenderProbe.bootstrapMissedDomContentLoadedScript(),
                        null
                )
            }
            webView.postDelayed({
                if (!isCurrentQmsLoad(generation) || qmsDomReady) return@postDelayed
                verifyQmsDomReady(generation, "onPageFinished")
            }, QmsWebRenderPolicy.PAGE_FINISHED_DOM_VERIFY_DELAY_MS)
        }

        override fun onMainFrameLoadError(
                view: WebView,
                request: WebResourceRequest?,
                errorCode: Int,
                description: String?
        ) {
            if (!isAdded || view == null) return
            logQmsChatWarn(
                    "main_frame_error",
                    mapOf("errorCode" to errorCode, "description" to description?.take(120))
            )
            abandonQmsWebRender(qmsLoadGeneration, "main_frame_error", showError = true)
        }

        override fun onMainFrameHttpError(
                view: WebView,
                request: WebResourceRequest?,
                statusCode: Int
        ) {
            if (!isAdded || view == null || statusCode in 200..299) return
            logQmsChatWarn("main_frame_http_error", mapOf("statusCode" to statusCode))
            abandonQmsWebRender(qmsLoadGeneration, "main_frame_http_$statusCode", showError = true)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Timber.w(
                    "QMS WebView render process gone: didCrash=%s priorityAtExit=%d",
                    detail.didCrash(),
                    detail.rendererPriorityAtExit()
            )
            recoverQmsWebViewAfterRendererGone(detail.didCrash())
            return true
        }
    }

    private data class PendingQmsJs(
            val generation: Int,
            val chatKey: String,
            val js: String
    )

    private data class PendingQmsShowMessages(
            val items: List<QmsMessage>,
            val forceScroll: Boolean,
            val clearExisting: Boolean,
    )
}
