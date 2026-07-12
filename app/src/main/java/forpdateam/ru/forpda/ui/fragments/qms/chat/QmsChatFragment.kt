package forpdateam.ru.forpda.ui.fragments.qms.chat

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import timber.log.Timber
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatLinkNavigation
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatUiEvent
import forpdateam.ru.forpda.presentation.qms.chat.QmsChatViewModel
import forpdateam.ru.forpda.presentation.qms.chat.QmsLoadErrorKind
import forpdateam.ru.forpda.presentation.qms.chat.formatQmsDebugSnackbarMessage
import forpdateam.ru.forpda.presentation.qms.chat.QmsThreadUiState
import forpdateam.ru.forpda.presentation.qms.chat.QmsVisibleMessages
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.fragments.RecyclerTopScroller
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender.QmsChatItem
import forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender.QmsChatItemMapper
import forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender.QmsMessagesAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import java.util.*
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Created by radiationx on 25.08.16.
 *
 * Экран переписки QMS.
 *
 * Рендер — нативный: [RecyclerView] + [QmsMessagesAdapter], тела сообщений разбираются общим
 * `PostBodyRenderer`/`BodyBlockViewFactory` (тот же движок, что и у нативных постов форума).
 * WebView-конвейер (шаблон `template_qms_chat.html`, `qms.js`, JS-мост `IChat`, dom-ready пробы,
 * watchdog'и пустого рендера) удалён целиком: список — это обычное состояние, которое view
 * пере-диффит, поэтому «применять», проверять и переотправлять сообщения больше нечего.
 */
@AndroidEntryPoint
class QmsChatFragment : TabFragment(), ChatThemeCreator.ThemeCreatorInterface, TabTopScroller {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var clipboardHelper: ClipboardHelper

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
    private var _chatBinding: FragmentQmsChatBinding? = null
    private val chatBinding get() = checkNotNull(_chatBinding) { "Binding accessed after onDestroyView" }
    lateinit var messagePanel: MessagePanel
        private set
    private var attachmentsPopup: AttachmentsPopup? = null
    private var clearMessagePanelTextDialog: AlertDialog? = null

    private lateinit var messagesAdapter: QmsMessagesAdapter
    private lateinit var messagesLayoutManager: LinearLayoutManager
    private val itemMapper = QmsChatItemMapper()

    /** Set when the model asks for a jump to the newest message; consumed by the next list commit. */
    private var pendingScrollToBottom = false

    /** Guards the upward-pagination trigger against firing again before the window actually grows. */
    private var loadMoreRequested = false

    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false
    private var visibleFrameLayoutObserver: ViewTreeObserver? = null
    private var isNetworkRefreshing = false
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

    private lateinit var topScroller: RecyclerTopScroller

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _chatBinding = FragmentQmsChatBinding.inflate(inflater, fragmentContent, true)
        messagePanel = MessagePanel(requireContext(), fragmentContainer, messagePanelHost, false, mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder)
        attachmentsPopup = messagePanel.attachmentsPopup
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Pin the toolbar the way every other list screen does. The WebView the list replaced kept the
        // toolbar visible by forcing its own elevation/translationZ to 0; a RecyclerView is added to the
        // CoordinatorLayout after the AppBarLayout and, at equal Z, simply painted over it — the avatar,
        // title and refresh action vanished behind the topmost message. pinStaticOpaqueToolbar() raises
        // the app bar (bringToFront + elevation) on top of clearing the scroll flags.
        pinStaticOpaqueToolbar()
        setListsBackground()
        messagePanelHost.setBackgroundColor(requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest))

        setupMessagesList()

        // Keep the «Вставить» / «В спойлер» controls enabled in QMS too: uploaded files are still
        // registered on the message via the `attaches` field, so inserting [attachment=id:name]
        // (optionally wrapped in [spoiler]) makes 4PDA render that file inline (inside the spoiler)
        // instead of appending it below. QMS bodies use the same PostBodyRenderer as forum posts,
        // so the spoiler renders natively. Restores the pre-native-rewrite «В спойлер» option.
        attachmentsPopup?.setEnabledTextControls(true)
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

        messagePanel.addSendOnClickListener { presenter.onSendClick() }
        messagePanel.setClearMessageClickListener { requestClearMessagePanelText() }

        messagePanel.heightChangeListener = MessagePanel.HeightChangeListener {
            keepListPinnedToBottomAfterRelayout()
        }

        messagePanel.disableBehavior()
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
                keepListPinnedToBottomAfterRelayout()
            }
            insets
        }
        attachVisibleFrameLayoutListener()

        topScroller = RecyclerTopScroller(chatBinding.qmsMessages, appBarLayout)

        observeViewModel()
    }

    /**
     * `stackFromEnd` pins the newest message to the bottom edge — the native equivalent of the
     * `scrollQmsToBottomWithRetries()` bootstrap the WebView needed (and of its image-load retries,
     * since a RecyclerView re-lays out on its own when a late image changes a row's height).
     */
    /**
     * Message bodies carry the server's raw hrefs, which may be relative (`/forum/…`) or
     * protocol-relative (`//4pda.to/…`) — the WebView resolved those against its base URL. Native
     * TextView links have no base, so absolutise them (and drop `#`/`javascript:` hrefs) exactly as
     * the old WebViewClient did before handing off to the app router.
     */
    private val messageLinkHandler = object : ILinkHandler {
        override fun handle(inputUrl: String?, router: TabRouter?, args: Map<String, String>): Boolean {
            val url = resolve(inputUrl) ?: return false
            return linkHandler.handle(url, router ?: this@QmsChatFragment.router, args)
        }

        override fun handle(inputUrl: String?, router: TabRouter?): Boolean {
            val url = resolve(inputUrl) ?: return false
            return linkHandler.handle(url, router ?: this@QmsChatFragment.router)
        }

        override fun findScreen(url: String): String? = linkHandler.findScreen(url)

        private fun resolve(rawUrl: String?): String? =
                QmsChatLinkNavigation.resolveInAppUrl(rawUrl) ?: rawUrl?.takeIf { it.isNotBlank() }
    }

    private fun setupMessagesList() {
        messagesAdapter = QmsMessagesAdapter(
                messageLinkHandler,
                object : QmsMessagesAdapter.Listener {
                    override fun onImageClick(galleryUrls: List<String>, index: Int) =
                            openImageViewer(galleryUrls, index)

                    override fun onImageLongClick(imageUrl: String) =
                            forpdateam.ru.forpda.ui.fragments.theme.nativerender.ImageActionsMenu
                                    .show(requireContext(), imageUrl, systemLinkHandler, clipboardHelper)

                    override fun onMessageLongClick(anchor: View, item: QmsChatItem.Message) =
                            showMessageMenu(anchor, item)

                    override fun onDownloadLinkTap(url: String, fileName: String?) {
                        systemLinkHandler.handleDownload(url, fileName, requireContext())
                    }

                    override fun onLinkLongClick(url: String) =
                            forpdateam.ru.forpda.ui.fragments.theme.nativerender.LinkActionsMenu
                                    .show(requireContext(), url, systemLinkHandler, clipboardHelper)
                },
        )
        messagesAdapter.textScale = mainPreferencesHolder.getWebViewFontSize() / REFERENCE_FONT_SIZE
        messagesAdapter.animatedSmiles =
                forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).getAnimatedSmiles()
        messagesAdapter.flatBlocks =
                forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(requireContext()).getFlatPosts()
        messagesLayoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        chatBinding.qmsMessages.apply {
            layoutManager = messagesLayoutManager
            adapter = messagesAdapter
            clipToPadding = false
            // Same page tone the native topic list paints under its post cards, so the gaps between
            // bubbles and the strip under the last one match the rest of the app.
            setBackgroundColor(
                    requireContext().getColorFromAttr(
                            com.google.android.material.R.attr.colorSurfaceContainerLowest,
                    ),
            )
            itemAnimator = null // a chat re-diffs on every WS tick; animations only add jitter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) return
                    maybeLoadMoreMessages()
                }
            })
        }
        tuneListRecyclerView(chatBinding.qmsMessages)
    }

    /**
     * The list already ends exactly at the top of `message_panel_host` (they are siblings, and the
     * panel's growth reflows the content container), so it must NOT reserve the panel height as
     * bottom padding the way `ExtendedWebView.setPaddingBottom` had to — that double-counted the
     * panel and left a panel-sized void under the newest message. All that's needed when the panel
     * grows or the keyboard opens is to keep a reader who was at the bottom pinned there.
     */
    private fun keepListPinnedToBottomAfterRelayout() {
        if (_chatBinding == null || !isListAtBottom()) return
        chatBinding.qmsMessages.post {
            if (_chatBinding == null) return@post
            scrollMessagesToBottom()
        }
    }

    /** Scroll-to-top reveals the previous page of history (was `IChat.loadMoreMessages()` from qms.js). */
    private fun maybeLoadMoreMessages() {
        if (loadMoreRequested || !presenter.visibleMessages.value.hasMoreAbove) return
        if (messagesLayoutManager.findFirstVisibleItemPosition() > LOAD_MORE_TRIGGER_POSITION) return
        loadMoreRequested = true
        presenter.loadMoreMessages()
    }

    private fun applyMessagePanelImeInsets(insets: WindowInsetsCompat?) {
        imeInsetsController.applyMessagePanelImeInsets(insets)
    }

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
        _chatBinding = null
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }

    /**
     * Rebinds the alone QMS tab to another dialog (navigator reuses one [QmsChatFragment] instance).
     * The list follows [QmsChatViewModel.visibleMessages], so switching dialogs needs no view surgery.
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
        if (identityChanged) {
            presenter.onChatIdentityChanged()
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
                    }
                }
                launch {
                    presenter.visibleMessages.collect { window ->
                        bindMessages(window)
                    }
                }
                launch {
                    presenter.scrollToBottom.collect {
                        pendingScrollToBottom = true
                        scrollMessagesToBottom()
                    }
                }
                yield()
                presenter.start()
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

    /**
     * The single render path: map the window to items and let DiffUtil work out the delta. A prepended
     * history page keeps the anchor (RecyclerView holds the first visible child), while an appended
     * message follows the list only when the user is already reading at the bottom.
     */
    private fun bindMessages(window: QmsVisibleMessages) {
        if (_chatBinding == null) return
        loadMoreRequested = false
        val wasAtBottom = isListAtBottom()
        val items = itemMapper.map(window.messages)
        messagesAdapter.submitList(items) {
            if (_chatBinding == null) return@submitList
            if (pendingScrollToBottom || wasAtBottom) {
                pendingScrollToBottom = false
                scrollMessagesToBottom()
            }
            presenter.onMessagesRendered(items.size)
        }
    }

    /** True when the newest message is (nearly) on screen — i.e. the user is not reading history. */
    private fun isListAtBottom(): Boolean {
        if (_chatBinding == null || !::messagesLayoutManager.isInitialized) return true
        val itemCount = messagesAdapter.itemCount
        if (itemCount == 0) return true
        val last = messagesLayoutManager.findLastVisibleItemPosition()
        if (last == RecyclerView.NO_POSITION) return true
        return last >= itemCount - 1 - BOTTOM_STICK_SLACK
    }

    private fun scrollMessagesToBottom() {
        if (_chatBinding == null) return
        val lastIndex = messagesAdapter.itemCount - 1
        if (lastIndex < 0) return
        chatBinding.qmsMessages.scrollToPosition(lastIndex)
    }

    private fun openImageViewer(galleryUrls: List<String>, index: Int) {
        if (galleryUrls.isEmpty()) return
        val start = index.coerceIn(0, galleryUrls.size - 1)
        forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
                .startActivity(requireContext(), ArrayList(galleryUrls), start)
    }

    /** Long-press a bubble → copy its plain text (the WebView offered only the system text selection). */
    private fun showMessageMenu(anchor: View, item: QmsChatItem.Message) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_COPY_MESSAGE, 0, R.string.copy)
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == MENU_COPY_MESSAGE) {
                clipboardHelper.copyToClipboard(plainTextOf(item))
                showSnackbar(R.string.copied)
            }
            true
        }
        popup.show()
    }

    private fun plainTextOf(item: QmsChatItem.Message): String = runCatching {
        forpdateam.ru.forpda.common.Html
                .fromHtml(item.contentHtml, forpdateam.ru.forpda.common.Html.FROM_HTML_MODE_COMPACT, null, null)
                .toString()
                .trim()
    }.getOrDefault(item.contentHtml)

    private fun handleUiEvent(event: QmsChatUiEvent) {
        when (event) {
            is QmsChatUiEvent.SetFontSize -> setFontSize(event.fontSize)
            is QmsChatUiEvent.SetTitles -> setTitles(event.title, event.nick)
            is QmsChatUiEvent.SetChatMode -> setChatMode(event.mode)
            is QmsChatUiEvent.OnShowSearchRes -> onShowSearchRes(event.result)
            is QmsChatUiEvent.ShowChat -> showChat(event.chat)
            is QmsChatUiEvent.OnNewThemeCreate -> onNewThemeCreate(event.chat)
            is QmsChatUiEvent.OnSentMessage -> onSentMessage(event.messages)
            is QmsChatUiEvent.OnBlockUser -> onBlockUser(event.isBlocked)
            is QmsChatUiEvent.ShowAvatar -> showAvatar(event.url)
            is QmsChatUiEvent.OnUploadFiles -> onUploadFiles(event.files)
            is QmsChatUiEvent.ShowCreateNote -> showCreateNote(event.title, event.nick, event.url)
            is QmsChatUiEvent.TempSendNewTheme -> temp_sendNewTheme()
            is QmsChatUiEvent.TempSendMessage -> temp_sendMessage()
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
            is QmsThreadUiState.Content -> hideQmsPersistentOverlays()
        }
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
            canRetry: Boolean
    ) {
        val messageRes = when (kind) {
            QmsLoadErrorKind.NETWORK -> R.string.no_network
            QmsLoadErrorKind.SESSION -> R.string.qms_error_session
            QmsLoadErrorKind.CAPTCHA -> R.string.qms_error_captcha
            QmsLoadErrorKind.SERVER -> R.string.qms_error_server
            QmsLoadErrorKind.PARSER -> R.string.qms_error_parser
            QmsLoadErrorKind.UNKNOWN -> R.string.qms_error_unknown
        }
        val base = getString(messageRes)
        val snackbarMessage = formatQmsDebugSnackbarMessage(
                kind = kind,
                failureDetail = failureDetail,
                baseMessage = base,
                traceId = presenter.traceIdForDiagnostics(),
        )
        if (!contentController.contains(TAG_QMS_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_notifications)
                    .setTitle(messageRes)
                    .setDesc(messageRes)
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

    /** The app font-size preference maps to the body text scale (16 px = the reference 1.0). */
    private fun setFontSize(size: Int) {
        if (!::messagesAdapter.isInitialized) return
        messagesAdapter.textScale = size / REFERENCE_FONT_SIZE
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
        if (!::blackListMenuItem.isInitialized) return
        blackListMenuItem.isEnabled = enable
        noteMenuItem.isEnabled = enable
        toDialogsMenuItem.isEnabled = enable
        refreshMenuItem.isEnabled = enable
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        isNetworkRefreshing = isRefreshing
        updateLoadingIndicator()
        refreshToolbarMenuItems(!isRefreshing)
    }

    private fun updateLoadingIndicator() {
        if (_chatBinding == null) return
        chatBinding.progressBar.visibility = if (isNetworkRefreshing) View.VISIBLE else View.GONE
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

    private fun showChat(data: QmsChatModel) {
        refreshToolbarMenuItems(true)
        setTitles(data.title.orEmpty(), data.nick.orEmpty())
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

    private fun setMessageRefreshing(isRefreshing: Boolean) {
        messagePanel.setProgressState(isRefreshing)
    }

    private fun setNewMessagesRefreshing(isRefreshing: Boolean) {
        if (::refreshMenuItem.isInitialized && !presenter.refreshing.value) {
            refreshMenuItem.isEnabled = !isRefreshing
        }
    }

    private fun onSentMessage(items: List<QmsMessage>) {
        if (items.isNotEmpty() && items[0].content != null) {
            //Empty because result returned from websocket
            messagePanel.clearMessage()
            messagePanel.clearAttachments()
        }
    }

    private fun sendMessage() {
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

    private fun logQmsChat(event: String, extra: Map<String, Any?> = emptyMap()) {
        if (!BuildConfig.DEBUG) return
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_QMS_CHAT,
                event,
                buildMap {
                    put("chatId", presenter.themeId)
                    put("userId", presenter.userId)
                    putAll(extra)
                }
        )
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
        FilePickHelper.showAttachChooser(requireContext()) { intent -> pickFileLauncher.launch(intent) }
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

    // Экран чата QMS почти всегда перехватывает «назад» (панель сообщения,
    // подтверждение потери несохранённого текста/вложений) и открывается над
    // списком контактов — одиночной корневой вкладкой не бывает. Консервативно
    // true: без «домой»-анимации отсюда и без риска для логики панели/черновика.
    override fun hasBackHandling(): Boolean = true

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        logQmsChat("lifecycle_on_resume", mapOf("themeId" to presenter.themeId))
        if (::messagePanel.isInitialized) {
            messagePanel.onResume()
        }
        presenter.checkNewMessages()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        logQmsChat("lifecycle_on_pause")
        if (::messagePanel.isInitialized) {
            messagePanel.onPause()
        }
    }

    override fun onDestroy() {
        logQmsChat("lifecycle_on_destroy")
        super.onDestroy()
        if (::messagePanel.isInitialized) {
            messagePanel.onDestroy()
        }
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        if (::messagePanel.isInitialized) {
            messagePanel.hidePopupWindows()
        }
    }

    companion object {
        private const val TAG_QMS_EMPTY = "QMS_EMPTY"
        private const val TAG_QMS_ERROR = "QMS_ERROR"
        private const val QMS_AUTO_REFRESH_INTERVAL_MS = 60_000L
        private const val QMS_AUTO_REFRESH_MAX_INTERVAL_MS = 240_000L
        private const val MENU_COPY_MESSAGE = 1

        /** Font-size pref value that maps to textScale 1.0 (matches the native topic renderer). */
        private const val REFERENCE_FONT_SIZE = 16f

        /** Rows from the end still counted as «reading the newest message» for auto-follow. */
        private const val BOTTOM_STICK_SLACK = 2

        /** Reveal older history once the user scrolls within this many rows of the top. */
        private const val LOAD_MORE_TRIGGER_POSITION = 3

        const val USER_ID_ARG = "USER_ID_ARG"
        const val USER_NICK_ARG = "USER_NICK_ARG"
        const val USER_AVATAR_ARG = "USER_AVATAR_ARG"
        const val THEME_ID_ARG = "THEME_ID_ARG"
        const val THEME_TITLE_ARG = "THEME_TITLE_ARG"
    }
}
