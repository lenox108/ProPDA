package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import android.app.Activity
import android.text.Editable
import androidx.activity.result.contract.ActivityResultContracts
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.common.dedupeAttachmentsById
import forpdateam.ru.forpda.common.mergeAttachmentIdsFromPostText
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.theme.ThemeToolbarTitlePolicy
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeTabHost
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Native RecyclerView topic renderer (roadmap `native-topic-renderer.md`) — the sole engine for
 * [forpdateam.ru.forpda.navigation.Screen.Theme] since Фаза 7 (the legacy WebView engine was removed).
 *
 * This first slice deliberately loads a single page directly through [ThemeApi] — the real
 * network + [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParser] layer the plan
 * says to REUSE and NOT touch — instead of wiring the WebView-coupled [ThemeViewModel] state
 * machine. That proves the native rendering pipeline end-to-end (real 4pda HTML → parser →
 * posts → [PostBodyRenderer] → native views) on device. Pagination / infinite-scroll / the
 * full presenter contract are later Фаза-1/3 steps; this is the fail-fast, verify-early
 * shell the roadmap asks for.
 *
 * Implements [ThemeTabHost] so the [forpdateam.ru.forpda.ui.navigation.TabNavigator] can reuse
 * this tab and switch topics (e.g. tapping an in-topic link to a DIFFERENT topic) exactly as it
 * does for the WebView engine.
 */
@AndroidEntryPoint
class NativeTopicFragment : RecyclerFragment(), ThemeTabHost, TopicPostsAdapter.PostActionListener {

    @Inject
    lateinit var themeApi: ThemeApi

    @Inject
    lateinit var editPostApi: forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi

    @Inject
    lateinit var reputationApi: forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi

    @Inject
    lateinit var linkHandler: ILinkHandler

    @Inject
    lateinit var menuShortcutPinner: forpdateam.ru.forpda.model.interactors.other.MenuShortcutPinner

    /** Browser/download entry point — powers the image and download-link long-press menus. */
    @Inject
    lateinit var systemLinkHandler: forpdateam.ru.forpda.presentation.ISystemLinkHandler

    @Inject
    lateinit var clipboardHelper: forpdateam.ru.forpda.common.ClipboardHelper

    @Inject
    lateinit var eventsRepository: forpdateam.ru.forpda.model.repository.events.EventsRepository

    @Inject
    lateinit var themeUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase

    @Inject
    lateinit var mainPreferencesHolder: forpdateam.ru.forpda.model.preferences.MainPreferencesHolder

    @Inject
    lateinit var otherPreferencesHolder: forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder

    /** Reuses the WebView editor's send/upload/delete network logic (no ViewModel needed). */
    @Inject
    lateinit var editorUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase

    /** Logged-in context — drives the footer 👍/👎 visibility (parity with the WebView). */
    @Inject
    lateinit var authHolder: forpdateam.ru.forpda.model.AuthHolder

    /** Reuses the WebView toolbar navigation actions (open forum / search in topic / my posts). */
    @Inject
    lateinit var navigationUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeNavigationUseCase

    /** For the «Создать закладку» post-menu action (parity with the WebView createNote). */
    @Inject
    lateinit var notesRepository: forpdateam.ru.forpda.model.repository.note.NotesRepository

    /** Add/remove-from-favorites from the toolbar overflow (parity with the WebView fav menu). */
    @Inject
    lateinit var interactionUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeInteractionUseCase

    /**
     * Клиентская граница прочитанного (модель Discourse) — общая с WebView-движком. Ведём самый
     * дальний реально-виденный пост локально, чтобы при переоткрытии не сесть НИЖЕ непрочитанных,
     * когда серверный getnewpost/getlastpost уполз вниз (walk-down 4PDA/IPB). См.
     * [forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore].
     */
    @Inject
    lateinit var readBoundaryStore: forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore

    @Inject
    lateinit var notificationPreferencesHolder: forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder

    @Inject
    lateinit var postDraftRepository: forpdateam.ru.forpda.model.repository.draft.PostDraftRepository

    /** Дебаунс-сохранение черновика ответа в теме (ключ topic:<id>, общий с полноэкранным редактором). */
    private var topicDraftSaveJob: kotlinx.coroutines.Job? = null

    // topicPreferencesHolder is provided by the TabFragment supertype.

    private val mapper = NativePostMapper()
    private val anchorResolver = NativeAnchorResolver()
    private val pagination = TopicPaginationController()
    /**
     * Some posts embed an inline blue «ЖАЛОБА» image whose enclosing `<a>` points at
     * `index.php?act=report&t=…&p=…`. The global [linkHandler] doesn't recognise `act=report`, so those
     * body taps used to fall through to the external browser and the site's report form (user report
     * «жалоба открывает браузер вместо формы»). Wrap the handler for the post body: intercept those links
     * and route them into the same in-app report flow as the «Пожаловаться» post-menu action; everything
     * else delegates to the real handler unchanged.
     */
    private val reportAwareLinkHandler: ILinkHandler by lazy {
        object : ILinkHandler {
            override fun handle(inputUrl: String?, router: forpdateam.ru.forpda.presentation.TabRouter?, args: Map<String, String>): Boolean =
                    tryInterceptReportLink(inputUrl) || linkHandler.handle(inputUrl, router, args)

            override fun handle(inputUrl: String?, router: forpdateam.ru.forpda.presentation.TabRouter?): Boolean =
                    tryInterceptReportLink(inputUrl) || linkHandler.handle(inputUrl, router)

            override fun findScreen(url: String): String? = linkHandler.findScreen(url)
        }
    }

    /** True when [inputUrl] is an in-topic `act=report` link we handled in-app (see [reportAwareLinkHandler]). */
    private fun tryInterceptReportLink(inputUrl: String?): Boolean {
        val url = inputUrl ?: return false
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        if (!uri.getQueryParameter("act").equals("report", ignoreCase = true)) return false
        val postId = uri.getQueryParameter("p")?.toIntOrNull() ?: return false
        if (view == null) return false
        val item = loadedItems.firstOrNull { it.postId == postId } ?: return false
        tryReportPost(item)
        return true
    }

    /**
     * Wrap [delegate] so that whenever a link is tapped it first dismisses the open hat/poll overlay, then
     * delegates. Used only for the toolbar hat popup: a link there points to another topic or to another
     * page of THIS topic, and leaving the hat hanging over the destination forced a manual close before the
     * post was visible (user report «шапка висит поверх после перехода по ссылке»).
     */
    private fun overlayDismissingLinkHandler(delegate: ILinkHandler): ILinkHandler = object : ILinkHandler {
        override fun handle(inputUrl: String?, router: forpdateam.ru.forpda.presentation.TabRouter?, args: Map<String, String>): Boolean {
            dismissThemeOverlay()
            return delegate.handle(inputUrl, router, args)
        }

        override fun handle(inputUrl: String?, router: forpdateam.ru.forpda.presentation.TabRouter?): Boolean {
            dismissThemeOverlay()
            return delegate.handle(inputUrl, router)
        }

        override fun findScreen(url: String): String? = delegate.findScreen(url)
    }

    private val postsAdapter by lazy { TopicPostsAdapter(reportAwareLinkHandler, this) }
    private val pollHeaderAdapter = PollHeaderAdapter(
            voteListener = PollHeaderAdapter.PollVoteListener { action, method, encodedForm ->
                submitPoll(action, method, encodedForm)
            },
    )

    /** Adapter positions are shifted by the poll header (0 or 1) — offset scroll targets by this. */
    private fun headerOffset(): Int = pollHeaderAdapter.itemCount

    /** The accumulated posts across all loaded pages (source of truth for the adapter). */
    private val loadedItems = ArrayList<NativePostItem>()

    /**
     * Post ids already scanned for mention-clearing this topic session, so a post scrolled past
     * (or re-bound during infinite scroll) is not re-fed to [EventsRepository]. Reset per topic load.
     */
    private val mentionScannedPostIds = HashSet<Int>()

    /** Fire the end-of-topic mark-read exactly once per topic load (reset on a fresh load). */
    private var markedTopicReadAtEnd = false

    /** The URL actually loaded into the list (may differ from ARG_TAB after a navigator switch). */
    private var loadedUrl: String? = null

    /** The last URL handed to [loadTopic] (set even before the load completes) — dedupes the navigator's
     *  redundant echo of the initial open against [onViewCreated]'s resolved load. */
    private var lastRequestedUrl: String? = null

    /**
     * true, пока последний [loadTopic] ещё в полёте (гасится по завершении актуального запроса —
     * epoch-гард отсеивает вытесненные). Квалифицирует дедуп в [loadThemeUrlFromNavigator]: скипать
     * повторную загрузку того же URL можно ТОЛЬКО пока оригинал летит (эхо навигатора через миллисекунды
     * после onViewCreated). Раньше дедуп был безусловным — [lastRequestedUrl] не протухает, и повторное
     * открытие темы из списка спустя минуты (реюз живого таба, resolved совпал с прошлым getnewpost-URL)
     * МОЛЧА скипалось: ни сети, ни якоря — юзер видел старые посты, хотя избранное показывало новый
     * («тема не обновляется, пока не дёрнешь руками»). Плавающесть: тема, чьё прошлое открытие ушло в
     * findpost-резюм, переоткрывалась свежей (URL отличался), а «ровно севшая» — залипала.
     */
    private var loadInFlight = false
    private var isLoadingNextPage = false
    private var isLoadingPrevPage = false
    /** Bumped on every [loadTopic]. In-flight coroutines snapshot it at launch and drop their result if a
     *  full reload (refresh / topic switch via tab reuse) overtook them — otherwise a stale next/prev-page
     *  onSuccess would append the OLD topic's posts into the freshly reset list (registerAndFilterNew
     *  dedups by postId only and doesn't know the topic changed). */
    private var loadEpoch = 0
    /** True while auto-pulling previous pages to fill an under-filled last page (see maybeFillLastPage). */
    private var fillingLastPage = false

    /** Topic context for posting a reply (from the loaded page). */
    private var pageForumId = 0
    private var pageTopicId = 0
    private var pageSt = 0
    private var isSending = false

    /** Favorites state of the loaded topic, driving the overflow «Добавить/Убрать из избранного» item. */
    private var pageIsInFavorite = false
    private var pageFavId = 0

    /** Loaded-page flags driving the toolbar poll / hat icon visibility (see [refreshToolbarState]). */
    private var pageHasPoll = false
    private var pageHasHat = false
    /** Post id of the topic hat on the loaded page (rendered as a collapsed inline block on page 1). */
    private var topicHatPostId: Int? = null
    /** The hat post id once learned from page 1 — used to strip the server-repeated copy off deep pages. */
    private var knownHatPostId: Int? = null
    /**
     * The hat post content for the toolbar «Инфо» popup — captured whenever the hat is seen on ANY page
     * (page 1 keeps it inline; deep pages strip the echoed copy but we still hold it here). Persisted for
     * the whole topic so the ⓘ button works on every page, matching the WebView (topic-level hat state).
     */
    private var toolbarHatItem: NativePostItem? = null
    /** Inline hat collapse state. The initial value is resolved once per fresh topic open in
     *  [applyInitialHatCollapsedState] from the «Шапка темы при открытии» setting (per-topic override
     *  wins); afterwards it only follows the user's manual toggle for the rest of the session. */
    private var hatCollapsed: Boolean = true
    /** Topic id whose initial hat state was already applied — guards re-applying the global setting on
     *  in-session pagination/reload (which would clobber the user's manual toggle). Re-applies only when
     *  a genuinely different topic is opened (tab reuse). */
    private var hatInitialStateAppliedTopicId: Int = 0
    /** The topic's poll (parsed on page 1) — the «Опрос» toolbar button shows it in a popup. Cached at
     *  topic level so the button persists across pages once the poll page has been seen (WebView parity). */
    private var currentPoll: forpdateam.ru.forpda.entity.remote.theme.Poll? = null
    /** Topic id [currentPoll] belongs to — scopes the cached poll to the current topic. */
    private var cachedPollTopicId: Int? = null

    /** Popup showing the full topic title when the toolbar title is tapped (WebView parity). */
    private var topicTitlePopup: android.widget.PopupWindow? = null

    /** The full BBCode editor (formatting toolbar, smiles, attachments) — one-to-one with WebView. */
    private var messagePanel: MessagePanel? = null
    private var attachmentsPopup: AttachmentsPopup? = null
    /** Mirror of the editor field text, so a draft survives IME-driven view churn (cf. WebView). */
    private var messagePanelDraftMirror = ""
    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false
    /** Non-null when the editor is editing an existing post (else composing a new reply). */
    private var editingForm: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(requireContext(), data))
    }

    private var paginationBar: android.widget.LinearLayout? = null
    private var paginationLabel: TextView? = null

    // «Верхняя пагинация» (pref TOPIC_PAGINATION_PANELS, top bit) — the same «  ‹  N / M  ›  » row, pinned in
    // the AppBarLayout below the toolbar (parity with the WebView top pagination panel). Shown in ALL reading
    // modes; the bottom [paginationBar] (bottom bit) stays a CLASSIC-mode-only navigator.
    private var topPaginationBar: android.widget.LinearLayout? = null
    private var topPaginationLabel: TextView? = null

    private var searchBar: android.widget.LinearLayout? = null
    private var searchInput: android.widget.EditText? = null
    private var searchCountLabel: TextView? = null
    /** Adapter positions (poll-header-offset applied) of posts matching the current query, in order. */
    private val searchMatchPositions = ArrayList<Int>()
    private var currentMatchIndex = -1
    /** The 1-based page shown in the pagination bar (best-effort as the user scrolls / jumps). */
    private var barCurrentPage: Int = 1
    /** Set for an explicit page-jump so [applyInitialAnchor] lands on the page top, not unread/find. */
    private var pendingJumpToTop: Boolean = false

    /** Set for «В конец темы» so [applyInitialAnchor] lands on the last post of the last page. */
    private var pendingJumpToBottom: Boolean = false

    /**
     * Обновление жестом «снизу вверх» ([refreshFromBottom]): наибольший id поста, который пользователь
     * видел ДО перезагрузки. Если свежая страница принесла посты новее — [applyInitialAnchor] сядет на
     * первый из них вместо низа (см. [forpdateam.ru.forpda.presentation.theme.TopicRefreshAnchorPolicy]).
     * 0 = обычная загрузка, не обновление.
     */
    private var pendingRefreshSeenUpToPostId: Int = 0

    /**
     * Обновление снизу: разрешить дошагать до страницы с непрочитанным. Пока пользователь читал, тема
     * могла перевалить за границу страницы — тогда перезагрузка ТЕКУЩЕЙ страницы не приносит ничего
     * нового (она уже заполнена), а все непрочитанные лежат ниже. Живой замер на теме 1103268: клиент
     * видел `newCount=0`, тогда как на следующей странице ждали 20 непрочитанных постов.
     * Только HYBRID: в CLASSIC низ страницы — это не низ темы, и шагать вниз нельзя.
     */
    private var refreshFollowNextPageArmed: Boolean = false

    /**
     * Клиентская граница прочитанного: разрешить резюм-на-границу только один раз за открытие темы —
     * на ПЕРВОЙ успешной загрузке. Взводится в [onViewCreated] перед первым [loadTopic], гасится сразу
     * после проверки, чтобы последующие загрузки (пагинация, переходы по страницам, findpost-резюм)
     * не запускали override повторно и не зациклили.
     */
    private var boundaryResumeArmed: Boolean = false

    /**
     * Гейт мгновенного mark-read при открытии на первом непрочитанном (порт WebView-guard'а
     * [TopicUnreadOpenPolicy.shouldSuppressMarkReadForFirstUnreadOpen], который жил в недостижимом для
     * натива [ThemeUseCase.onPrimaryThemeLoaded]): короткая последняя страница показывает свой низ сразу
     * при открытии, и [maybeMarkTopicReadAtEnd] метил тему прочитанной (и стирал границу прочитанного) в
     * момент рендера — юзер ещё ничего не прочитал. Пока флаг взведён, mark-read-в-конце не срабатывает;
     * гасится первым ЖЕСТОМ пользователя (drag/fling/FAB — [onScrollStateChanged]): дальше «низ виден» —
     * снова честный сигнал «дочитал». Штампуется на каждой первичной загрузке из [ThemePage.openSessionKind].
     */
    private var suppressEndMarkReadUntilUserScroll: Boolean = false

    /**
     * Был ли в ТЕКУЩЕЙ сессии просмотра темы (с последнего первичного рендера) хотя бы один жест
     * скролла (drag/fling/FAB — см. [onScrollStateChanged]). Гейтит страховочную запись границы
     * прочитанного из [onPause]: граница пишется только по устоявшемуся вьюпорту ([recordReadBoundaryAtRest]
     * на IDLE), но выход из темы ВО ВРЕМЯ инерции (back/сворачивание, пока список settling) съедал
     * финальный IDLE — граница отставала на экран-полтора, и следующее открытие резюмило на уже
     * прочитанные посты. Писать из onPause БЕЗ жеста нельзя: «открыл-глянул-закрыл» записал бы
     * вьюпорт, который юзер, возможно, не читал (это отдельно решает dwell-гейт).
     */
    private var userScrollGestureThisSession: Boolean = false

    /**
     * Было ли в текущей сессии просмотра хотя бы одно ПРОТЯГИВАНИЕ (drag за touchSlop) по списку.
     * Дополняет [userScrollGestureThisSession] для [maybeMarkTopicReadAtEnd]: когда тема открывается уже
     * показывая свой конец (короткий непрочитанный хвост влез в экран), протяжка в самом низу — это
     * overscroll, и RecyclerView на устройстве может НЕ сменить scroll-state на DRAGGING (скроллить вниз
     * некуда) → флаг жеста не взводится. Ловим сам drag (ACTION_MOVE за slop), а НЕ простой тап: тап по
     * ссылке/аватару/лайку — не доказательство «долистал до конца» (иначе короткая тема у низа гасла бы от
     * случайного тапа, репорт-ревью). Вместе с «список упёрт в самый низ» = юзер тянул к концу. Сброс — в
     * [renderThemePage].
     */
    private var userDraggedListThisSession: Boolean = false

    /**
     * Момент ([android.os.SystemClock.elapsedRealtime]) последнего первичного рендера темы — начало
     * текущей сессии просмотра. Питает dwell-гейт [TopicNoGestureDwellReadPolicy]: сессия без жеста
     * считается дочиткой только если пользователь провёл на экране заметное время.
     */
    private var sessionRenderedAtMs: Long = 0L

    /**
     * Мостик того же гейта через findpost-резюм на границу прочитанного ([maybeResumeToReadBoundary]):
     * страница вложенной findpost-перезагрузки несёт hasUnreadTarget=false (парсер размечает unread
     * только для getnewpost), поэтому сам guard по ней не сработал бы — а резюм по построению сажает
     * юзера НА границу с непрочитанным ниже, и мгновенный mark-read сжёг бы именно спасённое.
     * Взводится перед вложенной перезагрузкой, потребляется (OR + сброс) при штамповке флага в onSuccess.
     */
    private var pendingSuppressEndMarkReadForResume: Boolean = false

    /**
     * Fallback для findpost-резюма к границе прочитанного ([maybeResumeToReadBoundary]). Перед вложенной
     * findpost-загрузкой сюда кладётся УЖЕ успешно распарсенная getnewpost-страница. Резюм — оптимизация
     * (сесть на непрочитанное вместо серверного «низа»), и он НЕ должен ронять открытие темы: если пост
     * границы удалён из темы, findpost падает исключением («Пост #… не найден») или отдаёт пустые посты —
     * тогда вместо пустого экрана с тостом рендерим этот fallback (тема открывается на серверном якоре).
     * Потребляется (рендер + сброс) при первом же завершении вложенной загрузки, успехом или ошибкой.
     */
    private var resumeFallbackPage: forpdateam.ru.forpda.entity.remote.theme.ThemePage? = null
    private var resumeFallbackUrl: String? = null

    /**
     * Findpost-резюм к границе прочитанного ([maybeResumeToReadBoundary]) — это АВТОМАТИЧЕСКАЯ посадка на
     * непрочитанное, а не осознанный тап по ссылке/цитате/поиску. Но вложенная findpost-перезагрузка
     * приходит в [applyInitialAnchor] с reason=FIND_POST (страница findpost не несёт unread-метаданных),
     * а FIND_POST-посадки вспыхивают ВСЕГДА (cue «куда я приземлился»), игнорируя «Подсветку
     * непрочитанного». Итог: при выключенной настройке переоткрытие всё равно подсвечивало пост резюма.
     * Взводится перед вложенной findpost-загрузкой, потребляется (capture + сброс) в [applyInitialAnchor]:
     * вспышка такой посадки подчиняется той же настройке, что и обычная посадка на первый непрочитанный.
     */
    private var pendingSilentResumeLanding: Boolean = false

    /**
     * Restore-scroll «где остановился» / устойчивость состояния: пост и его пиксельный offset, на который
     * надо вернуться после пересоздания фрагмента (смерть процесса, восстановление FragmentManager,
     * пересоздание вью). Пишутся в [onSaveInstanceState], читаются в [onViewCreated], применяются один
     * раз в [applyInitialAnchor] вместо серверного якоря. 0 = восстанавливать нечего (свежее открытие).
     */
    private var pendingRestorePostId: Int = 0
    private var pendingRestoreOffset: Int = 0

    /**
     * In-tab navigation history for «Поведение кнопки Назад в темах» = HISTORY: each time the navigator
     * reuses this tab for a NEW url (a link to another post/topic), we push where we were (url + scroll
     * anchor) so Back returns there instead of closing the tab. Empty → Back leaves the tab as before.
     */
    private data class ThemeBackEntry(val url: String, val postId: Int, val offset: Int)
    private val themeBackStack = ArrayDeque<ThemeBackEntry>()

    /**
     * Post id whose in-content hyperlink the user just tapped, set from [onContentLinkTap] right before
     * the tap is routed to navigation. [captureThemeBackEntry] prefers it over the topmost-visible post
     * so «Назад» returns to the SOURCE post (the one holding the link): a link sitting low in a post makes
     * findFirstVisibleItemPosition() report an EARLIER post peeking at the top, and Back would land above
     * the source (reported: tap link in post A → back lands on the post before A). Consumed (read + reset)
     * by the next [captureThemeBackEntry]; 0 = no pending link tap.
     */
    private var lastContentLinkSourcePostId: Int = 0

    /**
     * Post id the open anchored to the BOTTOM (last post of an already-read topic). The async metadata
     * enrichment (post counts / reputation) grows posts AFTER the anchor is applied, which would push the
     * last post's action buttons back below the fold — so [enrichLoadedPage] re-bottom-anchors while the
     * user still sits at the bottom. Cleared once the user scrolls up off the last post.
     */
    private var anchoredBottomPostId: Int? = null

    /** Guards [maintainBottomPin] against re-entering while its own corrective scroll is pending. */
    private var pinningInProgress = false

    /** Guards [healBottomEndGap] against re-entering while its own corrective scroll is pending. */
    private var gapHealInProgress = false


    /**
     * Bottom-nav «chrome» (tab bar + system navigation) height in px, pushed in by MainActivity via
     * [onBottomChromePaddingChanged]. With [shouldDrawBehindBottomNav]==true the list runs edge-to-edge
     * behind this chrome, so it is reserved verbatim (+ gap) as list bottom padding to lift the last post
     * above the tab bar — see [applyListBottomPadding].
     */
    private var bottomNavChromePad = 0

    /**
     * Transient extra bottom padding reserved so an EXPLICIT deep-link/quote anchor ([AnchorRequest.Post]
     * with reason FIND_POST) can TOP-align a target that sits near the end of the topic. Near the end there
     * may be less than a viewport of content below the target, so `scrollToPositionWithOffset(pos, 0)` clamps
     * at the content edge and the target never reaches the top — the user taps a quote of a recent post on the
     * last page and sees the highlight fire but no scroll ([[theme-open-anchor-subsystem]]). Reserving the
     * shortfall as bottom room lets the top-align land; released on the next user drag ([onScrollStateChanged])
     * and reset on every fresh anchor ([applyInitialAnchor]).
     */
    private var deepLinkAnchorExtraBottomPad = 0

    /** Whether the loaded content still has an unread anchor — drives the «К непрочитанному» menu item. */
    private var topicHasUnread: Boolean = false

    /** Reused WebView smart-navigation popup (page wheel + start/unread/end/enter-number). */
    private var smartNavMenu: forpdateam.ru.forpda.ui.views.SmartNavigationMenu? = null

    /** «Умная кнопка темы» (FAB) state: enabled per pref; arrow follows the last scroll direction. */
    private var fabEnabled = false
    private var fabPointsDown = true
    /** Auto-hide: the smart button appears on scroll and hides after this idle delay (WebView parity). */
    private val fabHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fabHideRunnable = Runnable { if (fabEnabled && view != null) fab.hide() }

    override fun hasBackHandling(): Boolean =
            themeOverlay != null || messagePanel?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE ||
                    // История переходов внутри вкладки (HISTORY): «назад» должен вернуть по истории,
                    // а не выйти из приложения на корневой вкладке — иначе back-callback выключается.
                    (mainPreferencesHolder.getTopicBackBehavior() ==
                            forpdateam.ru.forpda.common.Preferences.Main.TopicBackBehavior.HISTORY &&
                            themeBackStack.isNotEmpty())

    override fun onBackPressed(): Boolean {
        // Back closes the hat/poll overlay first, then the find-on-page bar / reply editor, before leaving.
        if (dismissThemeOverlay()) return true
        if (searchBar?.visibility == View.VISIBLE) {
            closeSearch()
            return true
        }
        // Let the panel dismiss its own BBCode/smiles popup before it hides entirely.
        if (messagePanel?.onBackPressed() == true) return true
        if (messagePanel?.visibility == View.VISIBLE) {
            hideMessagePanel()
            return true
        }
        // «Поведение кнопки Назад в темах» = HISTORY (по умолчанию): пройтись по истории переходов внутри
        // вкладки (ссылки на посты/темы) прежде чем закрыть вкладку. ORIGIN — сразу закрыть (как раньше).
        if (mainPreferencesHolder.getTopicBackBehavior() ==
                forpdateam.ru.forpda.common.Preferences.Main.TopicBackBehavior.HISTORY &&
                themeBackStack.isNotEmpty()) {
            val entry = themeBackStack.removeLast()
            if (entry.postId > 0) {
                pendingRestorePostId = entry.postId
                pendingRestoreOffset = entry.offset
            }
            boundaryResumeArmed = false // возврат в историю, не свежее открытие
            loadTopic(entry.url)
            return true
        }
        return super.onBackPressed()
    }

    private val topicUrl: String
        get() = arguments?.getString(TabFragment.ARG_TAB).orEmpty()

    /**
     * Flat, edge-to-edge top-bar chrome like the WebView theme screen ([ThemeFragment] does the same
     * override). Without it the topic toolbar defaults to [R.attr.chrome_plane_background] → non-flat
     * chrome, which draws a 1dp app-bar stroke that reads as an extra divider line under the toolbar
     * (very visible on the reading palettes, e.g. Sepia Blue's blue-grey stroke).
     */
    override fun topBarSurfaceColorAttr(): Int =
            forpdateam.ru.forpda.R.attr.main_toolbar_accent_surface

    /**
     * Rounded lower plaque corners like the WebView theme screen ([ThemeFragment] overrides this the same
     * way). Flat accent chrome otherwise resolves to [TopAppBarShapeStyle.FULL_WIDTH_RECT] — a hard,
     * straight, edge-to-edge bar that reads as non-M3 (user: «прямой прям идёт до конца»). With rounded
     * corners the flat full-width bar becomes [TopAppBarShapeStyle.FULL_WIDTH_BOTTOM_ROUNDED] — the M3
     * plaque that hangs from the status bar, matching the classic topic toolbar exactly.
     */
    override fun useTopBarRoundedCorners(): Boolean = true

    /**
     * Keep the DEFAULT (false): MainActivity insets the fragment container by the full bottom-nav chrome
     * (tab bar + system nav) on EVERY device, so the RecyclerView already ends exactly at the top of the tab
     * bar. The list therefore needs only a small breathing gap ([bottomRestGapPx]) as bottom padding to lift
     * the last post's border off the bar — NOT the chrome height (that would double-count).
     *
     * Drawing edge-to-edge (true) was a wrong turn: it made the container run to the very screen bottom, so
     * when the reply editor opens the window background flashes as a solid band below the input before the
     * keyboard slides up (user: «сначала появляется синий фон а потом его заполняет клавиатура»). false has
     * no such band.
     */
    override fun shouldDrawBehindBottomNav(): Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = androidx.recyclerview.widget.ConcatAdapter(pollHeaderAdapter, postsAdapter)
        // Bottom room for the CLASSIC-mode pagination bar is managed in updatePaginationBar().
        recyclerView.clipToPadding = false
        // Page tone UNDER the post cards = полотно ChromeCanvas (под Material You — динамический
        // тон обоев, единый с шапкой/нижним баром; вне MY = colorSurfaceContainerLowest, как раньше).
        // Карточки постов остаются на content_card_surface и всплывают над тонированным полотном.
        requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest).let { page ->
            recyclerView.setBackgroundColor(page)
            refreshLayout.setBackgroundColor(page)
        }
        applyDisplaySettings()
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                // The end-gap correction ([healBottomEndGap]) is a layout repair, not a user scroll: it moves
                // the list UP by a few dozen pixels, which must not be read as «поехали вверх» (previous-page
                // load, FAB arrow flip). Everything below it is state-derived and stays correct.
                val healing = gapHealInProgress
                if (dy > 0) maybeLoadNextPage()
                if (dy < 0 && !healing) maybeLoadPrevPage()
                markVisiblePostsRead()
                maybeMarkTopicReadAtEnd()
                updateBarCurrentPageFromScroll()
                updateBottomPaginationBarOffset()
                if (!healing) updateFabOnScroll(dy)
                if (smartNavMenu?.isShowing() == true) smartNavMenu?.dismiss()
            }

            /**
             * Drop the bottom anchor only when the USER actually grabs the list. It used to be dropped on any
             * upward [onScrolled] (dy < 0) — but a metadata-enrichment relayout that grows posts ABOVE the last
             * one makes RecyclerView compensate and dispatch exactly that, killing the pin a frame before it
             * was due to correct the grown last post. That is why the last post stayed clipped on a real device
             * (big, late enrichment) while the emulator (tiny, early enrichment) looked fine.
             */
            override fun onScrollStateChanged(rv: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (forpdateam.ru.forpda.BuildConfig.DEBUG && anchoredBottomPostId != null) android.util.Log.i("FPDA_CLEAR", "anchor CLEARED (user drag)")
                    anchoredBottomPostId = null
                    // The user is scrolling away from a deep-link/quote landing → give back the transient
                    // top-align bottom room so the topic end no longer carries phantom empty space.
                    if (deepLinkAnchorExtraBottomPad != 0) {
                        deepLinkAnchorExtraBottomPad = 0
                        applyListBottomPadding()
                    }
                }
                // Жест пользователя (drag; SETTLING без drag'а = единственный smoothScroll — FAB-пейджинг,
                // тоже действие юзера; программные коррекции идут через мгновенный scrollBy и state не меняют)
                // → mark-read-в-конце снова разрешён: теперь «низ виден» = юзер сам туда пришёл.
                if (newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    suppressEndMarkReadUntilUserScroll = false
                    userScrollGestureThisSession = true
                }
                // Границу прочитанного двигаем ТОЛЬКО по устоявшемуся вьюпорту: покадровая запись из
                // onScrolled на флинге «вниз глянуть и назад» монотонно сжигала всё до самой глубокой
                // мелькнувшей точки (recordSeen назад не откатывается) — источник «пропускаются непрочитанные».
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    recordReadBoundaryAtRest()
                    // Settled past the content end (post heights changed under the fling)? Pull the list back
                    // onto its last post — see [healBottomEndGap]. A fling can settle without any further
                    // layout pass, so the layout listener alone would not catch it.
                    healBottomEndGap()
                    // Settled: re-check «низ ли это» (a fling can stop without a final onScrolled frame).
                    updateBottomPaginationBarOffset()
                    // ...и по той же причине пере-проверить mark-read-в-конце. onScrolled метит тему
                    // прочитанной покадрово, но флинг к концу может осесть без финального onScrolled-кадра,
                    // в котором последний пост уже полностью на экране (или его съел healing-guard) — тогда
                    // тема так и оставалась непрочитанной в избранном до следующего касания. Проверка
                    // идемпотентна (markedTopicReadAtEnd) и полностью защищена своими гейтами, поэтому
                    // безопасна в покое. Вызываем ПОСЛЕ healBottomEndGap — на исправленной позиции.
                    maybeMarkTopicReadAtEnd()
                    // ...и по той же причине — перевзвести подгрузку соседних страниц. Флинг может осесть
                    // ровно у края, не прислав финального onScrolled-кадра в зоне порога (или его съел
                    // healing-guard на dy<0), из-за чего след./пред. страница не грузилась, пока юзер не
                    // «поёрзает» пальцем. Обе проверки позиционные (абсолютный край, не dy) и защищены
                    // isLoading*-флагами, так что вызвать их в покое безопасно и идемпотентно.
                    maybeLoadNextPage()
                    maybeLoadPrevPage()
                }
            }
        })
        // Фиксируем ПРОТЯГИВАНИЕ по списку (питает [maybeMarkTopicReadAtEnd]): overscroll в самом низу
        // короткой темы может не сменить scroll-state на DRAGGING, и флаг жеста не взведётся. Ловим drag за
        // touchSlop, а НЕ тап (ACTION_DOWN): случайный тап по ссылке/аватару НЕ должен считаться «долистал».
        // Только наблюдаем (onInterceptTouchEvent → false), не перехватываем: тапы, выделение и скролл
        // работают как прежде. Порог crossing'а приходит сюда ДО решения RecyclerView интерсептить.
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            private val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
            private var downX = 0f
            private var downY = 0f
            override fun onInterceptTouchEvent(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    e: android.view.MotionEvent,
            ): Boolean {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!userDraggedListThisSession &&
                                (kotlin.math.abs(e.x - downX) > touchSlop ||
                                        kotlin.math.abs(e.y - downY) > touchSlop)) {
                            userDraggedListThisSession = true
                        }
                    }
                }
                return false
            }
        })
        // The bottom-nav container padding lands a beat AFTER the first anchor (async window-inset pass),
        // shrinking the list. If we parked on the last post, that stale offset re-hides its action buttons
        // below the new fold — re-pin to the bottom whenever the list height actually changes while anchored.
        // Correct the last post's bottom on EVERY layout pass while parked on it — enrichment / image loads
        // grow it long after any fixed delay would have fired. See [maintainBottomPin].
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(bottomPinLayoutListener)
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                // The list's bottom edge moved (container inset landed, keyboard, rotation) → the on-screen
                // overlap with the tab bar changed, so re-measure the clearance padding (device-agnostic).
                applyListBottomPadding()
                if (anchoredBottomPostId != null) recyclerView.post { reanchorBottomAfterGrowth() }
            }
        }
        val auth = authHolder.get()
        postsAdapter.setAuthContext(authorized = auth.isAuth(), memberId = auth.userId)
        installPageSwipeDetector()
        installBottomRefreshDetector()
        refreshLayout.setOnRefreshListener { loadTopic(loadedUrl ?: topicUrl) }
        setupMessagePanel()
        // Lift the reply panel above the keyboard by reading the REAL ime inset on every window-inset pass
        // and setting the host's bottom margin directly. The shared DimensionHelper path lags/sticks on
        // some OEM builds (keyboard covered the editor; and a stale inset left an empty strip after the
        // keyboard closed) — reading Type.ime() here is exact for both show and dismiss. This fragment
        // opts out of the base DimensionHelper host-margin path (shouldApplyMessagePanelImeInsets=false).
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { _, insets ->
            applyMessagePanelImeMargin(insets)
            insets
        }
        // Ride the keyboard frame-by-frame so the panel tracks the IME edge with no exposed gap / colour
        // flash while it slides. Без этого хост прыгает на ФИНАЛЬНУЮ высоту клавиатуры мгновенно, а клава
        // выезжает ~200мс — в зазоре под хостом мелькает фон. DISPATCH_MODE_STOP откладывает apply-listener
        // (resting bottomMargin) до конца анимации: во время выезда coordinator сохраняет полную высоту (его
        // surface закрывает область, по которой едет клава), а сам хост лишь транслируется на верхний край IME.
        ViewCompat.setWindowInsetsAnimationCallback(
                fragmentContainer,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                    override fun onProgress(
                            insets: WindowInsetsCompat,
                            running: MutableList<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        if (messagePanel?.visibility == View.VISIBLE) {
                            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                            val resting = (messagePanelHost.layoutParams
                                    as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
                            messagePanelHost.translationY = (resting - imeBottom).toFloat()
                        }
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        messagePanelHost.translationY = 0f
                        ViewCompat.getRootWindowInsets(fragmentContainer)?.let { applyMessagePanelImeMargin(it) }
                    }
                })
        setupFab()
        setupToolbarMenu()
        setupTitleTap()
        applyToolbarAutoHide()
        // Устойчивость состояния / restore-scroll: если фрагмент пересоздан (смерть процесса / восстановление
        // FragmentManager / пересоздание вью), в savedInstanceState лежит пост+offset, где стоял пользователь —
        // грузим ИМЕННО ту страницу (findpost) и садимся туда, а не на серверный якорь.
        val restorePostId = savedInstanceState?.getInt(STATE_RESTORE_POST_ID, 0) ?: 0
        if (restorePostId > 0) {
            pendingRestorePostId = restorePostId
            pendingRestoreOffset = savedInstanceState?.getInt(STATE_RESTORE_OFFSET, 0) ?: 0
            barCurrentPage = (savedInstanceState?.getInt(STATE_RESTORE_BAR_PAGE, 1) ?: 1).coerceAtLeast(1)
        }
        // Arm the client read-boundary resume only for a genuinely fresh open (nothing to restore).
        boundaryResumeArmed = restorePostId <= 0
        loadTopic(if (restorePostId > 0) buildRestoreUrl(restorePostId) else resolveInitialOpenUrl())

        // Live-toggle «Панель страниц темы»: re-evaluate both bars when the setting flips while the topic
        // tab stays alive in the background stack (the collector re-emits the current value immediately).
        viewLifecycleOwner.lifecycleScope.launch {
            mainPreferencesHolder.observeTopicPaginationPanelsFlow().collect {
                if (view != null && pagination.isInitialised) updatePaginationBar()
            }
        }
    }

    /** URL, ведущий на конкретный пост (findpost) для restore-scroll; фолбэк — обычное открытие. */
    private fun buildRestoreUrl(postId: Int): String {
        val topicId = ThemeApi.extractTopicIdFromUrl(topicUrl) ?: return resolveInitialOpenUrl()
        return forpdateam.ru.forpda.presentation.theme.TopicUnreadFindPostReloadPolicy
                .buildFindPostUrl(topicId, postId.toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем первый видимый пост и его пиксельный offset — точную точку «где остановился», чтобы
        // пережить пересоздание фрагмента (смерть процесса / restore FragmentManager). Пустой список / нет
        // вью — сохранять нечего.
        if (view == null || pageTopicId <= 0 || loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val item = loadedItems.getOrNull(firstPos - headerOffset()) ?: return
        if (item.postId <= 0) return
        val top = lm.findViewByPosition(firstPos)?.top ?: 0
        outState.putInt(STATE_RESTORE_POST_ID, item.postId)
        outState.putInt(STATE_RESTORE_OFFSET, top)
        outState.putInt(STATE_RESTORE_BAR_PAGE, barCurrentPage)
    }

    /**
     * Apply the «При открытии темы» setting (Первая страница / Первое непрочитанное) to the initial URL,
     * exactly as the WebView ViewModel does via [TopicOpenTargetResolver]: with «Первое непрочитанное» and
     * an unread hint from the list, this yields a `view=getnewpost`/find-post URL so the topic opens on the
     * first unread post instead of always page 1. Only used for the very first load.
     */
    private fun resolveInitialOpenUrl(): String {
        val args = arguments ?: return topicUrl
        val context = forpdateam.ru.forpda.presentation.theme.TopicOpenContext(
                rawUrl = topicUrl,
                setting = mainPreferencesHolder.getTopicOpenTarget(),
                sourceScreen = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_SOURCE)
                        ?: "unknown",
                sourceUrl = topicUrl,
                openIntentRaw = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_INTENT),
                unreadUrlFromList = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_URL_FROM_LIST),
                unreadPostIdFromList = args.getInt(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST)
                        .takeIf { it > 0 },
                inspectorMarkedUnread = args.getBoolean(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_INSPECTOR_MARKED_UNREAD),
                lastReadUrlFromList = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_LAST_READ_URL_FROM_LIST),
        )
        return runCatching {
            forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver.resolve(context).url
        }.getOrDefault(topicUrl)
    }

    /**
     * Resolve the open target for a navigator-driven open/switch (parity with [resolveInitialOpenUrl], but
     * fed from the navigator's explicit [listHints] rather than fragment arguments). Mirrors the WebView
     * presenter's loadUrl → the same getnewpost/findpost/page-1 decision, so the navigator never loads the
     * bare page-1 url.
     */
    private fun resolveNavigatorOpenUrl(
            rawUrl: String,
            sourceScreen: String,
            openIntent: String,
            listHints: TopicOpenListHints?,
    ): String {
        val context = forpdateam.ru.forpda.presentation.theme.TopicOpenContext(
                rawUrl = rawUrl,
                setting = mainPreferencesHolder.getTopicOpenTarget(),
                sourceScreen = sourceScreen,
                sourceUrl = rawUrl,
                openIntentRaw = openIntent,
                unreadUrlFromList = listHints?.unreadUrlFromList,
                unreadPostIdFromList = listHints?.unreadPostIdFromList?.takeIf { it > 0 },
                listTopicMarkedUnread = listHints?.topicMarkedUnread ?: false,
                inspectorMarkedUnread = listHints?.inspectorMarkedUnread ?: false,
                lastReadUrlFromList = listHints?.lastReadUrlFromList,
        )
        return runCatching {
            forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver.resolve(context).url
        }.getOrDefault(rawUrl)
    }

    /**
     * «Умная кнопка темы» (setting «Умная кнопка темы»): a persistent mini FAB (parity with the
     * WebView, which keeps it on screen). A short tap scrolls ~a screen in the direction the user is
     * travelling (the arrow follows that direction); a LONG press opens the page-jump dialog. It
     * stays visible so the long press is always reachable. Hidden entirely when the pref is off.
     */
    private fun setupFab() {
        fabEnabled = mainPreferencesHolder.getScrollButtonEnabled()
        val dm = resources.displayMetrics
        // Bottom-end, lifted above the CLASSIC pagination bar so it never sits on top of it.
        (fab.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            val m = (16 * dm.density).toInt()
            lp.rightMargin = m
            // Lifted a bit higher off the bottom (WebView reference sits well above the tab bar); in
            // CLASSIC it must also clear the pagination bar, so it sits higher still.
            lp.bottomMargin = ((if (isClassicMode()) 152 else 112) * dm.density).toInt()
            fab.layoutParams = lp
        }
        androidx.core.view.ViewCompat.setElevation(fab, 12f * dm.density)
        if (!fabEnabled) {
            fab.hide()
            return
        }
        fab.size = com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
        fab.setImageResource(forpdateam.ru.forpda.R.drawable.ic_arrow_down)
        // FAB colours are a matched pair defined per theme: smart_nav_fab_background + smart_nav_fab_icon.
        // Read BOTH from the pair (not colorAccent) — on AMOLED palettes colorAccent == smart_nav_fab_icon,
        // so using it as the background made the arrow invisible (bg == icon, 1:1). smart_nav_fab_background
        // is the palette accent for normal themes and the AMOLED black behind a coloured arrow.
        val fabBg = requireContext().getColorFromAttr(forpdateam.ru.forpda.R.attr.smart_nav_fab_background)
        val fabIcon = requireContext().getColorFromAttr(forpdateam.ru.forpda.R.attr.smart_nav_fab_icon)
        fab.backgroundTintList = android.content.res.ColorStateList.valueOf(fabBg)
        fab.imageTintList = android.content.res.ColorStateList.valueOf(fabIcon)
        fab.isLongClickable = true
        // Гасим АВТОМАТИЧЕСКИЙ haptic View (на long-press он давал второй, дублирующий buzz), а свой
        // одиночный отклик шлём через Haptic.perform() с FLAG_IGNORE_VIEW_SETTING — он обходит выключенный
        // флаг view, но уважает настройку «Тактильный отклик». Иначе после отключения флага вибрации не было.
        fab.isHapticFeedbackEnabled = false
        fab.setOnClickListener {
            forpdateam.ru.forpda.ui.Haptic.perform(it, android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            showFabTemporarily() // использование кнопки сбрасывает таймер авто-скрытия обратно на 2.5с
            smartScrollTap()
        }
        fab.setOnLongClickListener {
            forpdateam.ru.forpda.ui.Haptic.perform(it, android.view.HapticFeedbackConstants.LONG_PRESS)
            showFabTemporarily() // long-press тоже продлевает видимость кнопки
            showSmartNavMenu()
            true
        }
        // Hidden until the user scrolls (WebView parity): appears on any scroll, hides after idle.
        fab.hide()
    }

    /** Show the smart button and (re)arm its idle auto-hide. */
    private fun showFabTemporarily() {
        if (!fabEnabled) return
        if (!fab.isShown) fab.show()
        fabHideHandler.removeCallbacks(fabHideRunnable)
        fabHideHandler.postDelayed(fabHideRunnable, FAB_AUTO_HIDE_MS)
    }

    /** Reveal the FAB on any scroll (auto-hiding after idle) and point its arrow along the scroll direction. */
    private fun updateFabOnScroll(dy: Int) {
        if (!fabEnabled) return
        if (dy != 0) showFabTemporarily() // any scroll wakes the button and re-arms the 2.5s hide
        if (kotlin.math.abs(dy) < SCROLL_HIDE_THRESHOLD) return
        val down = dy > 0
        if (down != fabPointsDown) {
            fabPointsDown = down
            fab.setImageResource(
                    if (down) forpdateam.ru.forpda.R.drawable.ic_arrow_down
                    else forpdateam.ru.forpda.R.drawable.ic_arrow_up)
        }
    }

    /**
     * Short tap: flip exactly ONE FORUM page (perPage posts) forward/back in the arrow's direction — the
     * user wants a whole-page jump («перекинуть на 1 страницу»), not a one-screen nudge. Position-based so
     * it lands on the page boundary regardless of variable post heights; on reaching the loaded edge the
     * infinite scroll pulls the adjacent page in. Falls back to a one-viewport scroll if positions aren't
     * resolvable yet.
     */
    private fun smartScrollTap() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val total = recyclerView.adapter?.itemCount ?: 0
        val first = lm?.findFirstVisibleItemPosition() ?: androidx.recyclerview.widget.RecyclerView.NO_POSITION
        if (lm == null || total <= 0 || first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            val viewport = recyclerView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            recyclerView.smoothScrollBy(0, if (fabPointsDown) viewport else -viewport)
            return
        }
        val perPage = pagination.perPage.takeIf { it > 0 } ?: 20
        val target = (if (fabPointsDown) first + perPage else first - perPage).coerceIn(0, total - 1)
        lm.scrollToPositionWithOffset(target, 0)
        recyclerView.post { if (fabPointsDown) maybeLoadNextPage() else maybeLoadPrevPage() }
    }

    /**
     * Embeds the full WebView editor component ([MessagePanel]) one-to-one: BBCode formatting
     * toolbar, smiles, attachments and the send / clear / hide controls. Hidden until the user taps
     * «Написать», reply or quote. Send / upload / delete reuse [ThemeEditorUseCase] (the same
     * network logic the WebView editor drives through its ViewModel).
     */
    private fun setupMessagePanel() {
        if (messagePanel != null) return
        val panel = MessagePanel(
                requireContext(), fragmentContainer, messagePanelHost, false,
                mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder,
        )
        panel.hostBaseBottomMarginProvider = { messagePanelBaseBottomMargin() }
        // The message-panel host sits over the fragment root, whose background is ?colorPrimary (blue).
        // The compact panel floats with margins, so that blue leaks around it as an unwanted border.
        // Paint the host with the list surface so the panel floats seamlessly (parity with WebView).
        val panelSurface = requireContext().chromeCanvasColor(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        messagePanelHost.setBackgroundColor(panelSurface)
        // Kill the coloured backdrop flash: when the keyboard opens, the host is lifted by bottomMargin =
        // ime inset (see applyMessagePanelImeMargin), but the IME reports its FINAL height instantly while
        // the keyboard slides in over ~200ms. During that slide the gap below the lifted host exposes the
        // fragment root, whose background is ?colorPrimary — a brown band flashes before the keyboard
        // covers it. Paint the root with the same list surface so that reserved area is seamless, not brown.
        fragmentContainer.setBackgroundColor(panelSurface)
        panel.visibility = View.GONE
        // Скрыть и сам ХОСТ: AdvancedPopup.attachCompactAdvancedView оборачивает панель в видимый
        // LinearLayout с исходными layout-параметрами компактной панели (topMargin 8dp). Обёртка с
        // GONE-содержимым имеет нулевую высоту, но её topMargin раздувает wrap_content-хост на 8dp —
        // coordinator (layout_above=host) укорачивается, и между нижней пагинацией и таббаром
        // просвечивала полоса фона фрагмента (?colorPrimary). См. showMessagePanel/hideMessagePanel.
        messagePanelHost.visibility = View.GONE
        // Behavior off: with IME adjustResize the AppBar translationY would push the panel under the
        // keyboard (matches the WebView fragment's disableBehavior()).
        panel.disableBehavior()
        panel.messageField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                messagePanelDraftMirror = s.toString()
                // Персистим только черновик нового ответа (не правку загруженного поста).
                if (editingForm == null) persistTopicDraft(s.toString())
            }
        })
        panel.addSendOnClickListener { sendMessage() }
        panel.setClearMessageClickListener { confirmClearMessage() }
        panel.hideButton?.visibility = View.VISIBLE
        panel.hideButton?.setOnClickListener { hideMessagePanel() }
        panel.fullButton?.visibility = View.VISIBLE // «полноэкранный редактор» (parity with the WebView)
        panel.fullButton?.setOnClickListener { openFullscreenEditor() }
        attachmentsPopup = panel.attachmentsPopup
        attachmentsPopup?.setAddOnClickListener {
            FilePickHelper.showAttachChooser(requireContext()) { intent -> pickFileLauncher.launch(intent) }
        }
        attachmentsPopup?.setDeleteOnClickListener { removeFiles() }
        attachmentsPopup?.setRetryUploadListener(object : AttachmentsPopup.OnRetryUploadListener {
            override fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>) {
                enqueueUpload(files, pending)
            }
        })
        messagePanel = panel
    }

    /**
     * Native top-toolbar (parity with the WebView theme toolbar): dedicated icon BUTTONS shown
     * always — «Написать» (pencil, opens the editor), «Опрос» (visible only with a poll), search,
     * «Обновить» and «Инфо» (topic hat, visible only when a hat exists) — plus an overflow with
     * page-jump / copy-link / open-forum. Icons match the WebView (ic_toolbar_create / ic_poll_box /
     * ic_toolbar_search / ic_toolbar_refresh / ic_info). The tab's [toolbar] comes from [TabFragment].
     */
    private fun setupToolbarMenu() {
        // Back navigation to leave the topic (parity with the WebView; system back is reused).
        toolbar.setNavigationIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_arrow_back)
        toolbar.navigationContentDescription = getString(forpdateam.ru.forpda.R.string.close_tab)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        // Overflow popup theme comes from the toolbar's app:popupTheme="?attr/popup_overlay" (fragment_base),
        // which resolves to a readable per-palette dropdown — no manual override needed.
        val menu = toolbar.menu
        menu.clear()
        menu.add(0, MENU_CREATE, 0, "Написать").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_create)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_POLL, 1, "Опрос").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_poll_box)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }
        menu.add(0, MENU_SEARCH, 2, "Найти на странице").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_search)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // «Обновить» moved into the «Ещё» overflow (see showOverflowMenu): one fewer always-icon frees
        // toolbar width so the «N / M» page counter subtitle shows in full instead of truncating to «N / …».
        // Refresh is still reachable via the overflow, pull-to-refresh (CLASSIC) and the bottom-up gesture.
        menu.add(0, MENU_HAT, 4, "Шапка темы").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_info)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }
        // «⋮» opens a compact top-sliding panel of extra actions (see showOverflowMenu) rather than the
        // toolbar's built-in overflow popup, which mis-renders with a transparent background on this theme.
        menu.add(0, MENU_OVERFLOW, 20, "Ещё").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_more_vert)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        // The icon drawables bake in DIFFERENT colour attrs (icon_base / icon_toolbar / colorOnSurface),
        // so the toolbar buttons rendered as visibly different colours. Force one uniform tint (matching
        // the back arrow) on the nav icon and every menu icon.
        val toolbarIconColor = requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        toolbar.navigationIcon?.mutate()?.setTint(toolbarIconColor)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.mutate()?.setTint(toolbarIconColor)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE -> { toggleComposeEditor(); true }
                MENU_POLL -> { onPollToolbarClick(); true }
                MENU_SEARCH -> { toggleSearchBar(); true }
                MENU_HAT -> { onHatToolbarClick(); true }
                MENU_OVERFLOW -> { showOverflowMenu(); true }
                else -> false
            }
        }
        refreshToolbarState()
    }

    /**
     * Toggle the poll / hat toolbar-icon visibility to match the loaded page (they are meaningless
     * on pages that carry neither). Mirrors the WebView's refreshToolbarMenuItems visibility gating.
     */
    private fun refreshToolbarState() {
        // Poll and page-search share one toolbar slot (WebView parity): a poll takes it when present, and
        // page-search then moves into the «⋮» overflow (see showOverflowMenu). No poll → show search.
        toolbar.menu.findItem(MENU_POLL)?.isVisible = pageHasPoll
        toolbar.menu.findItem(MENU_SEARCH)?.isVisible = !pageHasPoll
        toolbar.menu.findItem(MENU_HAT)?.isVisible = pageHasHat
    }

    /**
     * A tap on the toolbar title must show the FULL topic name in a popup (WebView parity) — not toggle
     * scroll-to-top/bottom. [TabFragment] wires the title strip to [TabTopScroller.toggleScrollTop] for
     * every tab; here we consume that touch on the wrapper and route the title's own click to the popup,
     * exactly as [ThemeFragmentWeb.consumeHeaderTouchGaps] does.
     */
    private fun setupTitleTap() {
        titlesWrapper.isClickable = true
        titlesWrapper.setOnTouchListener { _, _ -> true } // swallow the scroll-toggle click on the strip
        toolbarTitleView.apply {
            isClickable = true
            setOnClickListener { showFullTopicTitle() }
        }
    }

    /** Popup with the full (untruncated) topic title, anchored under the toolbar title (WebView parity). */
    private fun showFullTopicTitle() {
        val topicTitle = getTitle().trim()
        if (topicTitle.isEmpty()) return
        if (topicTitlePopup?.isShowing == true) return
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val dp8 = (8 * dm.density).toInt()
        val dp16 = (16 * dm.density).toInt()
        val contentView = androidx.appcompat.widget.AppCompatTextView(ctx).apply {
            text = topicTitle
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            gravity = android.view.Gravity.START
            maxLines = 4
            maxWidth = dm.widthPixels - dp16 * 2
            setPadding(dp16, dp8, dp16, dp8)
        }
        topicTitlePopup = android.widget.PopupWindow(
                contentView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
        ).apply {
            isOutsideTouchable = true
            elevation = 4 * dm.density
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp8.toFloat()
            })
            setOnDismissListener { if (topicTitlePopup === this) topicTitlePopup = null }
            showAsDropDown(toolbarTitleView, 0, dp8)
        }
    }

    /**
     * «Поведение тулбара» = HIDE_ON_SCROLL: let the AppBar scroll off with the list (standard
     * AppBarLayout scroll flags; the RecyclerView container already carries ScrollingViewBehavior).
     * Kept PINNED while the editor/search is open so those don't fight the collapsing bar. Re-read
     * on return since the pref may have changed.
     */
    private fun applyToolbarAutoHide() {
        val enabled = mainPreferencesHolder.getTopicToolbarBehavior() ==
                forpdateam.ru.forpda.common.Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL &&
                messagePanel?.visibility != View.VISIBLE &&
                searchBar?.visibility != View.VISIBLE
        val lp = toolbarLayout.layoutParams as? com.google.android.material.appbar.AppBarLayout.LayoutParams
                ?: return
        val flags = if (enabled) {
            com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        } else {
            0
        }
        if (lp.scrollFlags != flags) {
            lp.scrollFlags = flags
            toolbarLayout.layoutParams = lp
            if (!enabled) appBarLayout.setExpanded(true, false)
        }
        // «Верхняя пагинация» rides the SAME flags as the toolbar so both hide/reveal as one block.
        topPaginationBar?.let { bar ->
            (bar.layoutParams as? com.google.android.material.appbar.AppBarLayout.LayoutParams)?.let { plp ->
                if (plp.scrollFlags != flags) {
                    plp.scrollFlags = flags
                    bar.layoutParams = plp
                }
            }
        }
    }

    /** Toolbar «Написать»: opens the empty compose editor (or closes it if already open). */
    private fun toggleComposeEditor() {
        if (messagePanel?.visibility == View.VISIBLE) {
            hideMessagePanel()
        } else {
            editingForm = null
            showMessagePanel(showKeyboard = true)
            restoreTopicDraftIntoPanel()
        }
    }

    /**
     * Editor «полноэкранный редактор» button: hand the current inline draft off to the standalone
     * fullscreen editor screen (parity with the WebView). On close-without-post the draft is restored into
     * the inline panel; on a successful post the topic reloads to show the new message.
     */
    private fun openFullscreenEditor() {
        val panel = messagePanel ?: return
        val draft = panel.message.ifEmpty { messagePanelDraftMirror }
        val selection = panel.selectionRange
        // Preserve the edit identity when handing off mid-edit: without postId + TYPE_EDIT_POST the
        // fullscreen editor submits a NEW post and IPB adds a duplicate (баг «из полноэкранного дубль»).
        val editing = editingForm
        val editPostId = editing?.postId ?: 0
        navigationUseCase.openFullscreenEditor(
                forumId = pageForumId,
                topicId = pageTopicId,
                st = pageSt,
                themeName = arguments?.getString(TabFragment.ARG_TITLE),
                message = draft,
                attachments = panel.attachments.toList(),
                selectionStart = selection.getOrNull(0),
                selectionEnd = selection.getOrNull(1),
                editPostId = editPostId,
                onSync = { data ->
                    if (view == null) return@openFullscreenEditor
                    messagePanelDraftMirror = data.message.orEmpty()
                    showMessagePanel(showKeyboard = false)
                    messagePanel?.setText(data.message)
                    // Программная загрузка — не в историю undo, иначе первое undo стёрло бы весь текст.
                    messagePanel?.messageField?.clearUndoHistory()
                    data.attachments?.let { attachmentsPopup?.setAttachments(it) }
                    messagePanel?.messageField?.let { field ->
                        val len = field.text?.length ?: 0
                        runCatching {
                            field.setSelection(
                                    data.selectionStart.coerceIn(0, len),
                                    data.selectionEnd.coerceIn(0, len))
                        }
                    }
                },
                onPosted = {
                    if (view == null) return@openFullscreenEditor
                    messagePanel?.clearMessage()
                    messagePanel?.clearAttachments()
                    messagePanelDraftMirror = ""
                    editingForm = null
                    hideMessagePanel()
                    // An edit re-anchors on the edited post (findpost), like the inline send path — a plain
                    // reload of loadedUrl lands on the server anchor = first post of the page (симптом
                    // «скролл уезжает вверх, а не на пост»). A new reply keeps the previous behaviour.
                    if (editPostId > 0) {
                        loadTopic(buildRestoreUrl(editPostId))
                    } else {
                        loadTopic(loadedUrl ?: topicUrl)
                    }
                },
        )
    }

    /** Reveal the editor panel (and hide the pagination bar they share the bottom edge with). */
    private fun showMessagePanel(showKeyboard: Boolean) {
        val panel = messagePanel ?: return
        if (panel.visibility != View.VISIBLE) {
            messagePanelHost.visibility = View.VISIBLE // хост скрыт, пока редактор закрыт (см. setupMessagePanel)
            panel.visibility = View.VISIBLE
            paginationBar?.visibility = View.GONE
            topPaginationBar?.visibility = View.GONE
            appBarLayout.setExpanded(true, true) // reveal toolbar for editing
            applyToolbarAutoHide() // pin the toolbar while composing
            if (showKeyboard) panel.show()
        }
        if (showKeyboard) {
            panel.messageField.requestFocus()
            showKeyboard(panel.messageField)
        }
        // Force an inset pass so the host lifts above the keyboard right after the panel appears.
        ViewCompat.requestApplyInsets(fragmentContainer)
    }

    /** This fragment manages the reply-panel IME margin itself (see the fragmentContainer inset listener),
     *  so opt out of the base DimensionHelper-driven host margin which lags/sticks on some OEM keyboards. */
    override fun shouldApplyMessagePanelImeInsets(): Boolean = false

    /** Lift [messagePanelHost] to sit exactly above the keyboard from the REAL ime inset (0 when the
     *  keyboard is hidden or the panel is closed) — device-robust, unlike the lagging DimensionHelper. */
    private fun applyMessagePanelImeMargin(insets: androidx.core.view.WindowInsetsCompat) {
        val imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
        val imeBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        val panelShown = messagePanel?.visibility == View.VISIBLE
        val target = if (panelShown && imeVisible) imeBottom else 0
        val lp = messagePanelHost.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        if (lp.bottomMargin != target) {
            lp.bottomMargin = target
            messagePanelHost.layoutParams = lp
        }
    }

    /** Hide the editor panel, dismiss the keyboard/popups, and restore the pagination bar. */
    private fun hideMessagePanel() {
        val panel = messagePanel ?: return
        panel.hideImeFromEditor()
        panel.visibility = View.GONE
        messagePanelHost.visibility = View.GONE // без этого пустой хост оставляет 8dp-полосу над таббаром
        panel.hidePopupWindows()
        hideKeyboard()
        editingForm = null
        updatePaginationBar()
        applyToolbarAutoHide() // restore auto-hide once the editor is closed
    }

    /** Toolbar «Опрос»: show the poll in a popup over the theme (parity with the WebView poll overlay). */
    private fun onPollToolbarClick() {
        if (dismissThemeOverlay()) return // toggle: second tap closes the poll overlay
        val poll = currentPoll ?: return
        val ctx = requireContext()
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = PollHeaderAdapter(
                    voteListener = PollHeaderAdapter.PollVoteListener { action, method, form ->
                        submitPoll(action, method, form)
                    },
                    collapsible = false, // popup poll is always fully expanded
            ).also { it.setPoll(poll) }
        }
        showThemePopup("Опрос", rv)
    }

    /**
     * A top-anchored overlay panel showing [content], sliding DOWN from the top over the theme (used by
     * the «Инфо»/«Опрос» toolbar buttons — parity with the WebView hat/poll overlays, which drop down
     * from under the toolbar). Capped to most of the screen height; the [content] scrolls if taller.
     */
    private fun showThemePopup(title: String?, content: View, fillHeight: Boolean = false) {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, forpdateam.ru.forpda.R.drawable.bg_theme_top_sheet)
            // A title header only when explicitly asked — the hat overlay omits it (the content is
            // self-evidently the topic hat, so «Шапка темы» just added a redundant strip).
            if (!title.isNullOrBlank()) {
                addView(TextView(ctx).apply {
                    text = title
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                    setPadding(pad, pad, pad, pad / 2)
                })
            } else {
                setPadding(0, pad / 2, 0, 0) // small top inset so content isn't glued to the toolbar
            }
            // fillHeight (hat): content takes the whole panel via weight, so it scrolls inside a
            // full-height overlay. Otherwise (poll): WRAP_CONTENT so a short list keeps its natural size.
            addView(content, if (fillHeight)
                android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            else
                android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        presentTopSheet(root, scrollTarget = content, fillHeight = fillHeight)
    }

    /** The live hat/poll overlay panel (an in-layout view, not a Dialog), or null when none is shown. */
    private var themeOverlay: View? = null

    /** Indeterminate spinner shown while a hybrid neighbour page is loading (top for prev, bottom for next). */
    private var hybridLoadingBar: com.google.android.material.progressindicator.CircularProgressIndicator? = null

    /** Show the hybrid page-load spinner at the top (prev page) or bottom (next page) of the content area. */
    private fun showHybridLoading(atTop: Boolean) {
        if (view == null) return
        val bar = hybridLoadingBar ?: com.google.android.material.progressindicator.CircularProgressIndicator(requireContext()).apply {
            // Material 3 indeterminate spinner (parity with the rest of the app — Favorites/Profile/Auth),
            // themed with the accent; replaces the plain android.widget.ProgressBar.
            isIndeterminate = true
            indicatorSize = (30 * resources.displayMetrics.density).toInt()
            trackThickness = (3 * resources.displayMetrics.density).toInt()
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT)
            coordinatorLayout.addView(this)
        }.also { hybridLoadingBar = it }
        val dm = resources.displayMetrics
        (bar.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL or
                    (if (atTop) android.view.Gravity.TOP else android.view.Gravity.BOTTOM)
            lp.topMargin = if (atTop) (appBarLayout.height + 12 * dm.density).toInt() else 0
            lp.bottomMargin = if (atTop) 0 else (24 * dm.density).toInt()
            bar.layoutParams = lp
        }
        bar.bringToFront()
        bar.visibility = View.VISIBLE
    }

    private fun hideHybridLoading() {
        if (!isLoadingNextPage && !isLoadingPrevPage) hybridLoadingBar?.visibility = View.GONE
    }

    /**
     * Present [root] as an overlay panel that slides down from directly under the toolbar (parity with the
     * WebView hat/poll/menu overlays, which are DOM elements at the top of the content area). Implemented
     * as a real child of the [coordinatorLayout] — positioned below the [appBarLayout] via
     * [AppBarLayout.ScrollingViewBehavior] and clipping its child — so the panel genuinely emerges from
     * *under* the toolbar with one smooth animation (the old Dialog approach flashed at the screen top
     * before repositioning). A tap on the free strip below the panel, or Back, dismisses it. When
     * [scrollTarget] is set and the panel would exceed the available height, that view is clamped so it
     * scrolls inside the panel instead.
     */
    private fun presentTopSheet(root: android.widget.LinearLayout, scrollTarget: View?, fillHeight: Boolean = false) {
        dismissThemeOverlay()
        val ctx = requireContext()
        // Fills the content area (ScrollingViewBehavior offsets it under the toolbar) and clips its child,
        // so a child translated up by its own height is hidden behind the toolbar until it slides down.
        val container = android.widget.FrameLayout(ctx).apply {
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT).apply {
                behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
            }
            clipChildren = true
            isClickable = true // tap the free strip below the panel to dismiss
            setOnClickListener { dismissThemeOverlay() }
        }
        root.isClickable = true // swallow taps on the panel itself so they don't dismiss
        // fillHeight (hat): the panel occupies the whole working area — no empty strip below.
        // Dismiss via Back or a second tap on the «Шапка темы» button. Otherwise WRAP_CONTENT + 90% cap.
        container.addView(root, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                if (fillHeight) android.view.ViewGroup.LayoutParams.MATCH_PARENT
                else android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP))
        coordinatorLayout.addView(container)
        themeOverlay = container
        appBarLayout.setExpanded(true, true) // ensure the toolbar is down so the panel drops from under it
        container.viewTreeObserver.addOnPreDrawListener(
                object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        container.viewTreeObserver.removeOnPreDrawListener(this)
                        // Cap to ~90% of the content height so tall content scrolls inside and a strip
                        // stays free below for tap-to-dismiss. Skipped when fillHeight — there the
                        // content already fills the full-height panel (weight) and scrolls internally.
                        if (scrollTarget != null && !fillHeight) {
                            val maxH = (container.height * 0.9f).toInt()
                            val overflow = root.height - maxH
                            if (overflow > 0) {
                                scrollTarget.layoutParams = scrollTarget.layoutParams
                                        .apply { height = scrollTarget.height - overflow }
                                scrollTarget.requestLayout()
                            }
                        }
                        root.translationY = -root.height.toFloat()
                        root.alpha = 0f
                        root.animate().translationY(0f).alpha(1f).setDuration(220)
                                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        return true
                    }
                })
    }

    /** Slide the hat/poll overlay back up under the toolbar and remove it. Returns true if one was open. */
    private fun dismissThemeOverlay(): Boolean {
        val overlay = themeOverlay ?: return false
        themeOverlay = null
        val panel = (overlay as? android.view.ViewGroup)?.getChildAt(0)
        val remove = { (overlay.parent as? android.view.ViewGroup)?.removeView(overlay) }
        if (panel != null && panel.height > 0) {
            panel.animate().translationY(-panel.height.toFloat()).alpha(0f).setDuration(160)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction { remove() }.start()
        } else {
            remove()
        }
        return true
    }

    /**
     * The toolbar «⋮» overflow — a compact dropdown anchored under the «⋮» button (right side), parity
     * with the WebView toolbar menu. Uses a [ListPopupWindow] with an explicitly solid background so it
     * never renders transparent (the toolbar's built-in overflow mis-resolves `?attr/colorSurface`), and
     * drops out from under the toolbar without covering it.
     */
    private fun showOverflowMenu() {
        val ctx = requireContext()
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Обновить" to { loadTopic(loadedUrl ?: topicUrl) })
            // Add/remove from favorites (parity with the WebView fav menu). Visible once a topic id is known.
            if (pageTopicId > 0) {
                if (pageIsInFavorite) {
                    add("Убрать из избранного" to { confirmRemoveFromFavorites() })
                } else {
                    add("Добавить в избранное" to { showAddToFavoritesDialog() })
                }
            }
            // «В закладки»: сохранить тему в Закладки (тот же диалог с папкой, что и в QMS).
            if (pageTopicId > 0) {
                add("В закладки" to { createNoteForTopic() })
            }
            if (pageTopicId > 0) {
                add(getString(forpdateam.ru.forpda.R.string.other_menu_pin_to_menu) to {
                    menuShortcutPinner.pinTopic(pageTopicId, getTitle().trim())
                    Toast.makeText(ctx, forpdateam.ru.forpda.R.string.other_menu_shortcut_added, Toast.LENGTH_SHORT).show()
                })
            }
            add("Скопировать ссылку" to {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("topic",
                        "https://4pda.to/forum/index.php?showtopic=$pageTopicId"))
                Toast.makeText(ctx, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            })
            // Page-search lives here whenever a poll has taken its toolbar slot (see refreshToolbarState).
            if (pageHasPoll) add("Найти на странице" to { toggleSearchBar() })
            add("Найти в теме" to {
                if (pageTopicId > 0) navigationUseCase.openSearchInTopic(pageForumId, pageTopicId, nick = "")
            })
            add("Найти мои посты" to {
                if (pageTopicId > 0) navigationUseCase.openSearchMyPosts(pageTopicId, pageForumId)
            })
            add("Перейти на страницу" to { showPagePicker() })
            add("Открыть форум темы" to { if (pageForumId > 0) navigationUseCase.openForum(pageForumId) })
            // «Следить за новыми версиями» — пуш, когда в шапку добавят новый apk.
            if (pageTopicId > 0) {
                val watched = notificationPreferencesHolder.isHatWatched(pageTopicId)
                val label = if (watched) "Не следить за новыми версиями" else "Следить за новыми версиями"
                add(label to {
                    val nowWatched = notificationPreferencesHolder.toggleHatWatch(pageTopicId)
                    Toast.makeText(
                            ctx,
                            if (nowWatched) getString(forpdateam.ru.forpda.R.string.fav_watch_versions_on)
                            else getString(forpdateam.ru.forpda.R.string.fav_watch_versions_off),
                            Toast.LENGTH_SHORT
                    ).show()
                })
            }
            add("Открыть в браузере" to {
                // Через ExternalBrowserLauncher, а не сырой ACTION_VIEW: на MIUI/HyperOS неявный
                // VIEW-интент показывает системный тост «Браузер по умолчанию не найден». Лаунчер
                // строит явный интент к найденному браузеру и имеет фолбэк на системный резолвер.
                forpdateam.ru.forpda.common.ExternalBrowserLauncher.open(
                        ctx,
                        "https://4pda.to/forum/index.php?showtopic=$pageTopicId"
                )
            })
        }
        val dm = resources.displayMetrics
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val surface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 12 * dm.density
        }
        val popup = androidx.appcompat.widget.ListPopupWindow(ctx)
        popup.anchorView = toolbar.findViewById(MENU_OVERFLOW) ?: toolbar
        popup.setBackgroundDrawable(bg)
        popup.isModal = true
        popup.width = (240 * dm.density).toInt()
        popup.verticalOffset = (4 * dm.density).toInt()
        val hpad = (20 * dm.density).toInt()
        val vpad = (13 * dm.density).toInt()
        val adapter = object : android.widget.ArrayAdapter<String>(
                ctx, 0, actions.map { it.first }) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(ctx).apply {
                    setPadding(hpad, vpad, hpad, vpad)
                    textSize = 15f
                    setTextColor(onSurface)
                }
                tv.text = getItem(position)
                return tv
            }
        }
        popup.setAdapter(adapter)
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            actions[position].second()
        }
        popup.show()
    }

    /**
     * «Добавить в избранное» — pick a subscription type (parity with the WebView [showAddInFavDialog]),
     * then add via [ThemeInteractionUseCase]. On success flip [pageIsInFavorite] so the overflow item
     * toggles to «Убрать из избранного» without a reload.
     */
    private fun showAddToFavoritesDialog() {
        if (pageTopicId <= 0) return
        val topicId = pageTopicId
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(forpdateam.ru.forpda.R.string.favorites_subscribe_email)
                .setItems(forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
                        .getSubNames(requireContext())) { _, which ->
                    val subType = forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
                            .SUB_TYPES.getOrElse(which) { "none" }
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            interactionUseCase.addTopicToFavorite(topicId, subType)
                        }
                        if (view == null) return@launch
                        when (result) {
                            is forpdateam.ru.forpda.model.interactors.theme.ThemeInteractionUseCase.FavoriteResult.Add -> {
                                if (result.success) {
                                    pageIsInFavorite = true
                                    Toast.makeText(requireContext(),
                                            forpdateam.ru.forpda.R.string.favorites_added, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(),
                                            forpdateam.ru.forpda.R.string.error_occurred, Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> Toast.makeText(requireContext(),
                                    forpdateam.ru.forpda.R.string.error_occurred, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
    }

    /**
     * «Убрать из избранного» — confirm, then delete by [pageFavId] via [ThemeInteractionUseCase]
     * (parity with the WebView [showDeleteInFavDialog]). Flips [pageIsInFavorite] back on success.
     */
    private fun confirmRemoveFromFavorites() {
        if (pageFavId <= 0) {
            Toast.makeText(requireContext(),
                    forpdateam.ru.forpda.R.string.fav_delete_error_id_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        val favId = pageFavId
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage(forpdateam.ru.forpda.R.string.fav_ask_delete)
                .setNegativeButton(forpdateam.ru.forpda.R.string.cancel, null)
                .setPositiveButton(forpdateam.ru.forpda.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            interactionUseCase.deleteTopicFromFavorite(favId)
                        }
                        if (view == null) return@launch
                        val ok = (result as? forpdateam.ru.forpda.model.interactors.theme
                                .ThemeInteractionUseCase.FavoriteResult.Delete)?.success == true
                        if (ok) {
                            pageIsInFavorite = false
                            pageFavId = 0
                            Toast.makeText(requireContext(),
                                    forpdateam.ru.forpda.R.string.favorite_theme_deleted, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(),
                                    forpdateam.ru.forpda.R.string.error_occurred, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
    }

    /**
     * Submit a poll vote (from [PollHeaderAdapter]) via [ThemeApi.submitPoll] — the same server write
     * the WebView JS `submitThemePoll` performs. On success the page is reloaded so the poll re-renders
     * with results; we keep the view pinned to the top so the freshly-voted poll stays in sight.
     */
    private fun submitPoll(action: String, method: String, encodedForm: String) {
        if (isSending) return
        isSending = true
        Toast.makeText(requireContext(), "Отправка голоса…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.submitPoll(action, method, encodedForm) }
            }
            isSending = false
            if (view == null) return@launch
            result.onSuccess {
                Toast.makeText(requireContext(), "Голос учтён", Toast.LENGTH_SHORT).show()
                pendingJumpToTop = true // land on the poll (now showing results), not the unread anchor
                loadTopic(loadedUrl ?: topicUrl)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Не удалось проголосовать: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Toolbar «Инфо»: show the topic hat in a popup over the theme (parity with the WebView overlay). */
    private fun onHatToolbarClick() {
        // Toggle: a second tap on the ⓘ button closes the overlay instead of reopening it.
        if (dismissThemeOverlay()) return
        // Prefer the live inline hat post (page 1); fall back to the captured topic-level hat so the popup
        // still works on deep pages where the echoed hat was stripped from the list.
        val hatItem = topicHatPostId?.let { id -> loadedItems.firstOrNull { it.postId == id } }
                ?: toolbarHatItem
                ?: return
        val ctx = requireContext()
        // A throwaway adapter renders the hat post fully (no hat-collapse in the popup) with all its
        // spoilers/links, reusing the exact post rendering. Links inside the hat go through a wrapper that
        // dismisses the overlay first, so tapping a hat link (another topic, or another page of THIS topic)
        // reveals the destination immediately instead of leaving the hat hanging over it (user report).
        val popupAdapter = TopicPostsAdapter(overlayDismissingLinkHandler(reportAwareLinkHandler), this)
        popupAdapter.setDisplaySettings(currentDisplaySettings())
        val auth = authHolder.get()
        popupAdapter.setAuthContext(auth.isAuth(), auth.userId)
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = popupAdapter
        }
        popupAdapter.submitList(listOf(hatItem))
        // No «Шапка темы» title/strip — opening it from the toolbar already makes the context clear.
        // fillHeight: the hat uses the whole working area (no empty strip below the panel).
        showThemePopup(title = null, content = rv, fillHeight = true)
    }

    /** Long-press a spoiler header → copy its deep link to the clipboard (parity with copySpoilerLink). */
    override fun onSpoilerCopyLink(item: NativePostItem, spoilNumber: Int) {
        val url = "https://4pda.to/forum/index.php?showtopic=${item.topicId}&act=findpost&pid=${item.postId}" +
                "&anchor=Spoil-${item.postId}-$spoilNumber"
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("spoiler", url))
        Toast.makeText(requireContext(), "Ссылка на спойлер скопирована", Toast.LENGTH_SHORT).show()
    }

    /** Tap an attachment image → open the swipeable image viewer over the post's whole gallery, starting
     *  on the tapped image (parity with the WebView's handleImageNavigation). */
    override fun onImageClick(galleryUrls: List<String>, index: Int) {
        if (galleryUrls.isEmpty()) return
        dismissThemeOverlay() // a tap inside the hat popup closes it too (no-op when no overlay is open)
        val start = index.coerceIn(0, galleryUrls.size - 1)
        forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
                .startActivity(requireContext(), ArrayList(galleryUrls), start)
    }

    /** Long-press an attachment image → save / open in browser / copy link (WebView-menu parity). */
    override fun onImageLongClick(imageUrl: String) {
        ImageActionsMenu.show(requireContext(), imageUrl, systemLinkHandler, clipboardHelper)
    }

    /**
     * Long-press on a downloadable file link → a chooser: «Скачать» (in-app download) or «Открыть в
     * браузере» (external). «Открыть в новой вкладке» is intentionally omitted — for a dl/post link it
     * would route straight back to the same in-app download, so it duplicates «Скачать».
     */
    /** Tap on a file-attachment link → download it. Passes the fragment's Activity context so the
     *  «Способ загрузки → Спрашивать каждый раз» chooser can appear (the old `linkHandler.handle`
     *  tap dropped the UI context, so on non-SYSTEM methods the tap silently did nothing). */
    override fun onDownloadLinkTap(url: String, fileName: String?) {
        dismissThemeOverlay() // a tap inside the hat popup closes it too (no-op when no overlay is open)
        systemLinkHandler.handleDownload(url, fileName, requireContext())
    }

    /** Long-press on an in-text hyperlink → open in browser / share / copy link. */
    override fun onLinkLongClick(url: String) {
        LinkActionsMenu.show(requireContext(), url, systemLinkHandler, clipboardHelper)
    }

    /**
     * Tap on an in-content hyperlink, fired just before it is routed to the link handler. Remember the
     * owning post so the in-tab Back history anchors to it rather than the topmost-visible post — see
     * [lastContentLinkSourcePostId] / [captureThemeBackEntry].
     */
    override fun onContentLinkTap(sourcePostId: Int, url: String) {
        if (sourcePostId > 0) lastContentLinkSourcePostId = sourcePostId
    }

    override fun onDownloadLinkLongPress(url: String, fileName: String?) {
        val ctx = requireContext()
        // Параллельно со скачиванием/открытием даём «Поделиться» и «Скопировать ссылку» — паритет
        // с меню обычной текстовой ссылки ([LinkActionsMenu]); раньше у вложения этих пунктов не было.
        val labels = arrayOf(
                getString(forpdateam.ru.forpda.R.string.app_update_action_download),
                getString(forpdateam.ru.forpda.R.string.wv_open_in_browser),
                getString(forpdateam.ru.forpda.R.string.share),
                getString(forpdateam.ru.forpda.R.string.wv_copy_link),
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setItems(labels) { _, which ->
                    when (which) {
                        0 -> systemLinkHandler.handleDownload(url, fileName, ctx)
                        1 -> systemLinkHandler.handle(url)
                        2 -> forpdateam.ru.forpda.common.Utils.shareText(ctx, url)
                        3 -> forpdateam.ru.forpda.common.Utils.copyToClipBoard(url, clipboardHelper)
                    }
                }
                .setNegativeButton(forpdateam.ru.forpda.R.string.cancel, null)
                .showWithStyledButtons()
    }

    /** Header tap on the hat post itself toggles its body. Session-only: a manual collapse lasts until the
     *  topic is re-opened, then the global «Шапка темы при открытии» setting decides again (no per-topic
     *  memory — the global setting must always win on open, per user report). */
    override fun onToggleHat() {
        val hatId = topicHatPostId ?: return
        hatCollapsed = !hatCollapsed
        postsAdapter.setTopicHat(hatId, hatCollapsed)
    }

    /**
     * Apply the initial inline-hat state ONCE per fresh topic open (page 1) straight from the global
     * «Шапка темы при открытии» setting (EXPANDED → open). Guarded by [hatInitialStateAppliedTopicId] so an
     * in-session reload (send-post/refresh) doesn't clobber a manual toggle; [openThemeFromNavigator] resets
     * that guard so genuinely re-opening the topic re-applies the setting (a manual collapse is NOT persisted).
     */
    private fun applyInitialHatCollapsedState(hatPostId: Int?) {
        if (hatPostId == null) return // no inline hat on this page (deep page / no hat)
        val topicId = pageTopicId
        if (topicId <= 0 || topicId == hatInitialStateAppliedTopicId) return
        val open = themeUseCase.getTopicHeaderInitialState() ==
                forpdateam.ru.forpda.common.Preferences.Main.TopicHeaderInitialState.EXPANDED
        hatCollapsed = !open
        hatInitialStateAppliedTopicId = topicId
    }

    /**
     * Optional horizontal-swipe page navigation (setting «Свайпы страниц», default OFF): a deliberate
     * left drag jumps to the next page, right to the previous. Intercepts the gesture only once
     * HORIZONTAL travel clearly dominates (so vertical scroll is never stolen); intercepting hands the
     * child view an ACTION_CANCEL, which is what prevents a link tap firing mid-swipe over hat/quote
     * links. Being opt-in, it can't regress the default reading experience.
     */
    // ---- Gesture indicator overlay (parity with the WebView pull-to-refresh / page-swipe overlays) ----
    // A centered rounded surface card with a big direction glyph, a label and a horizontal progress bar,
    // shown WHILE the user performs the bottom-up refresh or the classic page-swipe gesture, so the gesture
    // is visible before release (previously the native engine gave no feedback until after release).
    private var gestureOverlay: android.widget.LinearLayout? = null
    private var gestureOverlayGlyph: TextView? = null
    private var gestureOverlayLabel: TextView? = null
    private var gestureOverlayProgress: android.widget.ProgressBar? = null

    private fun ensureGestureOverlay(): android.widget.LinearLayout {
        gestureOverlay?.let { return it }
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val glyph = TextView(ctx).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 22f
            maxLines = 1
        }
        val label = TextView(ctx).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 12f
            maxLines = 1
        }
        val progress = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant))
        }
        val overlay = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f).coerceAtLeast(1),
                        ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant))
            }
            androidx.core.view.ViewCompat.setElevation(this, dp(6f).toFloat())
            val wrap = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            addView(glyph, android.widget.LinearLayout.LayoutParams(wrap, wrap))
            addView(label, android.widget.LinearLayout.LayoutParams(wrap, wrap).apply {
                topMargin = dp(4f); bottomMargin = dp(6f)
            })
            addView(progress, android.widget.LinearLayout.LayoutParams(dp(120f), dp(4f)))
        }
        // Attach to the coordinator (the FAB's parent), NOT fragment_content: the RecyclerView there
        // draws over a fragment_content sibling regardless of elevation, so the overlay stayed hidden.
        coordinatorLayout.addView(overlay, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.CENTER
        })
        gestureOverlay = overlay
        gestureOverlayGlyph = glyph
        gestureOverlayLabel = label
        gestureOverlayProgress = progress
        return overlay
    }

    /** Show/update the gesture indicator with a direction [glyph], a [label] and 0..1 [progress]. */
    private fun showGestureIndicator(glyph: String, label: String, progress: Float) {
        if (view == null) return
        val overlay = ensureGestureOverlay()
        val n = progress.coerceIn(0f, 1f)
        gestureOverlayGlyph?.text = glyph
        gestureOverlayLabel?.text = label
        gestureOverlayProgress?.progress = (n * 100f).toInt()
        overlay.visibility = View.VISIBLE
        overlay.alpha = (0.4f + n * 0.6f).coerceAtMost(1f)
        overlay.bringToFront()
    }

    private fun hideGestureIndicator() {
        gestureOverlay?.let {
            it.visibility = View.GONE
            it.alpha = 0f
            gestureOverlayProgress?.progress = 0
        }
    }

    private fun installPageSwipeDetector() {
        val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        val minDist = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var claimed = false
            /** Set once this gesture has done real vertical work — the finger is scrolling, so page swipes
             *  stay disarmed until it lifts, no matter how the pointer drifts sideways afterwards. */
            private var verticalLocked = false
            /** Largest vertical excursion from the down point seen SO FAR in this gesture. A plain
             *  `e.y - downY` cancels itself out when you scroll down and back up, which made an up-down
             *  scroll with a little sideways drift satisfy the `|dx| > |dy|` test and flip the page. */
            private var maxAbsDy = 0f

            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                // Page swipes are a CLASSIC-only navigation (the setting itself says «доступно только в
                // классическом режиме»); in HYBRID you scroll, so never steal horizontal gestures there —
                // even if the stored flag is still true from a previous CLASSIC session.
                if (!isClassicMode() || !mainPreferencesHolder.getTopicPageSwipeEnabled()) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.x; downY = e.y; claimed = false
                        maxAbsDy = 0f
                        // Finger landing on a list that is still gliding = a catch-the-fling scroll, never a swipe.
                        verticalLocked = rv.scrollState !=
                                androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        maxAbsDy = kotlin.math.max(maxAbsDy, kotlin.math.abs(dy))
                        // Once the finger has scrolled past the slop, this gesture belongs to the list. Latch it:
                        // a later sideways drift (or a scroll back to the start, which zeroes `dy`) must not
                        // resurrect the page swipe. Fixes «скроллю вверх-вниз — листает страницу».
                        if (!claimed && maxAbsDy > touchSlop) {
                            if (!verticalLocked) {
                                verticalLocked = true
                                updateRefreshGesture() // undo the pre-empt below; this is a scroll after all
                            }
                            return false
                        }
                        if (verticalLocked) return false
                        // Horizontal intent detected EARLY (lower bar than the claim below): pre-empt the parent
                        // SwipeRefreshLayout, whose dy>touchSlop threshold otherwise wins the race and steals a
                        // left/right page swipe started near the TOP (where pull-to-refresh is armed). Restored on
                        // UP/CANCEL. Fixes «свайп страницы вверху темы не срабатывает — ловит обновление сверху».
                        if (!claimed && kotlin.math.abs(dx) > touchSlop &&
                                kotlin.math.abs(dx) > maxAbsDy && refreshLayout.isEnabled) {
                            refreshLayout.isEnabled = false
                        }
                        if (!claimed && kotlin.math.abs(dx) > touchSlop * 3 &&
                                kotlin.math.abs(dx) > maxAbsDy * 2f) {
                            claimed = true
                            return true // steal the gesture → child gets CANCEL (no link tap / scroll)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        updateRefreshGesture() // never claimed → restore pull-to-refresh
                }
                return false
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val prog = (kotlin.math.abs(dx) / minDist).coerceIn(0f, 1f)
                        val armed = kotlin.math.abs(dx) >= minDist
                        // Finger left (dx<0) → NEXT page (→); finger right (dx>0) → PREVIOUS page (←).
                        if (dx < 0) {
                            showGestureIndicator("→", getString(if (armed)
                                    forpdateam.ru.forpda.R.string.theme_page_swipe_release_next
                                else forpdateam.ru.forpda.R.string.theme_page_swipe_pull_next), prog)
                        } else {
                            showGestureIndicator("←", getString(if (armed)
                                    forpdateam.ru.forpda.R.string.theme_page_swipe_release_previous
                                else forpdateam.ru.forpda.R.string.theme_page_swipe_pull_previous), prog)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = e.x - downX
                        if (kotlin.math.abs(dx) > minDist) {
                            if (dx < 0) jumpToPage(barCurrentPage + 1) else jumpToPage(barCurrentPage - 1)
                        }
                        claimed = false
                        hideGestureIndicator()
                        updateRefreshGesture() // restore pull-to-refresh after the swipe
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        claimed = false
                        hideGestureIndicator()
                        updateRefreshGesture() // restore pull-to-refresh after the swipe
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        })
    }

    /**
     * Bottom-edge pull-up refresh («Обновление свайпом снизу», default ON) — parity with the WebView's
     * BottomRefreshGestureController. Arms only at the TRUE bottom of the list (nothing more to scroll,
     * so it never fights hybrid next-page loading), captures after a clear controlled UPWARD drag (not a
     * fling, not a horizontal swipe), and on release past the threshold reloads the current page.
     */
    private fun installBottomRefreshDetector() {
        val vc = android.view.ViewConfiguration.get(requireContext())
        val touchSlop = vc.scaledTouchSlop
        val density = resources.displayMetrics.density
        val captureDist = kotlin.math.max(touchSlop * 3f, 48f * density)
        val triggerDist = 230f * density
        val maxReleaseVelocity = 1450f * density
        val minDurationMs = 240L
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var downAt = 0L
            private var captured = false
            private var blocked = false
            private var vt: android.view.VelocityTracker? = null

            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!mainPreferencesHolder.getTopicBottomRefreshGestureEnabled()) return false
                if (messagePanel?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.x; downY = e.y; downAt = android.os.SystemClock.uptimeMillis()
                        captured = false
                        // Arm only when already at the very bottom (no more content below) and not mid-reload.
                        blocked = rv.canScrollVertically(1) || refreshLayout.isRefreshing || e.pointerCount > 1
                        vt = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        vt?.addMovement(e)
                        if (blocked || e.pointerCount != 1) return false
                        val up = downY - e.y
                        val horiz = kotlin.math.abs(e.x - downX)
                        if (up <= 0f) return false
                        if (up < captureDist) return false
                        if (up < horiz * 1.5f) { blocked = true; return false } // horizontal → not ours
                        if (rv.canScrollVertically(1)) { blocked = true; return false } // left the bottom
                        captured = true
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                vt?.addMovement(e)
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!captured) return
                        val up = downY - e.y
                        val prog = (up / triggerDist).coerceIn(0f, 1f)
                        showGestureIndicator("↑", getString(if (prog >= 1f)
                                forpdateam.ru.forpda.R.string.theme_bottom_refresh_release
                            else forpdateam.ru.forpda.R.string.theme_bottom_refresh_pull), prog)
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val up = downY - e.y
                        vt?.computeCurrentVelocity(1000)
                        val vel = kotlin.math.abs(vt?.yVelocity ?: 0f)
                        val dur = android.os.SystemClock.uptimeMillis() - downAt
                        if (captured && up >= triggerDist && vel <= maxReleaseVelocity && dur >= minDurationMs &&
                                !refreshLayout.isRefreshing) {
                            refreshFromBottom()
                        }
                        hideGestureIndicator()
                        reset(rv)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        hideGestureIndicator()
                        reset(rv)
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}

            private fun reset(rv: androidx.recyclerview.widget.RecyclerView) {
                rv.parent?.requestDisallowInterceptTouchEvent(false)
                vt?.recycle(); vt = null
                captured = false; blocked = false
            }
        })
    }

    /**
     * Bottom-up «обновление» gesture: the user is at the TRUE bottom of the topic (the gesture only
     * arms there), so reload the LAST loaded page — not [loadedUrl], which stays pinned to the entry
     * page even after infinite-scrolling down — and land back at the bottom. Without the
     * [pendingJumpToBottom] flag the fresh load's [applyInitialAnchor] falls to the page TOP, which is
     * the reported «после обновления кидает на первый пост последней страницы» bug.
     *
     * «Вернуться на низ» верно только когда обновление не принесло новых постов. Принесло — низ
     * оказывается ПОСЛЕДНИМ непрочитанным, а всё новое уезжает вверх (и метится прочитанным при первой
     * же записи границы в [recordReadBoundaryAtRest] — она монотонна по максимальному id). Поэтому
     * запоминаем последний виденный пост: [applyInitialAnchor] посадит якорь на первый пост новее него.
     */
    private fun refreshFromBottom() {
        val url = if (pagination.isInitialised) pagination.pageUrl(pagination.loadedPage) else (loadedUrl ?: topicUrl)
        pendingJumpToBottom = true
        pendingRefreshSeenUpToPostId = loadedItems.maxOfOrNull { it.postId } ?: 0
        refreshFollowNextPageArmed = !isClassicMode()
        loadTopic(url, preserveRefreshIntent = true)
    }

    /**
     * CLASSIC reading mode shows one page at a time with the bottom pagination bar (no infinite
     * scroll); HYBRID (default) is continuous infinite scroll with no bar. Mirrors the WebView
     * «Режим чтения тем» setting.
     */
    private fun isClassicMode(): Boolean =
            mainPreferencesHolder.getTopicScrollMode() ==
                    forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.CLASSIC

    /**
     * Handle the topic «шапка» (4pda echoes the topic's first post at the top of EVERY page):
     *  - real first page → detect the hat, remember its id, and RETURN it (rendered as a collapsible
     *    block on page 1 only);
     *  - deeper page → strip the repeated copy from [page].posts in place (so it never shows again) and
     *    return null. Mirrors the WebView's TopicPrependedHatPolicy.stripFromNonFirstPage.
     */
    private fun processHatForPage(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage): Int? {
        val policy = forpdateam.ru.forpda.presentation.theme.TopicPrependedHatPolicy
        if (page.pagination.current <= 1) {
            val hatPost = policy.detectPrependedHat(page)?.takeIf { it.id > 0 }
            val hatId = hatPost?.id
            if (hatId != null) {
                knownHatPostId = hatId
                toolbarHatItem = mapper.map(hatPost) // hold the hat for the toolbar ⓘ popup on every page
            }
            return hatId
        }
        // Deep page: 4pda echoes the topic's FIRST post (the hat) at the very top of every page. Only ever
        // strip that LEADING post — never a middle one (the WebView policy's number/anchor heuristics can
        // mis-resolve here and delete the open's anchor target while the real hat, whose number the parser
        // leaves at the page's start index, survives — device log topic 1103268 p1350). Identify the
        // leading hat by, in order: the id learned from page 1, the server marker, or a structural signal —
        // the leading post is far OLDER (much smaller id) than the page's own posts.
        val posts = page.posts
        val first = posts.firstOrNull()?.takeIf { it.id > 0 } ?: return null
        val second = posts.getOrNull(1)?.takeIf { it.id > 0 }
        val leadGap = if (second != null) second.id.toLong() - first.id.toLong() else 0L
        // A typical intra-page gap between consecutive posts — the leading hat's gap must dwarf it, so a
        // merely-slow topic (large but uniform gaps) isn't mistaken for a hat.
        val typicalGap = posts.getOrNull(2)?.takeIf { it.id > 0 }
                ?.let { it.id.toLong() - (second?.id?.toLong() ?: it.id.toLong()) }
                ?.coerceAtLeast(1L) ?: 1L
        val structuralHat = leadGap > HAT_LEADING_ID_GAP && leadGap > typicalGap * HAT_LEADING_GAP_RATIO
        val isLeadingHat = knownHatPostId == first.id ||
                (page.prependedHatPostId > 0 && page.prependedHatPostId == first.id) ||
                structuralHat
        if (isLeadingHat) {
            if (knownHatPostId == null) knownHatPostId = first.id
            // Capture the echoed hat for the toolbar ⓘ popup BEFORE stripping it from the deep page, so the
            // button works even when the topic is opened directly on a deep page (never showing page 1).
            if (toolbarHatItem == null) toolbarHatItem = mapper.map(first)
            posts.removeAt(0)
        }
        return null
    }

    /** Tag [items] with the 1-based [pageNumber] they were loaded from (drives the «Страница N» dividers). */
    private fun tagPage(items: List<NativePostItem>, pageNumber: Int): List<NativePostItem> =
            if (pageNumber <= 0) items else items.map {
                if (it.pageNumber == pageNumber) it else it.copy(pageNumber = pageNumber)
            }

    /** Drop posts by forum-blacklisted authors (parity with the WebView, which hides their posts). Applied
     *  at load time so all the index-based anchor/pagination logic keeps working on the visible list. */
    private fun filterBlacklisted(items: List<NativePostItem>): List<NativePostItem> =
            items.filterNot { it.userId > 0 && themeUseCase.isForumBlacklisted(it.userId, it.nick) }

    /**
     * Submit [loadedItems] to the adapter with the «Страница N» divider labels recomputed over the whole
     * list first, so the label lives IN the item (DiffUtil then rebinds a page-boundary post when a
     * prepended page shifts it — a purely positional divider would go stale). The hat never gets one.
     */
    private fun submitPosts(commit: (() -> Unit)? = null) {
        var prevPage = 0
        val list = loadedItems.map { item ->
            val label = if (prevPage != 0 && item.pageNumber > 0 && item.pageNumber != prevPage &&
                    item.postId != topicHatPostId) {
                "Страница ${item.pageNumber}"
            } else {
                null
            }
            prevPage = item.pageNumber
            if (item.pageDividerLabel == label) item else item.copy(pageDividerLabel = label)
        }
        if (commit != null) postsAdapter.submitList(list, commit) else postsAdapter.submitList(list)
        prewarmBodyMarkup(list)
    }

    /**
     * Parse the submitted posts' markup into the renderer's span cache OFF the main thread, so the bind
     * that eventually shows a post finds its spans ready instead of running a full `Html.fromHtml` parse
     * inside the frame (measured: 2–22 ms per post on the UI thread, the main source of the «микролаги и
     * поддёргивания» on a fast fling).
     *
     * Fire-and-forget on the fragment's lifecycle scope: it is a pure cache warm-up, so a cancelled or
     * lost run costs nothing but the old behaviour (the bind parses it itself). Already-cached bodies are
     * skipped, so re-submits (enrichment merge, page prepend, divider relabel) do no work.
     */
    private fun prewarmBodyMarkup(items: List<NativePostItem>) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                for (item in items) {
                    if (!isActive) return@withContext
                    BodyBlockViewFactory.prewarm(item.blocks)
                }
            }
        }
    }

    /**
     * Prefetch distance for hybrid infinite scroll: ~one viewport from the edge, mirroring the WebView's
     * pixel threshold (`height - (scrollTop + viewport) <= threshold`). Pixel-based (not item-count) so a
     * single very tall post can't leave the loader un-armed near the boundary.
     */
    private fun prefetchThresholdPx(): Int {
        // «Умная предзагрузка страниц» OFF → load only AT the edge (threshold 0), not a viewport ahead.
        if (!mainPreferencesHolder.getSmartPreload()) return 0
        return (recyclerView.height.coerceAtLeast(1) * HYBRID_PREFETCH_VIEWPORT_FRACTION).toInt()
    }

    /** Downward infinite scroll: when the content scrolled to within ~a viewport of the bottom. */
    private fun maybeLoadNextPage() {
        // Входит из отложенных recyclerView.post{}: к моменту выполнения view мог быть
        // уничтожен (onDestroyView), а loadNextPage дёргает viewLifecycleOwner (крашит
        // при getView()==null) и recyclerView. Гейтим по живому view.
        if (view == null) return
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        // Never run a next- and prev-page load at once — serialised so only ONE loading spinner shows.
        if (isLoadingNextPage || isLoadingPrevPage || !pagination.hasNextPage()) return
        val range = recyclerView.computeVerticalScrollRange()
        val offset = recyclerView.computeVerticalScrollOffset()
        val extent = recyclerView.computeVerticalScrollExtent()
        val estimateDistance = range - (offset + extent)
        // computeVerticalScroll* с постами переменной высоты — ОЦЕНКА, которая у истинного конца может
        // «завышать» остаток (тот же баг, из-за которого нижняя панель иногда не показывалась — см.
        // distanceToListBottomPx). При пороге 0 (умная предзагрузка OFF) это оставляло подгрузку невзведённой
        // при открытии сразу на последнем посте страницы. Дублируем надёжной геометрией края (0 у истинного низа).
        val distanceToBottom = minOf(estimateDistance, distanceToListBottomPx())
        if (distanceToBottom <= prefetchThresholdPx()) loadNextPage()
    }

    private fun loadNextPage() {
        if (view == null) return // stale post after onDestroyView → viewLifecycleOwner крашит
        val url = pagination.nextPageUrl() ?: return
        isLoadingNextPage = true
        val epoch = loadEpoch
        showHybridLoading(atTop = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            if (epoch != loadEpoch) {
                // A full reload overtook this page load — drop the stale posts. Don't touch the loading
                // flags: loadTopic already reset them, and a NEWER page load may own them by now.
                hideHybridLoading()
                return@launch
            }
            result.onSuccess { page ->
                processHatForPage(page) // strip the repeated hat 4pda echoes onto this deeper page
                recordMaxLoaded(page) // догрузка вниз углубляет трек «докуда грузили» (кросс-девайс детект)
                val newItems = pagination.registerAndFilterNew(
                        filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current)))
                pagination.onPageAppended(page.pagination.current, page.pagination)
                if (newItems.isNotEmpty()) {
                    loadedItems.addAll(newItems)
                    submitPosts {
                        // Chain another prefetch if the appended page still leaves the bottom underfilled
                        // (short pages / tall viewport), so reading forward never stalls at a page seam.
                        if (view != null) recyclerView.post { maybeLoadNextPage() }
                    }
                    // Enrich the appended page too (post ratings «ka_p» + 💬 counts live only in desktop
                    // HTML) — WebView parity: it defers a merge for every hybrid-appended page, not just the
                    // first. Without this, ratings/counts appeared only on the initially opened page.
                    enrichLoadedPage(page)
                }
                updatePaginationBar() // totalPages may have grown

            }
            // On failure: silently stop; the user can pull-to-refresh. isLoadingNextPage resets so a
            // later scroll retries.
            isLoadingNextPage = false
            hideHybridLoading()
        }
    }

    /** Upward infinite scroll: when the content scrolled to within ~a viewport of the top. */
    private fun maybeLoadPrevPage() {
        if (view == null) return // stale post after onDestroyView → viewLifecycleOwner/recyclerView крашат
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        if (isLoadingPrevPage || isLoadingNextPage || !pagination.hasPrevPage()) return
        // Как и для нижнего края (см. maybeLoadNextPage): computeVerticalScrollOffset — оценка, у самого
        // верха может не дотянуть до 0 при пороге 0, поэтому дублируем надёжной геометрией верхнего края.
        val topDistance = minOf(recyclerView.computeVerticalScrollOffset(), distanceToListTopPx())
        if (topDistance <= prefetchThresholdPx()) loadPrevPage()
    }

    /**
     * When the topic opens on its LAST page and the few posts there don't fill the screen, pull previous
     * pages in and anchor the newest posts to the bottom. Without this: the empty area below the last post
     * (issue «пустой блок в конце темы»), AND scrolling back is impossible — a short page produces no scroll
     * events, so the upward infinite scroll (maybeLoadPrevPage) would never fire and previous pages stay
     * unreachable. Chained in [continueFillingLastPage] until the viewport is full or page 1 is reached.
     */
    private fun maybeFillLastPage() {
        if (isClassicMode() || fillingLastPage || isLoadingPrevPage || isLoadingNextPage) return
        if (pagination.loadedPage < pagination.totalPages) return // only at the very end of the topic
        if (!pagination.hasPrevPage()) return
        if (recyclerView.computeVerticalScrollRange() > recyclerView.height) return // already fills the screen
        fillingLastPage = true
        // Hide the list while filling so the user never sees the transient «few posts at the top + empty
        // block below → jump to the bottom» — only the final, filled state (posts at the bottom) is revealed.
        recyclerView.alpha = 0f
        loadPrevPage()
    }

    /** After a fill-prepend: pull another previous page if still short, else reveal anchored at the bottom. */
    private fun continueFillingLastPage() {
        recyclerView.post {
            if (view == null) { finishLastPageFill(scrollToBottom = false); return@post }
            if (pagination.hasPrevPage() && recyclerView.computeVerticalScrollRange() <= recyclerView.height) {
                loadPrevPage() // still under-filled → pull one more previous page
            } else {
                finishLastPageFill(scrollToBottom = true)
            }
        }
    }

    /** End a last-page fill: optionally anchor the newest post to the bottom, then reveal the list. */
    private fun finishLastPageFill(scrollToBottom: Boolean) {
        fillingLastPage = false
        if (scrollToBottom) {
            val last = (recyclerView.adapter?.itemCount ?: 0) - 1
            if (last >= 0) {
                // This path only runs for a last page SHORTER than the viewport (maybeFillLastPage trigger),
                // so the last post always FITS — a plain bottom-clamp is correct and flash-free. The resting
                // gap that keeps its border off the tab bar comes from [bottomRestGapPx] padding; bottomAlignPost
                // is a defensive pull-up (and no-ops on a taller-than-viewport post, see its height guard).
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(last)
                bottomAlignPost(last)
            }
            anchoredBottomPostId = loadedItems.lastOrNull()?.postId // survive enrichment growth (see reanchorBottomAfterGrowth)
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) android.util.Log.i("FPDA_CLEAR", "anchor SET(fillLastPage)=$anchoredBottomPostId")
        }
        recyclerView.alpha = 1f
    }

    /**
     * The TOP swipe-down pull-to-refresh belongs to CLASSIC mode only. In HYBRID (infinite scroll) the top
     * pull must feed upward pagination, never reload — a swipe-refresh there yanks the reader to a different
     * page (the «прыжок» on scroll-up). HYBRID refreshes exclusively via the bottom-up gesture
     * (installBottomRefreshDetector); manual reload stays on the toolbar refresh button in both modes.
     */
    private fun updateRefreshGesture() {
        refreshLayout.isEnabled = isClassicMode()
    }

    private fun loadPrevPage() {
        if (view == null) return // stale post after onDestroyView → viewLifecycleOwner крашит
        val url = pagination.prevPageUrl() ?: return
        isLoadingPrevPage = true
        val epoch = loadEpoch
        showHybridLoading(atTop = true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) { isLoadingPrevPage = false; return@launch }
            if (epoch != loadEpoch) {
                // A full reload overtook this page load — drop the stale posts. Don't touch the loading
                // flags or fillingLastPage: loadTopic already reset them (and re-showed the list).
                hideHybridLoading()
                return@launch
            }
            var prepended = false
            result.onSuccess { page ->
                recordMaxLoaded(page) // монотонно — догрузка ВВЕРХ трек не понижает, но фиксирует факт загрузки
                // Prepending page 1 brings the real hat into view — keep it and light the toolbar ⓘ; a
                // deeper page's repeated hat is stripped instead.
                val hatId = processHatForPage(page)
                if (hatId != null) {
                    topicHatPostId = hatId
                    // Hybrid: a deep-page open that scrolls UP to page 1 brings the real hat in through this
                    // prepend path, not renderTopicPage — so apply «Шапка темы при открытии» here too, else the
                    // setting would only ever take effect in classic mode (where page 1 always renders directly).
                    applyInitialHatCollapsedState(hatId)
                    postsAdapter.setTopicHat(hatId, hatCollapsed)
                }
                // Prepending page 1 also brings the poll into view — cache it so the toolbar «Опрос» button
                // lights up (and the inline poll header appears). The hat button follows toolbarHatItem.
                if (page.pagination.current <= 1 && page.poll != null && currentPoll == null) {
                    currentPoll = page.poll
                    cachedPollTopicId = page.id
                    pollHeaderAdapter.setPoll(page.poll)
                }
                if (hatId != null || toolbarHatItem != null) pageHasHat = true
                if (currentPoll != null) pageHasPoll = true
                if (pageHasHat || pageHasPoll) refreshToolbarState()
                val newItems = pagination.registerAndFilterNew(
                        filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current)))
                pagination.onPagePrepended(page.pagination.current)
                updateRefreshGesture() // reaching page 1 re-enables pull-to-refresh
                if (newItems.isNotEmpty()) {
                    prependPreservingPosition(newItems)
                    prepended = true
                    // Enrich the prepended page too (ratings/💬 counts), same as the initial + next-page
                    // paths — otherwise scrolling UP into earlier pages would show them without ratings.
                    enrichLoadedPage(page)
                }
            }
            // End the prev-page load reliably HERE (mirrors loadNextPage) — NOT inside the prepend's
            // submitList commit callback. AsyncListDiffer drops that callback when a later submitList
            // (enrichLoadedPage) supersedes it before it commits, which left isLoadingPrevPage stuck true:
            // the top spinner span forever and prev-pagination jammed. The prepend callback now only
            // restores scroll position and continues the last-page fill.
            isLoadingPrevPage = false
            hideHybridLoading()
            // A prev page with no NEW posts never prepends, so end the fill here (reveal the list) or
            // fillingLastPage would stay stuck true.
            if (!prepended && fillingLastPage) finishLastPageFill(scrollToBottom = true)
        }
    }

    /**
     * Insert [newItems] at the top and keep the currently-visible post fixed on screen. The anchor
     * is the current first-visible post BY ID (not index) + its pixel offset, so the prepend never
     * makes the content jump under the user's finger.
     */
    private fun prependPreservingPosition(newItems: List<NativePostItem>) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val header = headerOffset()
        val anchorConcatPos = lm?.findFirstVisibleItemPosition() ?: header
        val anchorOffset = lm?.findViewByPosition(anchorConcatPos)?.top ?: 0
        // Concat position → post index (subtract the poll header, if any).
        val anchorPostId = loadedItems.getOrNull(anchorConcatPos - header)?.postId

        loadedItems.addAll(0, newItems)
        submitPosts {
            if (view != null) {
                val newIndex = anchorPostId
                        ?.let { id -> loadedItems.indexOfFirst { it.postId == id } }
                        ?.takeIf { it >= 0 }
                        ?: newItems.size
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(newIndex + header, anchorOffset)
            }
            // Flag reset + spinner hide now happen in loadPrevPage's coroutine (robust against a dropped
            // commit callback); here we only restore scroll position and continue the last-page fill.
            if (fillingLastPage) continueFillingLastPage()
        }
    }

    // region ThemeTabHost — navigator-driven tab reuse / topic switch

    override fun getOpenTopicIdForReuse(): Int? {
        val url = loadedUrl ?: topicUrl
        return ThemeApi.extractTopicIdFromUrl(url)?.takeIf { it > 0 }
    }

    override fun loadThemeUrlFromNavigator(
            url: String,
            sourceScreen: String,
            openIntent: String,
            listHints: TopicOpenListHints?,
    ) {
        // The navigator has already written the new url/title into arguments; refresh the title
        // and reload the list for the new topic (or the new findpost within the same topic).
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
        if (view != null) {
            // Resolve the open target with the SAME policy the WebView presenter uses (getnewpost for an
            // unread open, findpost for an explicit post, page 1 otherwise) — the navigator hands us the
            // bare topic url, so loading it raw always landed on page 1 first. Deduped against the load
            // onViewCreated already issued: the navigator echoes the initial open right after onViewCreated,
            // and loading page 1 there is exactly what caused the visible «page 1 → jump to unread» flash.
            val resolved = resolveNavigatorOpenUrl(url, sourceScreen, openIntent, listHints)
            // Дедуп ТОЛЬКО против летящего эха первого открытия (тот же URL, запрос ещё в полёте).
            // Совпавший URL при ЗАВЕРШЁННОЙ загрузке = настоящее повторное открытие (реюз живого таба
            // из списка) — обязаны перезагрузить: иначе свежие посты не тянутся, пока юзер не дёрнет
            // руками (см. [loadInFlight]). Бонус: повторный тап той же ссылки заново якорится.
            if (resolved != lastRequestedUrl || !loadInFlight) {
                // The in-tab Back history exists for IN-TOPIC link taps (source="link"): tapping a post
                // link inside the tab replaces its content, and «Назад» (HISTORY) should return to where
                // you were. But this same reuse path also fires when an EXTERNAL list (search / «Мои
                // сообщения» / избранное / …) opens a post in an already-open topic tab — there the user
                // entered the topic FROM the list, so «Назад» must EXIT the tab back to that list, not
                // replay a prior post. So only record history for non-external-list sources; otherwise the
                // captured entry would swallow the back press and keep the user stuck in the topic.
                if (!isExternalListOpenSource(sourceScreen)) {
                    captureThemeBackEntry()?.let { themeBackStack.addLast(it) }
                }
                // Лог 11_07-11-32 (1080563): свежее навигаторное открытие ре-армит резюм на границу
                // прочитанного. [boundaryResumeArmed] взводился ТОЛЬКО в onViewCreated, а этот путь
                // РЕЮЗАЕТ живой таб (открыл тему → назад → открыл ту же тему из списка) — флаг оставался
                // потреблённым первым открытием, резюм молчал, и серверный all-read bottom-редирект
                // (страницу пометил прочитанной сам GET первого открытия) сажал на ПОСЛЕДНИЙ пост мимо
                // непрочитанных, а мгновенный mark-read стирал границу. Взводим строго в ветке реальной
                // загрузки: дедуп-эхо навигатора (resolved == lastRequestedUrl) не ре-армит; findpost-дип-
                // линки отфильтрует сам maybeResumeToReadBoundary; back-по-истории гасит флаг отдельно.
                boundaryResumeArmed = true
                // Genuine re-open of a (reused) topic tab → let the inline hat re-apply the global «Шапка
                // при открытии» setting again. Without this the guard, still holding this topic id from the
                // first open, would skip re-applying and the hat would keep whatever collapsed state the user
                // last left it in — the exact «once collapsed, always collapsed» report. Echo/dedup opens
                // never reach this branch, so an in-place reload still can't clobber a mid-session toggle.
                hatInitialStateAppliedTopicId = 0
                loadTopic(resolved)
            }
        }
        // If the view is not created yet, onViewCreated will load from the (already updated) args.
    }

    /**
     * True when a navigator open originates from an EXTERNAL list screen (search, «Мои сообщения» / QMS,
     * избранное, упоминания, трекер, лента, …) rather than from an in-topic link tap (`source="link"`).
     * Such an open re-enters the topic tab from that list, so its Back must exit the tab, not build in-tab
     * history. Kept as a denylist so a genuine in-topic link (source="link"/"unknown") still records history.
     */
    private fun isExternalListOpenSource(sourceScreen: String): Boolean =
            sourceScreen.trim().lowercase() in EXTERNAL_LIST_OPEN_SOURCES

    /** Snapshot the current url + scroll anchor for the in-tab Back history. */
    private fun captureThemeBackEntry(): ThemeBackEntry? {
        val url = loadedUrl ?: return null
        // Prefer the post whose in-content link was just tapped over the topmost-visible post: a link
        // low in a post makes findFirstVisibleItemPosition() report an EARLIER post peeking at the top,
        // so Back would land above the source. Consume the flag regardless so it never leaks forward.
        val tappedSource = lastContentLinkSourcePostId.also { lastContentLinkSourcePostId = 0 }
        if (view == null) return ThemeBackEntry(url, tappedSource.coerceAtLeast(0), 0)
        val lm = recyclerView.layoutManager as? LinearLayoutManager
                ?: return ThemeBackEntry(url, tappedSource.coerceAtLeast(0), 0)
        if (tappedSource > 0) {
            val idx = loadedItems.indexOfFirst { it.postId == tappedSource }
            if (idx >= 0) {
                // The source post's own on-screen top → restore re-aligns to exactly where the link was
                // tapped. If it is not the first-visible post, `top` is positive (it sits below the edge).
                val top = lm.findViewByPosition(idx + headerOffset())?.top ?: 0
                return ThemeBackEntry(url, tappedSource, top)
            }
        }
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return ThemeBackEntry(url, 0, 0)
        val item = loadedItems.getOrNull(firstPos - headerOffset())
        val top = lm.findViewByPosition(firstPos)?.top ?: 0
        return ThemeBackEntry(url, item?.postId ?: 0, top)
    }

    override fun onTabStackBecameCurrent() {
        // Load lazily if this tab became visible before a load was ever REQUESTED (e.g. created hidden).
        // Guard on lastRequestedUrl, not loadedUrl: the navigator makes the tab current right after
        // onViewCreated issues its resolved (getnewpost) load, while loadedUrl is still null — checking
        // loadedUrl here fired a redundant bare page-1 load in parallel (the «page 1 → jump» flash). Also
        // resolve the open target so this safety-net path never lands on page 1 either.
        if (lastRequestedUrl == null && view != null) {
            loadTopic(resolveInitialOpenUrl())
        }
        // The user may have changed font/avatar prefs while away — re-apply on return.
        if (view != null) {
            applyDisplaySettings()
            setupFab() // the «Умная кнопка темы» pref may have been toggled while away
            applyToolbarAutoHide() // the «Поведение тулбара» pref may have been toggled while away
            updateRefreshGesture() // the «Режим чтения тем» pref may have been toggled while away
            healOrphanedLoadingIndicator() // clear a spinner stranded by a superseded/interrupted open
        }
    }

    override fun onResume() {
        super.onResume()
        messagePanel?.onResume()
        healOrphanedLoadingIndicator() // clear a spinner stranded by a superseded/interrupted open
        // Метка «эта тема сейчас на экране»: пуш о читаемой прямо сейчас теме — шум, и
        // EventsRepository/EventsCheckWorker глушат его по этой метке. Снимается в onPause.
        if (pageTopicId > 0) eventsRepository.setViewedTopic(pageTopicId)
    }

    /**
     * Self-heal for a stranded loading indicator (the «значок обновления данных висит» report on
     * notification/badge opens). The topic's «обновление данных» spinner is set on in [loadTopic] and
     * cleared only when a load reaches [renderThemePage] or its failure branch. The latest-wins epoch
     * guard and the read-boundary resume handoff deliberately return WITHOUT clearing, trusting the
     * SUCCEEDING load to own the indicator — but if that successor is itself dropped, cancelled with the
     * view, or never emits, the spinner is left spinning over already-rendered content. That is exactly
     * why re-entering the topic from a list «fixes» it (the reuse fires a fresh load that clears it).
     *
     * Whenever the tab becomes current or resumes, if a page is already rendered ([loadedUrl] != null)
     * and nothing is actually loading (no main load in flight, no hybrid page load, no last-page fill),
     * force the indicator off. Fully guarded, so it never hides a genuinely in-flight load: during a real
     * open/refresh [loadInFlight] is true, and during infinite-scroll the [isLoadingNextPage]/
     * [isLoadingPrevPage] flags are true. Idempotent — [setRefreshing]`(false)` is a no-op when nothing shows.
     */
    private fun healOrphanedLoadingIndicator() {
        if (view == null) return
        val nothingLoading = !loadInFlight && !isLoadingNextPage && !isLoadingPrevPage && !fillingLastPage
        if (loadedUrl != null && nothingLoading) {
            setRefreshing(false)
        }
    }

    override fun onPause() {
        super.onPause()
        messagePanel?.onPause()
        // Снимаем метку «тема на экране» (сворачивание/переключение вкладки) — иначе гейт
        // глушил бы пуши о теме, которую уже никто не смотрит.
        if (pageTopicId > 0) eventsRepository.clearViewedTopic(pageTopicId)
        // Разрыв цикла «открыл-глянул-закрыл»: unread-посадка без единого жеста, но весь остаток темы
        // был целиком виден и юзер задержался на экране (dwell) — считаем дочиткой: снимаем гейт
        // мгновенного mark-read и пишем границу по вьюпорту, чтобы переоткрытие не резюмило вечно на
        // те же «старые» посты (сервер уже пометил страницу прочитанной самим GET этого открытия, и
        // без этого его all-read-редирект перебивался бы findpost-резюмом на застывшую границу).
        if (view != null && loadedItems.isNotEmpty() &&
                forpdateam.ru.forpda.presentation.theme.TopicNoGestureDwellReadPolicy.shouldTreatVisibleTailAsRead(
                        suppressEndMarkReadActive = suppressEndMarkReadUntilUserScroll,
                        hadUserGesture = userScrollGestureThisSession,
                        dwellMs = android.os.SystemClock.elapsedRealtime() - sessionRenderedAtMs,
                        hasNextPage = pagination.hasNextPage(),
                        lastItemFullyVisible = (recyclerView.layoutManager as? LinearLayoutManager)
                                ?.findLastCompletelyVisibleItemPosition() == headerOffset() + loadedItems.size - 1,
                )) {
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                android.util.Log.i("FPDA_READ_BOUNDARY", "dwell_read_no_gesture topic=$pageTopicId " +
                        "dwellMs=${android.os.SystemClock.elapsedRealtime() - sessionRenderedAtMs}")
            }
            suppressEndMarkReadUntilUserScroll = false
            recordReadBoundaryAtRest()
        }
        // Страховка на выход из темы (back/сворачивание) для ГРАНИЦЫ прочитанного: выход во время
        // инерции скролла съедает финальный IDLE, и recordReadBoundaryAtRest не фиксирует последнюю
        // точку покоя — граница отстаёт, следующее открытие резюмит на уже прочитанные посты. Пишем
        // здесь по текущему вьюпорту, но ТОЛЬКО если в сессии был реальный жест: без жеста вьюпорт —
        // не доказательство чтения («открыл-глянул-закрыл»). Запись монотонна — назад не откатывает.
        if (userScrollGestureThisSession) recordReadBoundaryAtRest()
        // Выход из темы, физически стоя в САМОМ НИЗУ последней страницы, = дочитал до конца, даже без
        // жеста и без 4-сек dwell: тема часто открывается прямо на последнем (новом) посте — юзер его
        // увидел и вышел. Сервер уже пометил её прочитанной самим GET открытия, синхронизируем и локально,
        // сняв anti-glance suppress (иначе «глянул последний пост и быстро вышел» оставлял тему жирной в
        // избранном — воспроизведено на эмуляторе). НЕ трогаем сессионный путь: там «низ виден при рендере»
        // намеренно ≠ дочитал, иначе тема метилась бы прочитанной в момент открытия.
        if (view != null && pageTopicId > 0 && !pagination.hasNextPage() &&
                !recyclerView.canScrollVertically(1)) {
            suppressEndMarkReadUntilUserScroll = false
        }
        // Страховка на выход из темы (back/сворачивание): если юзер долистал ровно до конца, но
        // финальный кадр onScrolled/IDLE-settle не успел зафиксировать «низ виден» до ухода — метим
        // прочитанной здесь. Полностью защищено собственными гейтами maybeMarkTopicReadAtEnd
        // (последний пост на экране, нет след. страницы, был жест пользователя ИЛИ мы у абсолютного
        // низа — см. выше), поэтому вызвать в onPause безопасно.
        maybeMarkTopicReadAtEnd()
    }

    override fun onDestroyView() {
        recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(bottomPinLayoutListener)
        messagePanel?.onDestroy()
        messagePanel = null
        attachmentsPopup = null
        smartNavMenu?.dispose()
        smartNavMenu = null
        fabHideHandler.removeCallbacks(fabHideRunnable)
        gestureOverlay = null
        gestureOverlayGlyph = null
        gestureOverlayLabel = null
        gestureOverlayProgress = null
        super.onDestroyView()
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        messagePanel?.hidePopupWindows()
    }

    /**
     * Read the user's font-size / avatar prefs and push them into the adapter (parity with the
     * WebView path, which sets `defaultFontSize` + avatar CSS at load). Font size is an absolute
     * base (default 16); [PostDisplaySettings.textScale] is relative to that reference so the
     * default look is unchanged.
     */
    private fun applyDisplaySettings() {
        postsAdapter.setDisplaySettings(currentDisplaySettings())
    }

    /** Current post display prefs (font/avatars/density) — shared by the list and the hat popup. */
    private fun currentDisplaySettings() = TopicPostsAdapter.PostDisplaySettings(
            textScale = mainPreferencesHolder.getWebViewFontSize() / REFERENCE_FONT_SIZE,
            showAvatars = topicPreferencesHolder.getShowAvatars(),
            circleAvatars = topicPreferencesHolder.getCircleAvatars(),
            density = mainPreferencesHolder.getTopicPostDensity(),
            animatedSmiles = topicPreferencesHolder.getAnimatedSmiles(),
            flatBlocks = topicPreferencesHolder.getFlatPosts(),
            modernHeader = topicPreferencesHolder.getModernPostHeader(),
    )

    override fun onRestoredAfterChildFragmentRemoved() {
        // Native list keeps its state/scroll across a covering child fragment — nothing to restore.
    }

    // endregion

    // region PostActionListener — write actions (authorised by the user)

    override fun onVote(item: NativePostItem, up: Boolean) {
        // Rating a post is a one-shot irreversible action → confirm first (per user request).
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (up) "Повысить репутацию поста?" else "Понизить репутацию поста?")
                .setMessage("Автор: ${item.nick.orEmpty()}")
                .setNegativeButton("Отмена", null)
                .setPositiveButton(if (up) "Повысить" else "Понизить") { _, _ -> performVote(item, up) }
                .showWithStyledButtons(compact = true)
    }

    private fun performVote(item: NativePostItem, up: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.votePost(item.postId, up) }
            }
            if (view == null) return@launch
            result.onSuccess { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                // Optimistically nudge the visible rating so the change is reflected immediately.
                updateRatingOptimistically(item.postId, if (up) +1 else -1)
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: "Ошибка голосования", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateRatingOptimistically(postId: Int, delta: Int) {
        val idx = loadedItems.indexOfFirst { it.postId == postId }
        if (idx < 0) return
        val cur = loadedItems[idx]
        val newRating = ((cur.postRating?.replace("+", "")?.trim()?.toIntOrNull() ?: 0) + delta)
        // Voted once → can't vote again on that direction; drop both to avoid a second attempt.
        loadedItems[idx] = cur.copy(
                postRating = newRating.toString(),
                canPlusPostRating = false,
                canMinusPostRating = false,
        )
        submitPosts()
    }

    override fun onReply(item: NativePostItem) {
        insertIntoEditor("[snapback]${item.postId}[/snapback] [b]${item.nick},[/b] \n")
    }

    override fun onQuote(item: NativePostItem) {
        val date = item.date?.takeIf { it.isNotBlank() }?.let { " date=\"$it\"" } ?: ""
        // Full-post quote must carry the post's TEXT, not just the name/date header (mirrors the WebView
        // openFullQuote): take the raw body HTML, drop nested quote blocks, normalize DOM→editor BBCode.
        // Spoilers collapse to their title: 4pda renders NO block tag inside [quote] (verified across ~200
        // live quotes — not one holds a nested spoiler/code/quote), so a kept [spoiler] would silently spill
        // the hidden screenshots into the quote at full size.
        val body = item.rawBodyHtml?.let { raw ->
            val withoutQuotes = forpdateam.ru.forpda.common.stripHtmlQuoteBlocks(raw)
            val normalized = forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml(withoutQuotes)
                    .ifEmpty { forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml(raw) }
            val withoutNestedQuotes = forpdateam.ru.forpda.common.stripBbcodeQuotes(normalized).ifEmpty { normalized }
            forpdateam.ru.forpda.common.collapseBbcodeSpoilersForQuote(withoutNestedQuotes)
        }.orEmpty()
        insertIntoEditor("[quote name=\"${item.nick}\"$date post=${item.postId}]$body[/quote]${'\n'}")
    }

    override fun onQuoteSelection(item: NativePostItem, selectedText: String) {
        val d = item.date?.takeIf { it.isNotBlank() }?.let { " date=\"$it\"" } ?: ""
        insertIntoEditor("[quote name=\"${item.nick}\"$d post=${item.postId}]$selectedText[/quote]${'\n'}")
    }

    /** Insert BBCode/text into the panel field at the caret and reveal the editor (like the WebView). */
    private fun insertIntoEditor(text: String) {
        editingForm = null
        messagePanel?.insertText(text)
        showMessagePanel(showKeyboard = true)
    }

    override fun onEdit(item: NativePostItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val form = withContext(Dispatchers.IO) {
                runCatching {
                    val loaded = editPostApi.loadForm(item.postId)
                    // Страница правки отдаёт список вложений скриптом, поэтому в её HTML их обычно нет:
                    // спрашиваем attach init отдельно (как полноэкранный редактор), а что не отдал и он —
                    // достаём из BBCode поста. Без этого панель открывалась пустой, а сохранение уходило с
                    // пустым `file-list` и отвязывало файлы от поста.
                    if (loaded.errorCode == EditPostForm.ERROR_NONE && loaded.attachments.isEmpty()) {
                        runCatching { editPostApi.loadEditAttachments(item.postId) }
                                .getOrNull()
                                ?.let { loaded.attachments.addAll(it) }
                    }
                    loaded
                }
            }.getOrNull()
            if (view == null) return@launch
            if (form == null || form.errorCode == forpdateam.ru.forpda.entity.remote.editpost.EditPostForm.ERROR_NO_PERMISSION) {
                Toast.makeText(requireContext(), "Не удалось открыть пост для правки", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // editPostApi.parseForm() fills ONLY message/editReason — it does NOT set the identity fields.
            // Without type=EDIT + postId the submit goes out as CODE=03 with no `p`, so IPB creates a NEW
            // post instead of editing (баг «вместо правки добавляется второе сообщение»). Populate them from
            // the tapped post + current page context, exactly like the WebView editor's arguments do.
            form.type = EditPostForm.TYPE_EDIT_POST
            form.postId = item.postId
            form.forumId = pageForumId
            form.topicId = pageTopicId
            form.st = pageSt
            mergeAttachmentIdsFromPostText(form)
            dedupeAttachmentsById(form)
            editingForm = form
            val panel = messagePanel ?: return@launch
            panel.setText(form.message)
            panel.moveCursorToEnd()
            panel.messageField.clearUndoHistory()
            messagePanelDraftMirror = form.message.orEmpty()
            // Всегда переустанавливаем список: иначе вложения предыдущего черновика/правки остаются в
            // панели и уезжают в `file-list` чужого поста.
            attachmentsPopup?.setAttachments(form.attachments)
            showMessagePanel(showKeyboard = true)
        }
    }

    override fun onDelete(item: NativePostItem) {
        // Destructive → confirm first (authorised, but irreversible).
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить сообщение?")
                .setMessage("Это действие необратимо.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить") { _, _ -> performDelete(item) }
                .showWithStyledButtons()
    }

    /**
     * The «⋮» post menu (parity with the WebView showPostMenu): reply / quote / copy-link / share /
     * author profile / report / edit / delete. Rendered as a solid MaterialAlertDialog list.
     */
    override fun onPostMenu(item: NativePostItem) {
        val postUrl = "https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=findpost&p=${item.postId}"
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Ответить" to { onReply(item) })
            add("Цитировать" to { onQuote(item) })
            if (item.canQuote) add("Цитировать из буфера" to { quoteFromBuffer(item) })
            add("Копировать ссылку" to {
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("post", postUrl))
                Toast.makeText(requireContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            })
            add("Поделиться ссылкой" to {
                runCatching {
                    startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, postUrl)
                            }, null))
                }
            })
            // «Профиль автора» intentionally omitted — that action is reached by tapping the avatar
            // (onAvatarClick), so it would be a redundant row here.
            if (item.canReport) add("Пожаловаться" to { tryReportPost(item) })
            add("Создать закладку" to { createNoteForPost(item) })
            if (item.canEdit) add("Изменить" to { onEdit(item) })
            if (item.canDelete) add("Удалить" to { onDelete(item) })
        }
        // No title header — the user asked for action rows only (the nick added visual noise).
        showM3Menu(title = null, actions = actions)
    }

    /**
     * «Пожаловаться» — in-app report flow (parity with the old WebView ThemeDialogsHelper.tryReportPost).
     * Previously this navigated to `act=report` via [linkHandler], which fell through to the external
     * browser and the site form (user report «открывается браузер вместо формы жалобы»). Show the one-time
     * warning (gated by the report-warning preference), then the reason dialog, then POST in-app.
     */
    private fun tryReportPost(item: NativePostItem) {
        if (otherPreferencesHolder.getShowReportWarningSync()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.attention)
                    .setMessage(R.string.report_warning)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            otherPreferencesHolder.setShowReportWarning(false)
                        }
                        showReportDialog(item)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
        } else {
            showReportDialog(item)
        }
    }

    private fun showReportDialog(item: NativePostItem) {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        val binding = forpdateam.ru.forpda.databinding.ReportLayoutBinding
                .inflate(android.view.LayoutInflater.from(builder.context))
        builder
                .setTitle(String.format(getString(R.string.report_to_post_Nick), item.nick.orEmpty()))
                .setView(binding.root)
                .setPositiveButton(R.string.send) { _, _ ->
                    performReport(item, binding.reportTextField.text?.toString().orEmpty())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun performReport(item: NativePostItem, message: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.reportPost(pageTopicId, item.postId, message) }
            }
            if (view == null) return@launch
            result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(),
                                getString(R.string.report_post_success), Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(),
                                e.message ?: "Не удалось отправить жалобу", Toast.LENGTH_SHORT).show()
                    })
        }
    }

    /** «Цитировать из буфера»: wrap the current clipboard text in a quote from [item] (parity with the
     *  WebView quoteFromBuffer). */
    private fun quoteFromBuffer(item: NativePostItem) {
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(requireContext())
                ?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            return
        }
        onQuoteSelection(item, text)
    }

    /** «Создать закладку»: open the note-create dialog pre-filled with this post's title/link (parity with
     *  the WebView createNote). */
    private fun createNoteForPost(item: NativePostItem) {
        val themeTitle = arguments?.getString(TabFragment.ARG_TITLE).orEmpty()
        val title = "пост $themeTitle ${item.nick.orEmpty()} ${item.postId}"
        val url = "https://4pda.to/forum/index.php?s=&showtopic=${item.topicId}&view=findpost&p=${item.postId}"
        forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
                .showAddNoteDialog(requireContext(), title, url, notesRepository)
    }

    /** «В закладки» из меню темы: диалог создания закладки, предзаполненный названием и ссылкой темы. */
    private fun createNoteForTopic() {
        if (pageTopicId <= 0) return
        val topicTitle = getTitle().trim().ifEmpty {
            arguments?.getString(TabFragment.ARG_TITLE).orEmpty().trim()
        }
        val url = "https://4pda.to/forum/index.php?showtopic=$pageTopicId"
        forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
                .showCreateBookmarkDialog(requireContext(), topicTitle, url, notesRepository)
    }

    /** Avatar tap → user menu (parity with the WebView showUserMenu), rendered as a clean M3 popup. */
    override fun onAvatarClick(item: NativePostItem) {
        if (item.userId <= 0) return
        val nick = item.nick.orEmpty()
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Профиль" to { navigationUseCase.openProfile(item.userId) })
            // «Репутация ±» → the same submenu the rep-badge tap opens (Увеличить / Посмотреть /
            // Уменьшить), gated by canPlusRep/canMinusRep. Reuses onReputation so the change dialog,
            // permission gating and API call stay in one place; «Посмотреть» keeps the old open-history behavior.
            add("Репутация ±" to { onReputation(item) })
            add("Личные сообщения QMS" to { navigationUseCase.openQms(item.userId) })
            add("Темы пользователя" to { navigationUseCase.openSearchUserTopics(nick, item.userId) })
            add("Сообщения в этой теме" to {
                navigationUseCase.openSearchInTopic(pageForumId, pageTopicId, nick, item.userId)
            })
            add("Сообщения пользователя" to { navigationUseCase.openSearchUserMessages(nick, item.userId) })
            // Own posts can't be blacklisted (parity with the WebView guard).
            if (item.userId != authHolder.get().userId) {
                val blacklisted = themeUseCase.isForumBlacklisted(item.userId, item.nick)
                val label = if (blacklisted) "Убрать из чёрного списка форума" else "Добавить в чёрный список форума"
                add(label to { toggleForumBlacklist(item, blacklisted) })
            }
        }
        showM3Menu(title = null, actions = actions)
    }

    /**
     * Toggle a user in the forum blacklist (parity with the WebView toggleForumBlacklist). Adding hides
     * their posts immediately from the loaded list; removing needs a reload to bring them back (they were
     * filtered out on load), so we refresh the topic.
     */
    private fun toggleForumBlacklist(item: NativePostItem, wasBlacklisted: Boolean) {
        val user = forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser(item.userId, item.nick.orEmpty())
        viewLifecycleOwner.lifecycleScope.launch {
            if (wasBlacklisted) themeUseCase.removeForumBlacklistedUser(user)
            else themeUseCase.addForumBlacklistedUser(user)
            if (view == null) return@launch
            if (wasBlacklisted) {
                Toast.makeText(requireContext(), "Убран из чёрного списка форума", Toast.LENGTH_SHORT).show()
                loadTopic(loadedUrl ?: topicUrl) // bring the un-blacklisted user's posts back
            } else {
                Toast.makeText(requireContext(), "Добавлен в чёрный список форума", Toast.LENGTH_SHORT).show()
                val removed = loadedItems.removeAll { it.userId == item.userId }
                if (removed) submitPosts()
            }
        }
    }

    /**
     * Clean Material-3 popup menu — reuses the same [DynamicDialogMenu] the WebView dialogs use, so the
     * post «⋮» menu and the avatar user menu look identical and polished (rounded surface, ripple rows,
     * M3 typography). [title] shows a TitleLarge header when non-null.
     */
    private fun showM3Menu(title: String?, actions: List<Pair<String, () -> Unit>>) {
        if (actions.isEmpty()) return
        val menu = forpdateam.ru.forpda.ui.views.DynamicDialogMenu<Unit, Unit>()
        actions.forEach { (label, action) -> menu.addItem(label) { _, _ -> action() } }
        menu.allowAll()
        val style = forpdateam.ru.forpda.ui.views.DynamicDialogMenu.Style(
                titleTextSizeSp = 18f,
                itemTextSizeSp = 16f,
                itemMinHeightDp = 48,
                contentVerticalPaddingDp = 8,
                itemVerticalPaddingDp = 8,
                titleBottomPaddingDp = 4,
        )
        menu.show(requireContext(), Unit, Unit, title, style)
    }

    override fun onReputation(item: NativePostItem) {
        if (item.userId <= 0) return
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (item.canPlusRep) options.add("Увеличить" to { showReputationChangeDialog(item, increase = true) })
        // Must route to the reputation-history screen — NOT a `showuser=…&tab=reputation` URL. LinkHandler
        // matches `showuser` first and opens the PROFILE screen (the `tab=reputation` anchor is a web-only
        // hint it ignores), so the old URL silently landed on the profile. Use the in-app rep-history nav
        // (same call as the avatar menu's «Репутация»).
        options.add("Посмотреть" to { navigationUseCase.openReputationHistory(item.userId) })
        if (item.canMinusRep) options.add("Уменьшить" to { showReputationChangeDialog(item, increase = false) })
        showM3Menu("Репутация ${item.nick.orEmpty()}", options)
    }

    /**
     * Long-press on a post's 👍/👎 icon → jump straight to the reputation-change dialog for the author
     * ([up] = raise, matching the pressed thumb). Faster than the avatar-badge → menu path. The thumbs
     * can appear via the quote-fallback even when reputation can't actually be changed (own limit reached,
     * etc.), so guard on the direction-specific flag and tell the user instead of silently no-op'ing.
     */
    override fun onReputationLongPress(item: NativePostItem, up: Boolean) {
        if (item.userId <= 0) return
        val allowed = if (up) item.canPlusRep else item.canMinusRep
        if (!allowed) {
            Toast.makeText(requireContext(),
                    "Нельзя изменить репутацию этому пользователю", Toast.LENGTH_SHORT).show()
            return
        }
        showReputationChangeDialog(item, increase = up)
    }

    private fun showReputationChangeDialog(item: NativePostItem, increase: Boolean) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply { hint = "Комментарий (необязательно)" }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle(if (increase) "Увеличить репутацию" else "Уменьшить репутацию")
                .setView(input)
                .setPositiveButton("OK") { _, _ -> performReputationChange(item, increase, input.text?.toString().orEmpty()) }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons(compact = true)
    }

    private fun performReputationChange(item: NativePostItem, increase: Boolean, message: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { reputationApi.editReputation(item.postId, item.userId, increase, message) }.getOrDefault(false)
            }
            if (view == null) return@launch
            Toast.makeText(requireContext(),
                    if (ok) "Репутация изменена" else "Не удалось изменить репутацию",
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun performDelete(item: NativePostItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { themeApi.deletePost(item.postId) }.getOrDefault(false)
            }
            if (view == null) return@launch
            if (ok) {
                Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                loadedUrl?.let { loadTopic(it) }
            } else {
                Toast.makeText(requireContext(), "Не удалось удалить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // region attachments — reuse the WebView editor's upload/delete pipeline via ThemeEditorUseCase

    /** Pre-shows placeholders for the picked files, then queues them for upload. */
    private fun uploadFiles(files: List<RequestFile>) {
        val pending = attachmentsPopup?.preUploadFiles(files) ?: emptyList()
        attachmentsPopup?.revealDuringUploadPreview()
        enqueueUpload(files, pending)
    }

    private fun enqueueUpload(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadQueue.addLast(files to pending)
        pumpUploadQueue()
    }

    /**
     * relId вложения = id правимого поста (у нового ответа поста ещё нет → 0). Без реального relId
     * сервер не находит вложение уже опубликованного поста: удаление `code=remove` отклоняется, файл
     * не открепляется — «удаляю вложение, а в посте всё равно 3 файла».
     */
    private fun attachmentRelId(): Int = editingForm?.postId ?: 0

    /** Serialise uploads (the popup shows one batch at a time), matching the WebView fragment. */
    private fun pumpUploadQueue() {
        if (uploadInProgress) return
        val next = uploadQueue.firstOrNull() ?: return
        uploadInProgress = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = editorUseCase.uploadFiles(attachmentRelId(), next.first, next.second)
            if (view != null && result is ThemeEditorUseCase.UploadResult.Success) {
                attachmentsPopup?.onUploadFiles(result.items)
            }
            uploadInProgress = false
            if (uploadQueue.isNotEmpty()) uploadQueue.removeFirst()
            pumpUploadQueue()
        }
    }

    private fun removeFiles() {
        attachmentsPopup?.preDeleteFiles()
        val selected = attachmentsPopup?.getSelected() ?: emptyList()
        if (selected.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = editorUseCase.deleteFiles(attachmentRelId(), selected)
            if (view != null && result is ThemeEditorUseCase.DeleteResult.Success) {
                attachmentsPopup?.onDeleteFiles(result.items)
            }
        }
    }

    // endregion

    /**
     * Lazily builds the bottom pagination bar «  ‹  N / M  ›  » — styled to match the WebView
     * `theme_bottom_pagination`: flat surface, bold `colorOnSurface` chevrons (NOT accent-blue),
     * no heavy divider. Tapping the label opens a page picker. CLASSIC mode only.
     */
    /**
     * Builds one «  ‹  N / M  ›  » pagination row (page-tone `colorSurfaceContainerLowest` background, bold `colorOnSurface`
     * chevrons, label opens the page picker). Shared by the bottom [ensurePaginationBar] and the top
     * [ensureTopPaginationBar] so both stay pixel-identical. Returns the row and its centre label.
     */
    private fun buildPaginationRow(): Pair<android.widget.LinearLayout, TextView> {
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            // Lowest, не Container: ряд должен сливаться с фоном страницы (flat). В статических
            // палитрах роли равны, а под Material You Container светлее Lowest — ряд читался
            // отдельной серой полосой на странице.
            // Полотно ChromeCanvas — ряд пагинации сливается с фоном страницы и под MY, и в статике.
            setBackgroundColor(ctx.chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
            elevation = 0f
            visibility = View.GONE
        }
        fun navButton(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            val pv = (8 * dm.density).toInt()
            setPadding(0, pv, 0, pv)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = ctx.obtainStyledAttributes(intArrayOf(
                    android.R.attr.selectableItemBackgroundBorderless)).let { ta ->
                // TypedArray.use{} компилится под compileSdk 36 (TypedArray:AutoCloseable),
                // но AutoCloseable у TypedArray только с API 31 → на API<31 .use{} роняет
                // ClassCastException. Ручной recycle работает на всех уровнях.
                try { ta.getDrawable(0) } finally { ta.recycle() }
            }
            setOnClickListener { onClick() }
        }
        val label = TextView(ctx).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            val pv = (8 * dm.density).toInt()
            setPadding(0, pv, 0, pv)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.45f)
            background = ctx.obtainStyledAttributes(intArrayOf(
                    android.R.attr.selectableItemBackgroundBorderless)).let { ta ->
                // TypedArray.use{} компилится под compileSdk 36 (TypedArray:AutoCloseable),
                // но AutoCloseable у TypedArray только с API 31 → на API<31 .use{} роняет
                // ClassCastException. Ручной recycle работает на всех уровнях.
                try { ta.getDrawable(0) } finally { ta.recycle() }
            }
            // Tap on «N / M» → manual page entry; long-press → a scrollable list of all pages to jump between.
            setOnClickListener { showPagePicker() }
            setOnLongClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                showPageList()
                true
            }
        }
        bar.addView(navButton("«") { jumpToPage(1) })
        bar.addView(navButton("‹") { jumpToPage(barCurrentPage - 1) })
        bar.addView(label)
        bar.addView(navButton("›") { jumpToPage(barCurrentPage + 1) })
        bar.addView(navButton("»") { jumpToPage(pagination.totalPages) })
        return bar to label
    }

    /**
     * Grey out (and disable) the «первая / предыдущая» arrows on page 1 and the «следующая / последняя»
     * arrows on the last page, so it's visually clear which direction is a dead end. Children order is
     * «(0) ‹(1) label(2) ›(3) »(4) — see [buildPaginationRow].
     */
    private fun applyPaginationArrowStates(bar: android.widget.LinearLayout?, total: Int) {
        bar ?: return
        val canGoBack = barCurrentPage > 1
        val canGoForward = barCurrentPage < total
        setPaginationArrowEnabled(bar.getChildAt(0), canGoBack)
        setPaginationArrowEnabled(bar.getChildAt(1), canGoBack)
        setPaginationArrowEnabled(bar.getChildAt(3), canGoForward)
        setPaginationArrowEnabled(bar.getChildAt(4), canGoForward)
    }

    private fun setPaginationArrowEnabled(view: View?, enabled: Boolean) {
        val arrow = view as? TextView ?: return
        arrow.isEnabled = enabled
        arrow.alpha = if (enabled) 1f else 0.3f
    }

    private fun ensurePaginationBar() {
        if (paginationBar != null) return
        // Match the POST-CARD colour so the bottom bar blends with the content above — no visible seam,
        // like the WebView's on-page bottom pagination.
        val (bar, label) = buildPaginationRow()
        // Parked below the fold from birth: [updateBottomPaginationBarOffset] slides it up as the list nears the
        // end of the page, so it must never start at translationY=0 on top of the posts.
        bar.visibility = View.GONE
        bar.translationY = 52 * resources.displayMetrics.density
        coordinatorLayout.addView(bar, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = android.view.Gravity.BOTTOM })
        paginationBar = bar
        paginationLabel = label
    }

    /**
     * «Верхняя пагинация» — the same row placed in the AppBarLayout directly BELOW the toolbar. It carries
     * the SAME scroll flags as the toolbar (kept in sync by [applyToolbarAutoHide]) so the toolbar and the
     * pagination hide / reveal together as ONE block. Gated by [Preferences.Main.TopicPaginationPanels.hasTop].
     */
    private fun ensureTopPaginationBar() {
        if (topPaginationBar != null) return
        val (bar, label) = buildPaginationRow()
        val toolbarFlags = (toolbarLayout.layoutParams as? com.google.android.material.appbar.AppBarLayout.LayoutParams)
                ?.scrollFlags ?: 0
        appBarLayout.addView(bar, com.google.android.material.appbar.AppBarLayout.LayoutParams(
                com.google.android.material.appbar.AppBarLayout.LayoutParams.MATCH_PARENT,
                com.google.android.material.appbar.AppBarLayout.LayoutParams.WRAP_CONTENT,
        ).apply { scrollFlags = toolbarFlags })
        topPaginationBar = bar
        topPaginationLabel = label
    }

    /** Show/hide the find-on-page bar; hiding clears the query and match highlights. */
    private fun toggleSearchBar() {
        val bar = ensureSearchBar()
        if (bar.visibility == View.VISIBLE) {
            closeSearch()
        } else {
            bar.visibility = View.VISIBLE
            appBarLayout.setExpanded(true, true)
            applyToolbarAutoHide() // pin the toolbar while searching
            searchInput?.requestFocus()
            searchInput?.let { showKeyboard(it) }
        }
    }

    private fun closeSearch() {
        searchBar?.visibility = View.GONE
        searchInput?.setText("")
        postsAdapter.setSearchQuery("")
        searchMatchPositions.clear()
        currentMatchIndex = -1
        hideKeyboard()
        applyToolbarAutoHide() // restore auto-hide once search is closed
    }

    /** Lazily builds the find-on-page bar: [query] «k/N» ↑ ↓ ✕, pinned just below the toolbar (top). */
    private fun ensureSearchBar(): android.widget.LinearLayout {
        searchBar?.let { return it }
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            val p = (4 * dm.density).toInt()
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        val input = android.widget.EditText(ctx).apply {
            hint = "Поиск по теме"
            textSize = 15f
            maxLines = 1
            isSingleLine = true
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = onSearchQueryChanged(s?.toString().orEmpty())
            })
        }
        val count = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val ph = (8 * dm.density).toInt()
            setPadding(ph, 0, ph, 0)
        }
        fun iconBtn(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 18f
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            val ph = (10 * dm.density).toInt()
            val pv = (6 * dm.density).toInt()
            setPadding(ph, pv, ph, pv)
            setOnClickListener { onClick() }
        }
        bar.addView(input)
        bar.addView(count)
        bar.addView(iconBtn("↑") { stepMatch(-1) })
        bar.addView(iconBtn("↓") { stepMatch(1) })
        bar.addView(iconBtn("✕") { closeSearch() })
        // Add the bar to the app bar, right below the toolbar, pinned (no scroll flags) — parity with the
        // WebView find-on-page bar, which sits at the top. Hidden (GONE) it takes no space.
        appBarLayout.addView(bar, com.google.android.material.appbar.AppBarLayout.LayoutParams(
                com.google.android.material.appbar.AppBarLayout.LayoutParams.MATCH_PARENT,
                com.google.android.material.appbar.AppBarLayout.LayoutParams.WRAP_CONTENT,
        ).apply { scrollFlags = 0 })
        searchBar = bar
        searchInput = input
        searchCountLabel = count
        return bar
    }

    private fun onSearchQueryChanged(query: String) {
        postsAdapter.setSearchQuery(query)
        searchMatchPositions.clear()
        currentMatchIndex = -1
        val q = query.trim()
        if (q.isNotBlank()) {
            val header = headerOffset()
            loadedItems.forEachIndexed { index, item ->
                if (postMatchesQuery(item, q)) searchMatchPositions.add(index + header)
            }
        }
        searchCountLabel?.text = if (q.isBlank()) "" else "${if (searchMatchPositions.isEmpty()) 0 else 1}/${searchMatchPositions.size}"
        if (searchMatchPositions.isNotEmpty()) {
            currentMatchIndex = 0
            scrollToMatch(0)
        }
    }

    /** Cycle to the previous (-1) / next (+1) matching post and scroll there. */
    private fun stepMatch(dir: Int) {
        if (searchMatchPositions.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + dir + searchMatchPositions.size) % searchMatchPositions.size
        searchCountLabel?.text = "${currentMatchIndex + 1}/${searchMatchPositions.size}"
        scrollToMatch(currentMatchIndex)
    }

    private fun scrollToMatch(matchIndex: Int) {
        val pos = searchMatchPositions.getOrNull(matchIndex) ?: return
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
    }

    /** Does any of [item]'s text (body, nested quotes/spoilers, code, attachments) contain [q]? */
    private fun postMatchesQuery(item: NativePostItem, q: String): Boolean =
            item.blocks.any { blockPlainText(it).contains(q, ignoreCase = true) }

    private fun blockPlainText(block: BodyBlock): String = when (block) {
        is BodyBlock.Text -> android.text.Html.fromHtml(block.html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        is BodyBlock.Code -> block.text
        is BodyBlock.Quote -> block.inner.joinToString(" ") { blockPlainText(it) }
        is BodyBlock.Spoiler -> block.inner.joinToString(" ") { blockPlainText(it) }
        is BodyBlock.Hidden -> block.inner.joinToString(" ") { blockPlainText(it) }
        is BodyBlock.FileAttachment -> block.name
        is BodyBlock.Table -> block.rows.joinToString(" ") { row ->
            row.joinToString(" ") { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString() }
        }
        is BodyBlock.WebFallback -> android.text.Html.fromHtml(block.html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        is BodyBlock.Image -> ""
        is BodyBlock.EditNote -> "" // system meta line — not searchable content
    }

    /**
     * Apply the topic title to the toolbar from the freshly loaded [page] (parity with the WebView, which
     * resolves the title from [ThemePage.title] on every load). Without this the title comes ONLY from
     * ARG_TITLE, so a topic opened without a title argument (deep link / mention / history / a deep page)
     * shows an empty title over the «N / M» counter. [ThemeToolbarTitlePolicy.resolveForToolbar] never
     * clears an already-visible label when deep-pagination HTML omits the title.
     */
    private fun applyToolbarTitleFromPage(page: ThemePage) {
        val current = getTitle().takeIf { it.isNotBlank() }
        val resolved = ThemeToolbarTitlePolicy.resolveForToolbar(
                page = page,
                sessionTitle = current,
                argTitle = arguments?.getString(TabFragment.ARG_TITLE),
                currentTitle = current,
        )
        if (resolved.isNotEmpty() && resolved != getTitle()) {
            setTitle(resolved)
            setTabTitle(String.format(getString(forpdateam.ru.forpda.R.string.fragment_tab_title_theme), resolved))
        }
    }

    /** Refresh the bar's «N / M» text and hide it entirely for single-page topics. */
    private fun updatePaginationBar() {
        if (!pagination.isInitialised) return
        ensurePaginationBar()
        val total = pagination.totalPages
        paginationLabel?.text = "$barCurrentPage / $total"
        applyPaginationArrowStates(paginationBar, total)
        // «Панель страниц темы» ([TopicPaginationPanels]): independent top/bottom bits. The TOP bar
        // (pinned under the toolbar) works in BOTH reading modes. The BOTTOM bar is a CLASSIC-only
        // navigator — in HYBRID (infinite scroll) poststep arrows make no sense, so the bottom bit is
        // ignored there and the page position shows via the toolbar subtitle instead («N / M»).
        val panels = mainPreferencesHolder.getTopicPaginationPanels()
        val editorOpen = messagePanel?.visibility == View.VISIBLE
        ensureTopPaginationBar()
        topPaginationLabel?.text = "$barCurrentPage / $total"
        applyPaginationArrowStates(topPaginationBar, total)
        topPaginationBar?.visibility = if (panels.hasTop && total > 1 && !editorOpen) View.VISIBLE else View.GONE
        // Top-toolbar subtitle mirrors the page position — digits only, no «Страница … из …» text
        // (parity with the WebView toolbar: «1348 / 1349»).
        setSubtitle(if (total > 1) "$barCurrentPage / $total" else null)
        // The bottom pagination bar belongs to CLASSIC reading mode only; HYBRID (default) uses continuous
        // infinite scroll with no bar. Also gone while the reply editor is open. When it IS enabled it lives
        // permanently below the end of the page and rides in with the scroll — see
        // [updateBottomPaginationBarOffset]. Bottom room for it is reserved for the whole session (chrome
        // overlap + bar height + breathing gap — see [applyListBottomPadding]), so it never covers the last
        // post and never reflows the list.
        updateBottomPaginationBarOffset()
        applyListBottomPadding()
    }

    /**
     * Bottom pagination bar = «панель страниц снизу»: it always EXISTS, parked just below the end of the page
     * exactly like the WebView's in-page panel was. It is not toggled and never animated — its position is a
     * pure function of how far the list still has to scroll, so it rides in under the finger and parks above
     * the bottom-nav chrome when the last post is fully reached (the same «follows the scroll offset» model the
     * toolbar + top pagination get for free from AppBarLayout's scroll flags).
     *
     * translationY = px still left to scroll, clamped to the bar's height: > barHeight of content below → the
     * bar sits entirely off-screen; 0 left → fully parked. No visibility flip means no state to get stuck in,
     * which is what made a very slow scroll sometimes never reveal it, and no animator means no lag.
     *
     * The list's bottom padding permanently reserves the bar's height ([classicPaginationBarPadPx]) plus the
     * chrome overlap and the breathing gap, so the bar can never cover the last post at ANY offset — the post's
     * bottom edge stays above the bar's top edge the whole way in.
     */
    private fun updateBottomPaginationBarOffset() {
        val bar = paginationBar ?: return
        val enabled = pagination.isInitialised && isClassicMode() && pagination.totalPages > 1 &&
                messagePanel?.visibility != View.VISIBLE &&
                mainPreferencesHolder.getTopicPaginationPanels().hasBottom &&
                loadedItems.isNotEmpty()
        if (!enabled) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        val height = bar.height
        if (height <= 0) {
            // Not measured yet (first layout pass) — park it off-screen on a guess and re-run once it has a
            // height, otherwise it would flash at translationY=0 over the posts.
            bar.translationY = 52 * resources.displayMetrics.density
            bar.post { if (view != null) updateBottomPaginationBarOffset() }
            return
        }
        bar.translationY = distanceToListBottomPx().coerceIn(0, height).toFloat()
    }

    /**
     * Pixels the list still has to travel before the last post rests at the bottom of the padded viewport.
     *
     * Geometry, not [RecyclerView.canScrollVertically]: with variable-height posts the scroll range is an
     * ESTIMATE that can still claim headroom at the true end (that is why the bar sometimes refused to show
     * on a slow scroll). When the last item isn't even bound we are far from the end — report a distance big
     * enough to keep the bar parked off-screen.
     */
    private fun distanceToListBottomPx(): Int {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return Int.MAX_VALUE
        val count = recyclerView.adapter?.itemCount ?: 0
        if (count == 0) return Int.MAX_VALUE
        if (lm.findLastVisibleItemPosition() != count - 1) return Int.MAX_VALUE
        val lastView = lm.findViewByPosition(count - 1) ?: return Int.MAX_VALUE
        val viewportBottom = recyclerView.height - recyclerView.paddingBottom
        return (lastView.bottom - viewportBottom).coerceAtLeast(0)
    }

    /**
     * Верхний аналог [distanceToListBottomPx]: пиксели до истинного верха списка по геометрии первого
     * элемента, а не по оценочному [RecyclerView.computeVerticalScrollOffset] (который у самого верха может
     * не дотянуть до 0 при постах переменной высоты). Пока первый элемент окна не первый видимый — мы далеко
     * от верха, возвращаем большое расстояние, чтобы upward-подгрузка не взводилась. hasPrevPage() гейтит
     * вызов на страницы > 1, где элемент 0 — это первый пост (poll-шапки на них нет).
     */
    private fun distanceToListTopPx(): Int {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return Int.MAX_VALUE
        if ((recyclerView.adapter?.itemCount ?: 0) == 0) return Int.MAX_VALUE
        if (lm.findFirstVisibleItemPosition() != 0) return Int.MAX_VALUE
        val firstView = lm.findViewByPosition(0) ?: return Int.MAX_VALUE
        return (recyclerView.paddingTop - firstView.top).coerceAtLeast(0)
    }

    /**
     * MainActivity reports the bottom-nav chrome height (tab bar + system nav) here. We re-derive the list
     * bottom padding from the MEASURED on-screen overlap with the chrome ([applyListBottomPadding]) rather
     * than adding this value blindly — that was wrong on both host kinds (double-count → huge gap when the
     * container is inset; under-count → last post cut behind the tab bar when it draws edge-to-edge).
     */
    override fun onBottomChromePaddingChanged(padding: Int) {
        if (bottomNavChromePad == padding) return
        bottomNavChromePad = padding
        if (view == null) return
        if (pagination.isInitialised) updatePaginationBar() else applyListBottomPadding()
        if (anchoredBottomPostId != null) recyclerView.post { reanchorBottomAfterGrowth() }
    }

    /**
     * «Current page» for the bar = the page whose posts occupy the MOST pixels of the viewport right now.
     * Each item carries its authoritative [NativePostItem.pageNumber] tag, so we sum visible height per page
     * and pick the dominant one. This beats index arithmetic (`firstLoadedPage + index/perPage`), which drifts
     * when a page has an odd post count (hat / «Добавлено» merges / deletions), and beats plain first-visible,
     * which would keep showing page N while only the footer of its last post lingers at the very top even
     * though page N+1 already fills the screen (the reported «71 / 72 но читаю 72» case).
     */
    private fun updateBarCurrentPageFromScroll() {
        if (!pagination.isInitialised || loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val viewTop = recyclerView.paddingTop
        val viewBottom = recyclerView.height - recyclerView.paddingBottom
        val pagePixels = HashMap<Int, Int>()
        for (pos in first..last) {
            val v = lm.findViewByPosition(pos) ?: continue
            val page = loadedItems.getOrNull(pos - headerOffset())?.pageNumber ?: continue
            if (page <= 0) continue
            val visible = (minOf(v.bottom, viewBottom) - maxOf(v.top, viewTop)).coerceAtLeast(0)
            if (visible > 0) pagePixels[page] = (pagePixels[page] ?: 0) + visible
        }
        val page = (pagePixels.maxByOrNull { it.value }?.key ?: return).coerceIn(1, pagination.totalPages)
        if (page != barCurrentPage) {
            barCurrentPage = page
            updatePaginationBar()
        }
    }


    private fun jumpToPage(pageNumber: Int) {
        if (!pagination.isInitialised) return
        val target = pageNumber.coerceIn(1, pagination.totalPages)
        if (target == barCurrentPage && loadedItems.isNotEmpty()) return
        pendingJumpToTop = true
        barCurrentPage = target
        loadTopic(pagination.pageUrl(target))
    }

    /**
     * FAB long-press → the exact WebView smart-navigation popup ([SmartNavigationMenu]) anchored to the
     * FAB: a page wheel («Текущая» highlighted) plus «В начало темы» / «К непрочитанному» / «В конец темы»
     * / «Ввести номер». On a single-page topic it's meaningless, so nothing shows.
     */
    private fun showSmartNavMenu() {
        if (!pagination.isInitialised || pagination.totalPages <= 1) return
        val menu = smartNavMenu ?: forpdateam.ru.forpda.ui.views.SmartNavigationMenu(
                requireContext(), fab, coordinatorLayout).also {
            it.setListener(object : forpdateam.ru.forpda.ui.views.SmartNavigationMenu.Listener {
                override fun onGoToPage(page: Int) = jumpToPage(page)
                override fun onGoToStart() = jumpToPage(1)
                override fun onGoToEnd() = jumpToLastPage()
                override fun onGoToUnread() {
                    pendingJumpToTop = false
                    loadTopic(unreadUrl())
                }
                override fun onDismiss() {}
            })
            smartNavMenu = it
        }
        menu.show(barCurrentPage, pagination.totalPages, hasUnread = topicHasUnread)
    }

    /** «К непрочитанному»: 4pda's `view=getnewpost` redirect lands on the first unread post. */
    private fun unreadUrl(): String =
            if (pageTopicId > 0) "https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=getnewpost"
            else (loadedUrl ?: topicUrl)

    /** «В конец темы»: load the last page (or just scroll down if already there) and land on the last post. */
    private fun jumpToLastPage() {
        if (!pagination.isInitialised) return
        val last = pagination.totalPages
        if (last == barCurrentPage && loadedItems.isNotEmpty()) {
            val lastPos = (loadedItems.size - 1 + headerOffset()).coerceAtLeast(0)
            recyclerView.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(lastPos, 0)
            }
            return
        }
        pendingJumpToBottom = true
        barCurrentPage = last
        loadTopic(pagination.pageUrl(last))
    }

    /**
     * Long-press on the «N / M» pagination counter → a scrollable radio list of every page, current one
     * pre-selected and scrolled into view. Picking a page jumps to it. Complements [showPagePicker] (the
     * single-tap manual number entry).
     */
    private fun showPageList() {
        if (!pagination.isInitialised || pagination.totalPages <= 1) return
        val ctx = requireContext()
        val total = pagination.totalPages
        val items = Array(total) { "Страница ${it + 1}" }
        val current = (barCurrentPage - 1).coerceIn(0, total - 1)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Страницы")
                .setSingleChoiceItems(items, current) { dialog, which ->
                    dialog.dismiss()
                    jumpToPage(which + 1)
                }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons()
    }

    private fun showPagePicker() {
        if (!pagination.isInitialised || pagination.totalPages <= 1) return
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 – ${pagination.totalPages}"
            setText(barCurrentPage.toString())
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Перейти на страницу")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    input.text?.toString()?.trim()?.toIntOrNull()?.let { jumpToPage(it) }
                }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons()
    }

    /** Resolve the field text, falling back to the mirror if the CodeEditor lost it to view churn. */
    private fun resolveMessagePanelDraft(): String {
        val field = messagePanel?.message.orEmpty()
        return if (field.isNotEmpty()) field else messagePanelDraftMirror
    }

    private fun topicDraftKey(): String? =
            if (pageTopicId > 0) forpdateam.ru.forpda.model.repository.draft.PostDraftRepository.topicKey(pageTopicId) else null

    /** Дебаунс-сохранение черновика нового ответа (ключ общий с полноэкранным редактором). */
    private fun persistTopicDraft(text: String) {
        val key = topicDraftKey() ?: return
        topicDraftSaveJob?.cancel()
        topicDraftSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(600)
            runCatching { postDraftRepository.save(key, text.trim(), System.currentTimeMillis()) }
        }
    }

    /** Снять персистентный черновик темы (после успешной отправки нового ответа). */
    private fun clearTopicDraft() {
        topicDraftSaveJob?.cancel()
        topicDraftKey()?.let { postDraftRepository.clearFireAndForget(it) }
    }

    /**
     * Восстановление черновика нового ответа при открытии пустого редактора (после перезапуска
     * приложения / нового открытия темы). Загружаем асинхронно и вставляем только если поле всё ещё
     * пустое и пользователь не начал править существующий пост.
     */
    private fun restoreTopicDraftIntoPanel() {
        val key = topicDraftKey() ?: return
        val panel = messagePanel ?: return
        if (editingForm != null) return
        if (resolveMessagePanelDraft().isNotBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = runCatching { postDraftRepository.load(key) }.getOrNull().orEmpty()
            if (saved.isEmpty()) return@launch
            if (view == null || editingForm != null) return@launch
            if (resolveMessagePanelDraft().isNotBlank()) return@launch
            panel.setText(saved)
            panel.moveCursorToEnd()
            panel.messageField.clearUndoHistory()
            messagePanelDraftMirror = saved
        }
    }

    /**
     * The editor «крестик» (clear-all) wipes the whole draft — an easy accidental tap. Confirm first so a
     * misfire doesn't lose a long message. Nothing to confirm on an empty field → just clear silently.
     */
    private fun confirmClearMessage() {
        val panel = messagePanel ?: return
        if (resolveMessagePanelDraft().isBlank() && panel.attachments.isEmpty()) {
            panel.clearMessage(); messagePanelDraftMirror = ""
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage("Очистить весь набранный текст?")
                .setPositiveButton("Очистить") { _, _ ->
                    messagePanel?.clearMessage()
                    messagePanelDraftMirror = ""
                }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons()
    }

    private fun sendMessage() {
        val panel = messagePanel ?: return
        val message = resolveMessagePanelDraft().trim()
        val attachments = panel.attachments.toMutableList()
        if ((message.isBlank() && attachments.isEmpty()) || isSending || pageTopicId <= 0) return
        isSending = true
        // A brand-new reply lands at the END of the topic; an edit re-anchors on the edited post.
        val isNewReply = editingForm == null
        val editedPostId = editingForm?.postId ?: 0
        hideKeyboard()
        panel.setProgressState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            // Editing an existing post reuses its loaded form (type=EDIT, postId set); a new reply
            // builds a fresh NEW_POST form from the current topic context. Attachments ride along.
            val form = editingForm?.apply { this.message = message }
                    ?: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm().apply {
                        type = forpdateam.ru.forpda.entity.remote.editpost.EditPostForm.TYPE_NEW_POST
                        forumId = pageForumId
                        topicId = pageTopicId
                        st = pageSt
                        this.message = message
                    }
            form.attachments.clear()
            form.attachments.addAll(attachments)
            val result = withContext(Dispatchers.IO) { runCatching { editPostApi.sendPost(form) } }
            isSending = false
            if (view == null) return@launch
            panel.setProgressState(false)
            result.onSuccess { postedPage ->
                panel.clearMessage()
                panel.clearAttachments()
                messagePanelDraftMirror = ""
                if (isNewReply) clearTopicDraft()
                editingForm = null
                hideMessagePanel()
                Toast.makeText(requireContext(), "Отправлено", Toast.LENGTH_SHORT).show()
                // sendPost уже вернул распарсенную страницу со свежим/правленым постом — применяем её к
                // списку НА МЕСТЕ (append/patch + плавный скролл + подсветка). Полный reload здесь гасил
                // весь экран: очистка списка → спиннер → last-page fill прятал список → мигание (репорт
                // «экран потухает, мигает, потом появляется мой пост»).
                val appliedInPlace = if (isNewReply) {
                    applyPostedReplyInPlace(postedPage)
                } else {
                    applyEditedPostInPlace(postedPage, editedPostId)
                }
                // Фолбэк — прежний полный reload. Новый ответ уходит в конец темы — грузим последнюю
                // страницу и садимся на свежий пост (getlastpost → pendingJumpToBottom). Правка —
                // перезагружаемся findpost'ом НА отредактированный пост: обычный reload loadedUrl сел бы на
                // серверный якорь = ПЕРВЫЙ пост страницы (баг «якорь слетает на 1 пост последней страницы»).
                if (!appliedInPlace) {
                    if (isNewReply && pageTopicId > 0) {
                        loadTopic("https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=getlastpost")
                    } else if (editedPostId > 0) {
                        loadTopic(buildRestoreUrl(editedPostId))
                    } else {
                        loadedUrl?.let { loadTopic(it) }
                    }
                }
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Ошибка отправки: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Применить страницу, которую вернул sendPost для НОВОГО ответа, к уже загруженному списку на месте:
     * дописать новые посты вниз (свой + чужие, успевшие появиться), плавно доскроллить к своему посту и
     * подсветить его — без полной перезагрузки, то есть без пустого экрана и миганий.
     *
     * In-place возможен только когда низ загруженного окна — страница поста или соседняя над ней
     * (обычный ответ с конца темы). При переносе поста на СЛЕДУЮЩУЮ страницу старая последняя могла
     * добрать чужие посты до лимита — их хвост дотягивается отдельным GET, иначе в окне осталась бы дыра.
     * Любой нетипичный контекст (окно далеко от конца, классика с переносом страницы, гонка с другой
     * загрузкой, свой пост не нашёлся) → false, вызывающий делает прежний полный reload.
     */
    private suspend fun applyPostedReplyInPlace(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ): Boolean {
        if (view == null || !pagination.isInitialised) return false
        if (page.id <= 0 || page.id != pageTopicId) return false
        if (isLoadingNextPage || isLoadingPrevPage || fillingLastPage) return false
        val postedPageNumber = page.pagination.current.coerceAtLeast(1)
        val bottomLoaded = pagination.loadedPage
        if (postedPageNumber < bottomLoaded || postedPageNumber > bottomLoaded + 1) return false
        // Классический режим показывает ОДНУ страницу: пост, перенесённый на новую, требует настоящего
        // перехода на неё.
        if (postedPageNumber == bottomLoaded + 1 && isClassicMode()) return false
        val epoch = loadEpoch
        isLoadingNextPage = true // сериализация с infinite scroll'ом (maybeLoad*/maybeFillLastPage)
        try {
            if (postedPageNumber == bottomLoaded + 1) {
                val gap = withContext(Dispatchers.IO) {
                    runCatching { themeApi.getTheme(pagination.pageUrl(bottomLoaded), hatOpen = false, pollOpen = false) }
                }.getOrElse { return false }
                if (view == null || epoch != loadEpoch) return false
                if (gap.id != pageTopicId || gap.pagination.current != bottomLoaded) return false
                processHatForPage(gap)
                recordMaxLoaded(gap)
                val gapItems = pagination.registerAndFilterNew(
                        filterBlacklisted(tagPage(mapper.map(gap.posts), gap.pagination.current)))
                if (gapItems.isNotEmpty()) {
                    loadedItems.addAll(gapItems)
                    enrichLoadedPage(gap)
                }
            }
            if (epoch != loadEpoch) return false
            processHatForPage(page) // срезать эхо шапки, иначе оно «допишется» вниз как новый пост
            recordMaxLoaded(page) // свой ответ мог создать/углубить последнюю страницу
            val newItems = pagination.registerAndFilterNew(
                    filterBlacklisted(tagPage(mapper.map(page.posts), postedPageNumber)))
            pagination.onPageAppended(postedPageNumber, page.pagination)
            if (newItems.isEmpty()) return false // свой пост не нашли — пусть отработает полный reload
            loadedItems.addAll(newItems)
            markedTopicReadAtEnd = false // низ темы вырос — «дочитал до конца» должен сработать заново
            // Отправка ответа = ОДНОЗНАЧНОЕ «дочитал до конца» (свой пост стал последним). Этот in-place
            // путь не проходит через renderThemePage, поэтому наследует stale suppress-гейт от сессии ДО
            // отправки (открыл на первом непрочитанном и НЕ скроллил перед набором текста → suppress ещё
            // взведён). Снимаем его вручную, иначе maybeMarkTopicReadAtEnd в scrollToPostedReply/onPause
            // заблокируется и тема останется «непрочитанной», пока не переоткрыть (репорт: «отправил пост,
            // вышел — тема непрочитана, хотя мой пост последний»). userScrollGestureThisSession тоже
            // взводим — фиксация границы прочитанного в onPause питается этим флагом.
            suppressEndMarkReadUntilUserScroll = false
            userScrollGestureThisSession = true
            updatePaginationBar()
            // Якорь sendPost'а (id своего поста) надёжнее последнего элемента: параллельный чужой ответ
            // мог успеть встать НИЖЕ нашего.
            val targetId = page.anchorPostId?.removePrefix("entry")?.trim()?.toIntOrNull()
                    ?.takeIf { id -> newItems.any { it.postId == id } }
                    ?: newItems.last().postId
            submitPosts {
                if (view != null) scrollToPostedReply(targetId)
            }
            return true
        } finally {
            isLoadingNextPage = false
        }
    }

    /**
     * Плавно подъехать к свежеотправленному посту [postId] и подсветить его. Пост рядом (обычный ответ с
     * конца темы) — smooth scroll, чтобы появление читалось как непрерывное движение; успели отскроллить
     * далеко вверх — мгновенный прыжок [anchorPost]'ом, как при обычном открытии. Пара отложенных
     * ре-якорей — та же, что у getlastpost-открытия: viewport ещё оседает после скрытия клавиатуры/панели,
     * и ранний замер мог бы оставить пост под нижней кромкой.
     */
    private fun scrollToPostedReply(postId: Int) {
        val idx = loadedItems.indexOfFirst { it.postId == postId }
        if (idx < 0) return
        val concatPos = idx + headerOffset()
        val isLast = idx == loadedItems.size - 1
        postsAdapter.requestHighlight(postId)
        if (isLast) {
            anchoredBottomPostId = postId // держать пост у нижнего края сквозь дорастание списка
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) android.util.Log.i("FPDA_CLEAR", "anchor SET(postedReply)=$postId")
        }
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val lastVisible = lm?.findLastVisibleItemPosition()
                ?: androidx.recyclerview.widget.RecyclerView.NO_POSITION
        val near = lastVisible != androidx.recyclerview.widget.RecyclerView.NO_POSITION &&
                concatPos - lastVisible <= POSTED_SMOOTH_SCROLL_MAX_ITEMS
        if (near) recyclerView.smoothScrollToPosition(concatPos) else anchorPost(concatPos, isLast)
        recyclerView.post { markVisiblePostsRead() }
        // Ре-якорить только в покое: scrollToPosition внутри reanchor'а оборвал бы ещё идущий smooth
        // scroll рывком (и «не видя» последний пост, ошибочно снял бы нижний якорь).
        recyclerView.postDelayed({
            if (view != null && recyclerView.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                reanchorBottomAfterGrowth()
            }
        }, 400)
        recyclerView.postDelayed({
            if (view != null) {
                if (recyclerView.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    reanchorBottomAfterGrowth()
                }
                markVisiblePostsRead()
                maybeMarkTopicReadAtEnd()
            }
        }, 800)
    }

    /**
     * Применить страницу после РЕДАКТИРОВАНИЯ к списку на месте: заменить содержимое совпавших постов по
     * id — DiffUtil перебиндит только изменившиеся карточки, без полного reload'а с пустым экраном. После
     * подмены выравниваем отредактированный пост по ВЕРХУ, чтобы он был полностью виден (тот же [anchorPost]
     * с topAlign, что у deep-link/quote-открытия): правка меняет высоту карточки, и без якоря пост мог бы
     * оказаться частично за кромкой. false — поста нет в загруженном окне (не должно случаться при правке из
     * контекст-меню), нужен фолбэк-reload.
     */
    private fun applyEditedPostInPlace(
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
            editedPostId: Int,
    ): Boolean {
        if (view == null || !pagination.isInitialised) return false
        if (editedPostId <= 0 || page.id <= 0 || page.id != pageTopicId) return false
        if (loadedItems.none { it.postId == editedPostId }) return false
        val freshById = mapper.map(page.posts).associateBy { it.postId }
        if (!freshById.containsKey(editedPostId)) return false
        var changed = false
        for (i in loadedItems.indices) {
            val existing = loadedItems[i]
            val fresh = freshById[existing.postId] ?: continue
            // Свежая маппа не знает страницы (pageNumber=0) — сохраняем тег, иначе слетят «Страница N».
            val updated = fresh.copy(pageNumber = existing.pageNumber)
            if (updated != existing) {
                loadedItems[i] = updated
                changed = true
            }
        }
        postsAdapter.requestHighlight(editedPostId) // короткая вспышка = «сохранилось», parity с reload-путём
        // Выровнять отредактированный пост по верху ПОСЛЕ того, как DiffUtil перебиндит карточку с новой
        // высотой (commit-колбэк submitPosts), иначе якорь замерил бы старую геометрию.
        submitPosts { anchorEditedPost(editedPostId) }
        // Панель редактора только что свёрнута (hideMessagePanel) — viewport ещё дорастает; ранний замер мог
        // бы оставить пост под кромкой. Отложенный повтор в покое, как ре-якори reply-пути.
        recyclerView.postDelayed({
            if (view != null &&
                    recyclerView.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                anchorEditedPost(editedPostId)
            }
        }, 400)
        return true
    }

    /** Выровнять отредактированный пост [editedPostId] по верху, полностью в видимости (см. [anchorPost]). */
    private fun anchorEditedPost(editedPostId: Int) {
        if (view == null) return
        val idx = loadedItems.indexOfFirst { it.postId == editedPostId }
        if (idx < 0) return
        val isLast = idx == loadedItems.size - 1
        anchorPost(idx + headerOffset(), isLast, topAlign = true)
    }

    // endregion

    /**
     * Добавляет уникальный neutral-параметр (`_cb=<nanos>`), заставляющий CDN 4PDA сходить на origin за
     * СВЕЖЕЙ страницей темы (edge-кэш отдаёт до ~80 мин staleness — проверено по `age` в ответе). Пишется
     * ТОЛЬКО в fetch-URL, не в [lastRequestedUrl]/[loadedUrl], чтобы не ломать эхо-дедуп ([loadInFlight]) и
     * якорную логику. Имя `_cb` нейтрально (не `s=` — это IPB-session); 4PDA роняет его при 302-редиректе.
     * Фрагмент URL (`#entry…`), если есть, сохраняется ПОСЛЕ query-параметра.
     */
    private fun topicFetchUrlWithCacheBuster(url: String): String {
        if (url.isBlank()) return url
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val frag = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val sep = if (base.contains('?')) '&' else '?'
        return "$base${sep}_cb=${System.nanoTime()}$frag"
    }

    private fun loadTopic(url: String, preserveRefreshIntent: Boolean = false) {
        if (url.isBlank()) {
            setRefreshing(false)
            return
        }
        if (!preserveRefreshIntent) {
            // Любая загрузка, кроме обновления снизу и его шага на следующую страницу, гасит
            // refresh-намерение. Иначе взведённые флаги пережили бы её и применились к чужому рендеру:
            // явный переход («страница N», «В конец темы») получил бы якорь «первый непрочитанный», а
            // смена темы в этом же табе — ещё и шаг по страницам ЧУЖОЙ темы (id постов глобальны,
            // сравнение с seenUpTo другой темы бессмысленно).
            pendingRefreshSeenUpToPostId = 0
            refreshFollowNextPageArmed = false
        }
        // Remember the requested target so the navigator's redundant echo of the initial open (see
        // loadThemeUrlFromNavigator) doesn't fire a second, page-1 load in parallel. The echo-dedup is
        // valid only WHILE this load is in flight ([loadInFlight]) — an equal URL minutes later is a
        // genuine fresh re-open and must reload (см. дедуп в loadThemeUrlFromNavigator).
        lastRequestedUrl = url
        loadInFlight = true
        // «view=getlastpost» = «открыть в конец темы» (read topic, «Первое непрочитанное» setting). Its
        // server redirect anchors on the last-READ post, which on an already-read topic with newer posts
        // is a MIDDLE post — felt like landing on a random post. Force the landing to the true bottom of
        // the loaded last page instead (same as «В конец темы»).
        if (url.contains("getlastpost", ignoreCase = true)) pendingJumpToBottom = true
        // Loading indicator (parity with the WebView engine): the FIRST open shows the centered Material 3
        // LoadingIndicator BELOW the toolbar (via ContentController → content_progress), NOT the swipe
        // spinner that overlapped the toolbar. Subsequent reloads/refreshes fall back to the swipe spinner.
        setRefreshing(true)
        isLoadingNextPage = false
        isLoadingPrevPage = false
        // Invalidate in-flight loads (this one included, if another loadTopic overtakes it): their results
        // belong to the previous topic/page-set and must not touch the freshly reset list.
        val epoch = ++loadEpoch
        // Defensive: never leave the list hidden if a previous last-page fill was interrupted mid-flight.
        fillingLastPage = false
        recyclerView.alpha = 1f
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // Cache-buster ТОЛЬКО на сетевой fetch (не на url для дедупа/loadedUrl): CDN 4PDA отдаёт
                // страницу темы из edge-кэша до ~80 мин давности (age в ответе), из-за чего свежий пост не
                // приходит при открытии, пока кэш не протухнет — «тема со старым постом, помогает только
                // ручной рефреш». WebView-путь обходил это (&s=), натив потерял. Уникальный параметр гонит
                // CDN на origin; 4PDA его игнорирует и роняет при 302 (getnewpost/getlastpost/findpost),
                // так что page.url остаётся чистым. Только для главной загрузки (открытие/рефреш/переход/
                // резюм) — infinite-scroll старых страниц остаётся на CDN.
                runCatching { themeApi.getTheme(topicFetchUrlWithCacheBuster(url), hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            // Latest-wins: a newer loadTopic (refresh, page jump, tab reuse for another topic via
            // loadThemeUrlFromNavigator) may have superseded this request while it was in flight.
            // Rendering the stale result would wipe the fresh content (pagination.reset + loadedItems
            // rebuild), so drop it. The superseding load owns the refresh indicator too — don't touch it.
            // The epoch check subsumes comparing url against lastRequestedUrl (both are stamped only
            // here) and also catches a superseding reload of the SAME url. The nested reload
            // (maybeResumeToReadBoundary) starts FROM onSuccess after this guard and bumps the epoch
            // itself, so it stays the latest.
            if (epoch != loadEpoch) return@launch
            // Этот запрос — актуальный и завершился (успехом или ошибкой): полёт окончен. Вложенные
            // перезагрузки (boundary-резюм, шаг на след. страницу) сами взведут флаг заново в loadTopic.
            loadInFlight = false
            result.onSuccess { page ->
                // Findpost-резюм к границе прочитанного вернул ПУСТУЮ страницу (пост границы удалён из темы,
                // 4PDA не отдаёт целевой пост) ИЛИ страницу ЧУЖОЙ темы (пост границы ПЕРЕНЕСЁН модераторами
                // в другую тему — IPB у findpost игнорирует showtopic и редиректит туда, где пост живёт
                // сейчас; лог 13_07: открытие «OnePlus 15 Обсуждение» рендерило «Энергосбережение» с
                // перенесёнными постами — «темы слились»). В обоих случаях не уводим юзера из запрошенной
                // темы — рендерим уже успешно загруженную getnewpost-страницу. См. [resumeFallbackPage].
                resumeFallbackPage?.let { fb ->
                    val crossTopic = fb.id > 0 && page.id > 0 && page.id != fb.id
                    if (page.posts.isEmpty() || crossTopic) {
                        val fbUrl = resumeFallbackUrl ?: url
                        resumeFallbackPage = null
                        resumeFallbackUrl = null
                        pendingSuppressEndMarkReadForResume = false
                        pendingSilentResumeLanding = false // fallback рендерится на серверном якоре, не «тихий резюм»
                        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                            android.util.Log.i("FPDA_READ_BOUNDARY",
                                    if (crossTopic) "resume_findpost redirected to foreign topic ${page.id} (boundary post moved) → fallback to ${fb.id}"
                                    else "resume_findpost empty (deleted boundary post) → fallback to loaded page")
                        }
                        renderThemePage(fbUrl, fb)
                        return@onSuccess
                    }
                }
                // Клиентская граница прочитанного: на ПЕРВОМ открытии, если серверный якорь сел бы НИЖЕ
                // самого дальнего реально-виденного поста, перезагрузиться findpost'ом на границу (иначе
                // проскочим непрочитанное — walk-down 4PDA). Фаер один раз за открытие; findpost-резюм не
                // рендерим здесь — return, дальше отработает вложенная загрузка.
                if (maybeResumeToReadBoundary(url, page)) return@onSuccess // keep the indicator; the nested findpost reload owns it
                // Дошли до реального рендера этой загрузки — fallback резюма (если взводился) больше не нужен.
                resumeFallbackPage = null
                resumeFallbackUrl = null
                renderThemePage(url, page)
            }.onFailure { error ->
                // Findpost-резюм к границе прочитанного упал исключением (пост границы удалён — «Пост #… не
                // найден») — вместо пустого экрана с тостом рендерим уже успешно загруженную getnewpost-
                // страницу. Тема открывается; максимум теряется точный якорь на непрочитанное.
                resumeFallbackPage?.let { fb ->
                    val fbUrl = resumeFallbackUrl ?: url
                    resumeFallbackPage = null
                    resumeFallbackUrl = null
                    pendingSuppressEndMarkReadForResume = false
                    pendingSilentResumeLanding = false // fallback рендерится на серверном якоре, не «тихий резюм»
                    if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                        android.util.Log.i("FPDA_READ_BOUNDARY",
                                "resume_findpost failed (${error.message}) → fallback to loaded page")
                    }
                    renderThemePage(fbUrl, fb)
                    return@onFailure
                }
                setRefreshing(false)
                pendingRefreshSeenUpToPostId = 0 // не переживать неудачную загрузку — иначе стухнет
                refreshFollowNextPageArmed = false
                pendingSuppressEndMarkReadForResume = false // мостик резюма не должен утечь в чужую загрузку
                pendingSilentResumeLanding = false
                Toast.makeText(requireContext(), "Ошибка загрузки темы: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Рендер успешно загруженной страницы темы в список (общий путь для обычной загрузки и для fallback
     * findpost-резюма, если тот упал на удалённом посту границы — см. [resumeFallbackPage]). Выделен из
     * [loadTopic].onSuccess, чтобы fallback мог переиспользовать ту же логику. НЕ вызывает
     * [maybeResumeToReadBoundary] — резюм взводится только из [loadTopic] для первичной загрузки.
     */
    private fun renderThemePage(
            url: String,
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ) {
        // Вкладка «История»: нативный рендер грузит темы напрямую через themeApi (loadTopic), минуя
        // ThemeRepository.getTheme, где раньше жила единственная запись посещения — после перехода на
        // натив «История» перестала запоминать переходы. Пишем визит здесь, на рендере первой видимой
        // страницы. Upsert по id темы: infinite-scroll старых/новых страниц идёт мимо renderThemePage,
        // а повторный заход/рефреш просто поднимает дату.
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { themeUseCase.recordThemeVisit(url, page) }
        }
        loadedUrl = url
        pageForumId = page.forumId
        pageTopicId = page.id
        // pageTopicId становится известен только после загрузки — onResume мог пройти раньше
        // с нулём. Обновляем метку «тема на экране» здесь, если вкладка сейчас видима.
        if (isResumed && pageTopicId > 0) eventsRepository.setViewedTopic(pageTopicId)
        pageSt = page.st
        pageIsInFavorite = page.isInFavorite
        pageFavId = page.favId
        recordMaxLoaded(page) // трек «докуда это устройство грузило» для кросс-девайс детекта
        setRefreshing(false)
        // Fresh (re)load of the topic — forget the previous session's topic-level hat/poll state.
        knownHatPostId = null
        toolbarHatItem = null
        currentPoll = null
        cachedPollTopicId = null
        // The poll's HTML lives on page 1 only. Show it inline only there, but CACHE it at topic
        // level (currentPoll) so the «Опрос» toolbar button persists across pages once page 1 has
        // been seen — matching the WebView, where the poll button is a topic-level toggle.
        val inlinePoll = if (page.pagination.current <= 1) page.poll else null
        pollHeaderAdapter.setPoll(inlinePoll)
        if (page.poll != null) {
            currentPoll = page.poll
            cachedPollTopicId = page.id
        }
        pageHasPoll = currentPoll != null
        // Topic hat (SAME policy as the WebView): 4pda echoes the topic's first post as a «шапка»
        // at the top of EVERY page. Keep it only on the real first page (as a collapsible block);
        // on deep pages strip the repeated copy so it doesn't show again. processHatForPage returns
        // the hat id only for page 1 (inline), but also captures [toolbarHatItem] on ANY page so the
        // ⓘ toolbar button (and its popup) work even when the topic opens directly on a deep page.
        topicHatPostId = processHatForPage(page)
        pageHasHat = toolbarHatItem != null
        refreshToolbarState()
        applyToolbarTitleFromPage(page) // fill the title from the loaded page (deep-link/deep-page opens)
        val items = filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current))
        // Пагинацию киим id ОТРЕНДЕРЕННОЙ страницы, а не URL запроса: findpost на перенесённый
        // модераторами пост 302-редиректит в тему, где пост живёт сейчас, и url (showtopic=старая
        // тема) расходится с page.id. Киинг по url тогда заставлял infinite-scroll ДОПИСЫВАТЬ в
        // список страницы ЧУЖОЙ темы (реальное «посты двух тем слились»), а «В конец темы» грузил
        // старую тему (лог 13_07: рендер «Энергосбережения» + страницы/конец «Обсуждения»).
        // URL-id остаётся лишь fallback'ом на случай, если парсер не вытащил id из HTML.
        val topicId = page.id.takeIf { it > 0 } ?: ThemeApi.extractTopicIdFromUrl(url) ?: 0
        pagination.reset(topicId, page.pagination, items)
        // Обновление снизу: на этой странице непрочитанного нет, но тема выросла за её границу —
        // шагаем вниз, пока не найдём страницу с непрочитанным (иначе новые посты не видны вовсе).
        // Каждый шаг увеличивает loadedPage, так что цикл упирается в последнюю страницу.
        if (refreshFollowNextPageArmed && pendingJumpToBottom &&
                items.none { it.postId > pendingRefreshSeenUpToPostId }) {
            pagination.nextPageUrl()?.let { next ->
                if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                    android.util.Log.i("FPDA_REFRESH_ANCHOR", "follow_next_page topic=${page.id} " +
                            "page=${page.pagination.current}/${page.pagination.all} seenUpTo=$pendingRefreshSeenUpToPostId")
                }
                loadTopic(next, preserveRefreshIntent = true) // индикатор держим: вложенная загрузка его снимет
                return
            }
        }
        refreshFollowNextPageArmed = false
        updateRefreshGesture() // top pull feeds prev-page loading, not refresh, when pages are above
        barCurrentPage = pagination.loadedPage
        loadedItems.clear()
        loadedItems.addAll(items)
        mentionScannedPostIds.clear()
        markedTopicReadAtEnd = false
        userScrollGestureThisSession = false // новая сессия просмотра — жестов ещё не было
        userDraggedListThisSession = false // ...и протягиваний списка тоже
        sessionRenderedAtMs = android.os.SystemClock.elapsedRealtime()
        topicHasUnread = page.hasUnreadTarget // drives «К непрочитанному» in the smart-nav menu
        // Порт WebView-guard'а (ThemeUseCase.onPrimaryThemeLoaded → shouldSuppressMarkReadForSession,
        // куда натив не ходит): подлинное открытие на первом непрочитанном, севшее выше низа
        // страницы, не должно метить тему прочитанной в момент рендера — только после жеста юзера.
        // OR с мостиком boundary-резюма: его findpost-страница unread-метаданных не несёт.
        suppressEndMarkReadUntilUserScroll = pendingSuppressEndMarkReadForResume ||
                forpdateam.ru.forpda.presentation.theme
                        .TopicUnreadOpenPolicy.shouldSuppressMarkReadForFirstUnreadOpen(
                                forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy
                                        .parseOpenSessionKind(page.openSessionKind),
                                page,
                        )
        pendingSuppressEndMarkReadForResume = false
        closeSearch() // matches from a previous page are stale after a reload
        updatePaginationBar()
        applyInitialHatCollapsedState(topicHatPostId)
        postsAdapter.setTopicHat(topicHatPostId, hatCollapsed)
        submitPosts {
            if (view != null) {
                applyInitialAnchor(page.anchorPostId, page.hasUnreadTarget, items)
                // Fill an under-filled last page from previous pages (no empty area + scroll-back works).
                recyclerView.post { maybeFillLastPage() }
                // Открытие СРАЗУ на последнем посте средней страницы (первый непрочитанный == последний на
                // странице) садит юзера у самого низа: тянуть вниз некуда → onScrolled(dy>0) не приходит →
                // след. страница не подгружалась, пока не «поёрзаешь». Программная посадка на последний пост
                // (anchorPost isLast → вложенные recyclerView.post) устаканивается через пару кадров, поэтому
                // взводим probe с задержкой (в паритет с reanchor'ами jumpToBottom 250/550). maybeFillLastPage
                // при этом no-op'ит на средней странице (loadedPage<totalPages), а maybeLoadNextPage —
                // на последней (hasNextPage=false), так что пути не конфликтуют.
                recyclerView.postDelayed({ if (view != null) maybeLoadNextPage() }, 350)
            }
        }
        enrichLoadedPage(page)
    }

    /**
     * Deferred desktop/profile metadata merge (author post counts «💬 N» + real post-rating/rep
     * metadata that mobile HTML omits) — parity with the WebView ViewModel's mergeDesktopRatingsIntoPage,
     * which runs AFTER first paint. Re-maps the affected posts and patches them in [loadedItems] by
     * post id (safe even if infinite scroll grew the list meanwhile).
     */
    private fun enrichLoadedPage(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { themeApi.enrichPageMetadata(page, page.url.orEmpty()) }
            }
            if (view == null) return@launch
            val enrichedById = mapper.map(page.posts).associateBy { it.postId }
            var changed = false
            for (i in loadedItems.indices) {
                val existing = loadedItems[i]
                val enriched = enrichedById[existing.postId] ?: continue
                // Freshly-mapped items carry pageNumber=0 (the mapper has no page context) — preserve the
                // existing page tag so the «Страница N» dividers survive the deferred metadata merge.
                val updated = enriched.copy(pageNumber = existing.pageNumber)
                if (updated != existing) {
                    loadedItems[i] = updated
                    changed = true
                }
            }
            if (changed) submitPosts { reanchorBottomAfterGrowth() }
        }
    }

    /**
     * After the metadata enrichment re-lays out the list (taller posts), keep the already-read topic's last
     * post pinned to the BOTTOM with its action buttons visible — but only while the user is still sitting on
     * it (hasn't scrolled up). Otherwise it would yank them back down.
     */
    private fun reanchorBottomAfterGrowth() {
        if (view == null) return
        val anchorId = anchoredBottomPostId ?: return
        if (anchorId != loadedItems.lastOrNull()?.postId) { anchoredBottomPostId = null; return }
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val itemCount = recyclerView.adapter?.itemCount ?: return
        val lastPos = itemCount - 1
        // Still parked at the end (last item at least partially visible) → re-pin it to the bottom.
        if (lm.findLastVisibleItemPosition() >= lastPos) {
            val lastView = recyclerView.findViewHolderForAdapterPosition(lastPos)?.itemView
            val visible = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
            if (lastView != null && lastView.height > visible) {
                // Last post is TALLER than the screen: it must stay TOP-aligned so the user reads it from the
                // start (user: «большой пост должен выравниваться по верху, а не обрезаться»). Never bottom-
                // anchor it here — that cut its top. Re-top-align only if growth pushed its top off-screen.
                if (lastView.top < recyclerView.paddingTop) lm.scrollToPositionWithOffset(lastPos, 0)
                return
            }
            lm.scrollToPosition(lastPos)
            bottomAlignPost(lastPos)
        } else {
            anchoredBottomPostId = null // user scrolled away — stop re-pinning
        }
    }

    /**
     * Клиентская граница прочитанного (модель Discourse) — общая с WebView-движком. На ПЕРВОМ открытии
     * темы сверяем, куда сел бы сервер, с самым дальним реально-виденным постом ([TopicReadBoundaryStore]).
     * Если сервер увёл бы СТРОГО НИЖЕ (новее) границы — между границей и серверным таргетом есть
     * непрочитанное, которое иначе проскочим (4PDA/IPB метит страницу прочитанной по факту загрузки, из-за
     * чего getnewpost/getlastpost уезжают вниз). Тогда перезагружаемся findpost'ом на границу.
     *
     * Фаер строго один раз за открытие ([boundaryResumeArmed] гасится сразу), явные findpost-дип-линки и
     * переходы по страницам не переопределяем. При отсутствии границы (cold-miss) — фолбэк на текущее
     * серверное поведение (безопасно).
     *
     * @return true, если запущен findpost-резюм (эту загрузку рендерить не нужно — return у вызывающего).
     */
    private fun maybeResumeToReadBoundary(
            url: String,
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ): Boolean {
        if (!boundaryResumeArmed) return false
        boundaryResumeArmed = false // один раз за открытие, что бы дальше ни решили
        if (page.id <= 0) return false
        // Явный findpost-дип-линк (упоминание/закладка на конкретный пост) или наш собственный резюм —
        // не переопределяем; переход по страницам тоже.
        if (url.contains("view=findpost", ignoreCase = true) ||
                url.contains("act=findpost", ignoreCase = true)) return false
        if (pendingJumpToTop) return false
        val boundaryId = readBoundaryStore.lastSeenPostId(page.id)
        if (boundaryId <= 0) return false
        // Кросс-девайс детект: если серверный якорь ушёл дальше, чем на одну страницу от самой дальней
        // страницы, которую грузило ЭТО устройство, тему дочитали на другом устройстве/в браузере
        // (walk-down так далеко уйти не может). Локальная граница устарела — стираем её и доверяем
        // серверному якорю (иначе бы откатили юзера на старую позицию, где он остановился здесь).
        if (forpdateam.ru.forpda.presentation.theme.TopicReadBoundaryPolicy.isCrossDeviceReadProgress(
                        serverAnchorPage = page.pagination.current,
                        maxLoadedPage = readBoundaryStore.maxLoadedPage(page.id))) {
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                android.util.Log.i("FPDA_READ_BOUNDARY",
                        "cross_device topic=${page.id} serverPage=${page.pagination.current} maxLoadedPage=${readBoundaryStore.maxLoadedPage(page.id)} → clear boundary, trust server")
            }
            readBoundaryStore.clear(page.id)
            return false
        }
        val serverAnchorId = page.anchorPostId?.removePrefix("entry")?.trim()?.toIntOrNull()
                ?: page.anchor?.removePrefix("entry")?.trim()?.toIntOrNull()
        val lastLoadedId = page.posts.lastOrNull { it.id > 0 }?.id
        // Первый не-виденный пост окна = наименьший id строго больше границы. Если сервер сел ровно на
        // него (свежий ответ сразу за границей, ничего не пропущено) — резюмить на границу не нужно,
        // иначе первый непрочитанный (последний пост) обрезался бы снизу под уже прочитанным.
        val firstUnseenId = page.posts.filter { it.id > boundaryId }.minByOrNull { it.id }?.id
        // Эксепшен firstUnseen валиден лишь когда сама граница в загруженном окне: иначе (граница на
        // пред. странице, сервер walk-down'ом ушёл на след.) firstUnseen посчитан по чужому окну и
        // ложно совпадёт с серверным якорем, проглотив непрочитанный хвост пред. страницы.
        val boundaryOnPage = page.posts.any { it.id == boundaryId }
        val resumeId = forpdateam.ru.forpda.presentation.theme.TopicReadBoundaryPolicy.resumeAnchorPostId(
                boundaryPostId = boundaryId,
                serverAnchorPostId = serverAnchorId,
                lastLoadedPostId = lastLoadedId,
                firstUnseenPostId = firstUnseenId,
                boundaryPostOnPage = boundaryOnPage,
        ) ?: return false
        // Резюм — findpost на границу. Гасим «в конец/на верх», чтобы вложенная загрузка села на границу.
        pendingJumpToBottom = false
        pendingJumpToTop = false
        // Findpost-страница резюма не несёт unread-метаданных — пробрасываем гейт мгновенного mark-read
        // вручную: юзер сядет на границу с непрочитанным НИЖЕ, «низ виден при рендере» ≠ «дочитал».
        pendingSuppressEndMarkReadForResume = true
        // ...и помечаем эту посадку «тихим резюмом»: её вспышка должна подчиняться «Подсветке
        // непрочитанного» (иначе FIND_POST-путь подсветил бы пост при выключенной настройке).
        pendingSilentResumeLanding = true
        // Fallback на случай, если пост границы удалён из темы: findpost по нему упадёт (исключение
        // «Пост #… не найден») или отдаст пустые посты. Тогда вместо пустого экрана рендерим ЭТУ уже
        // распарсенную getnewpost-страницу. Взводим ДО вложенной загрузки — её onSuccess/onFailure читают.
        resumeFallbackPage = page
        resumeFallbackUrl = url
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_READ_BOUNDARY",
                    "native resume_findpost topic=${page.id} boundary=$resumeId serverAnchor=$serverAnchorId lastLoaded=$lastLoadedId")
        }
        loadTopic(forpdateam.ru.forpda.presentation.theme.TopicUnreadFindPostReloadPolicy
                .buildFindPostUrl(page.id, resumeId.toString()))
        return true
    }

    /**
     * Top-align [concatPos] (first-unread / findpost / read-boundary target) so the user starts reading
     * from it. When there is enough content below, its top sits at the list's top edge. When the anchor is
     * near the END (little/no content below), the layout manager clamps at the natural bottom and the post
     * ends up fully visible without any empty region. We do NOT add transient bottom padding to force it to
     * the very top — that left an ugly empty block below the anchor (user report «пустая область внизу»).
     * A short last page is handled separately by [maybeFillLastPage], which pulls previous pages in.
     */
    /**
     * Pull the post at [concatPos] fully into view at the BOTTOM: after a bottom-anchor [scrollToPosition]
     * the layout manager can still leave a post TALLER than the remaining space cut off (its action buttons
     * below the fold — user report «последнее сообщение обрезается по низу, не видно кнопок»). Measure the
     * laid-out item and, if it overshoots the bottom edge, [scrollBy] exactly that much so its bottom (with
     * the like/quote/reputation row) sits at the viewport bottom. No-op when the post already fits.
     */
    /**
     * Breathing gap kept BELOW the last post at rest so its bottom border / rounded corner / open-highlight
     * clears the tab-bar chrome instead of sitting flush against it, where it reads as clipped (user:
     * «последнее сообщение чуть срезано снизу, видно по границе поста»).
     *
     * Sized so the space between the last card and the bottom-nav bar EQUALS the inter-post gap (user:
     * «такой же минимальный отступ как между постами»). Cards carry a 4dp vertical margin each, so adjacent
     * posts show an 8dp neutral gap (4dp + 4dp). The last card already contributes its own 4dp bottom margin,
     * so this padding adds the remaining 4dp → 8dp total, matching the inter-post spacing.
     *
     * This MUST be layout padding, not a scroll: the last post is the content end, so a bottom-anchor
     * `scrollBy` to open a gap below it is clamped to zero (nothing below to scroll into) and does nothing —
     * verified on device (clearance stayed 0 despite scrollBy(gap)). Reserved as extra [recyclerView]
     * paddingBottom; clipToPadding=false keeps mid-scroll content edge-to-edge, so the gap only shows at the
     * very bottom.
     */
    private fun bottomRestGapPx(): Int = (4 * resources.displayMetrics.density).toInt()

    /** Height reserved for the CLASSIC pagination bar when it is currently shown, else 0. */
    private fun classicPaginationBarPadPx(): Int {
        val show = pagination.isInitialised && isClassicMode() && pagination.totalPages > 1 &&
                messagePanel?.visibility != View.VISIBLE && mainPreferencesHolder.getTopicPaginationPanels().hasBottom
        return if (show) (52 * resources.displayMetrics.density).toInt() else 0
    }

    /**
     * How many pixels of the list's bottom edge are actually covered by the bottom-nav chrome ON SCREEN.
     *
     * MEASURED, never assumed. Whether the host insets the fragment container above the tab bar is
     * device-dependent: on the emulator it does (overlap 0), on a real device it did NOT — the list ran to
     * (and past) the screen bottom, so the last post's final [bottomNavChromePad] pixels sat behind the
     * opaque tab bar. Device log: window height 2772, rvBottomScreen 2786, chromePad 182 → the card's bottom
     * landed at 2772, i.e. fully behind the bar, even though it was correctly parked 14px above the list's
     * own bottom edge.
     *
     * The window's decor height is the authoritative screen height here (displayMetrics.heightPixels
     * under-reported on some devices, which is what made an earlier attempt at this under-reserve).
     */
    private fun listBottomChromeOverlapPx(): Int {
        val decorHeight = activity?.window?.decorView?.height ?: 0
        if (decorHeight <= 0 || recyclerView.height <= 0) return 0
        val loc = IntArray(2)
        recyclerView.getLocationOnScreen(loc)
        val listBottomOnScreen = loc[1] + recyclerView.height
        val chromeTopOnScreen = decorHeight - bottomNavChromePad
        return (listBottomOnScreen - chromeTopOnScreen).coerceAtLeast(0)
    }

    /**
     * List bottom padding = measured chrome overlap ([listBottomChromeOverlapPx]) + classic pagination bar
     * ([classicPaginationBarPadPx]) + inter-post breathing gap ([bottomRestGapPx]).
     *
     * Reserving the MEASURED overlap (instead of either always adding [bottomNavChromePad] — a huge empty
     * band on hosts that already inset the container — or never adding it — the last post hidden behind the
     * tab bar on hosts that don't) is what makes the last post's clearance correct on every device.
     */
    private fun applyListBottomPadding() {
        if (view == null) return
        val target = listBottomChromeOverlapPx() + classicPaginationBarPadPx() + bottomRestGapPx() +
                deepLinkAnchorExtraBottomPad
        if (recyclerView.paddingBottom != target) {
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop,
                    recyclerView.paddingRight, target)
            if (anchoredBottomPostId != null) recyclerView.post { reanchorBottomAfterGrowth() }
        }
    }

    /**
     * Keep the last post's bottom clear of the fold for as long as we are parked on it, correcting on EVERY
     * layout pass instead of at a few guessed moments.
     *
     * Why: after first paint the list keeps GROWING under us — [enrichLoadedPage] adds the author's «💬 N»
     * count line and the real rating row to every post, avatars and inline images finish loading, spoilers
     * measure. Each of those makes the last card taller AFTER it was bottom-clamped, sliding its bottom
     * (rating row + border) under the tab bar — the recurring «последний пост обрезается снизу». The old
     * defence was a submitList commit callback plus two hardcoded postDelayed(250/550) re-anchors; network
     * enrichment and image decodes routinely land after that window, and AsyncListDiffer silently drops a
     * commit callback that a later submitList supersedes. Both are timing guesses, so both leak.
     *
     * A global-layout listener is the honest fix: whatever grew, whenever it grew, the very next layout pass
     * measures the overshoot and scrolls it away. Self-limiting — once the overshoot is 0 it no-ops.
     *
     * Deliberately does nothing when: the user scrolled off the bottom anchor ([anchoredBottomPostId] is
     * cleared on an upward scroll), a scroll/fling is in flight (never fight the finger), or the last post is
     * TALLER than the viewport (it is top-aligned on purpose so it reads from the start).
     */
    private val bottomPinLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        // The list's on-screen position (and therefore its overlap with the tab bar) can change at any
        // layout pass — recompute the reserved clearance first, then correct the last post inside it.
        // Both are idempotent, so a settled layout produces no further work.
        applyListBottomPadding()
        maintainBottomPin()
        healBottomEndGap()
        // The content height moves under us too (enrichment, images, spoilers) — «низ ли это» must follow it,
        // and no scroll event fires for a layout-driven change. Idempotent: a no-op once the state matches.
        updateBottomPaginationBarOffset()
    }

    /**
     * Close an EMPTY BAND left below the last post: after a fast fling the list can come to rest scrolled
     * PAST the end of its content — the last card's bottom sits above the padded viewport bottom and a strip
     * of bare page tone shows between it and the pagination bar (user: «при быстром скролле внизу иногда
     * появляется пустое место, а при скролле чуть вверх-вниз оно пропадает»).
     *
     * Why it happens: post heights are not final when they are laid out. Inline images (WRAP_CONTENT until
     * Coil delivers a drawable), smile spans and the async metadata enrichment all resize cards AFTER the
     * layout that positioned them. A child's `requestLayout` raised while RecyclerView is mid-fling is
     * swallowed (`stopInterceptRequestLayout(false)` drops the deferred-layout flag), so the shrink that
     * follows a re-bind never gets a correcting layout pass — the scroll offset stays where the taller
     * estimate put it. LinearLayoutManager fixes exactly this in `fixLayoutEndGap`, but only when a layout
     * pass actually runs; the user's own workaround (nudge the list) is what finally triggers one.
     *
     * So do it ourselves, on the same terms: only at rest, only when the LAST item is laid out and there is
     * scrollable content above to pull down. [RecyclerView.scrollBy] clamps, so a topic genuinely shorter
     * than the viewport (its trailing space is legitimate) cannot be dragged out of place. Self-limiting —
     * once the gap is 0 it no-ops.
     *
     * Deliberately skipped while a bottom anchor ([anchoredBottomPostId]) or a deep-link top-align reservation
     * ([deepLinkAnchorExtraBottomPad]) owns the bottom: there the space below the last post is on purpose.
     */
    private fun healBottomEndGap() {
        if (view == null || gapHealInProgress || pinningInProgress) return
        if (anchoredBottomPostId != null || deepLinkAnchorExtraBottomPad != 0) return
        if (recyclerView.isComputingLayout) return
        if (recyclerView.scrollState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastPos = (recyclerView.adapter?.itemCount ?: 0) - 1
        if (lastPos < 0) return
        if (lm.findLastVisibleItemPosition() != lastPos) return // не у конца списка — пустоты внизу нет
        val lastView = lm.findViewByPosition(lastPos) ?: return
        val gap = (recyclerView.height - recyclerView.paddingBottom) - lastView.bottom
        if (gap <= 0) return
        if (!recyclerView.canScrollVertically(-1)) return // контент короче экрана — пустота законна
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_CLEAR", "healBottomEndGap gap=$gap lastBottom=${lastView.bottom} " +
                    "padB=${recyclerView.paddingBottom} rvH=${recyclerView.height}")
        }
        // Scrolling from inside a layout pass is illegal → one tick later. Re-check the state we relied on.
        // The flag stays raised ACROSS the scroll so [onScrolled] can tell this layout correction from a real
        // upward scroll (it must not flip the FAB arrow to «up» or arm a previous-page load).
        gapHealInProgress = true
        recyclerView.post {
            try {
                if (view == null || recyclerView.isComputingLayout) return@post
                if (recyclerView.scrollState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return@post
                if (anchoredBottomPostId != null || deepLinkAnchorExtraBottomPad != 0) return@post
                val stillLast = lm.findViewByPosition((recyclerView.adapter?.itemCount ?: 0) - 1) ?: return@post
                val still = (recyclerView.height - recyclerView.paddingBottom) - stillLast.bottom
                if (still > 0) recyclerView.scrollBy(0, -still) // clamped: pulls earlier posts down into the band
            } finally {
                gapHealInProgress = false
            }
        }
    }

    private fun maintainBottomPin() {
        if (view == null || pinningInProgress) return
        val anchorId = anchoredBottomPostId ?: return
        if (anchorId != loadedItems.lastOrNull()?.postId) { anchoredBottomPostId = null; return }
        if (recyclerView.isComputingLayout) return
        if (recyclerView.scrollState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastPos = (recyclerView.adapter?.itemCount ?: 0) - 1
        if (lastPos < 0) return
        val lastView = recyclerView.findViewHolderForAdapterPosition(lastPos)?.itemView ?: return
        val visible = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        if (visible <= 0) return
        if (lastView.height > visible) {
            // Grew TALLER than the screen: it can never be shown whole, so show it from its START. Merely
            // returning here (the old behaviour) froze it wherever the growth left it — top mid-screen and
            // bottom clipped, i.e. cut on the very side the user complained about.
            if (lastView.top != recyclerView.paddingTop) {
                pinScroll { lm.scrollToPositionWithOffset(lastPos, 0) }
            }
            return
        }
        val overshoot = lastView.bottom - (recyclerView.height - recyclerView.paddingBottom)
        if (overshoot <= 0) return
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_CLEAR", "pin correcting overshoot=$overshoot cardH=${lastView.height} visible=$visible")
        }
        pinScroll { recyclerView.scrollBy(0, overshoot) }
    }

    /** Run a corrective scroll one tick later (scrolling from inside a layout pass is illegal), re-entrancy-safe. */
    private fun pinScroll(action: () -> Unit) {
        pinningInProgress = true
        recyclerView.post {
            pinningInProgress = false
            if (view == null || anchoredBottomPostId == null || recyclerView.isComputingLayout) return@post
            action()
        }
    }

    /**
     * DIAGNOSTIC: log the actual on-screen clearance between the last post card's bottom and the top of the
     * bottom-nav tab bar, so the last-post clipping can be MEASURED on a device instead of guessed. Positive
     * = gap (good), negative = the card runs behind the bar (clipped). Debug builds only.
     */
    private fun logLastPostClearance(tag: String) {
        if (!forpdateam.ru.forpda.BuildConfig.DEBUG) return
        recyclerView.post {
            if (view == null) return@post
            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@post
            val lastPos = (recyclerView.adapter?.itemCount ?: 0) - 1
            if (lastPos < 0) return@post
            val lastView = recyclerView.findViewHolderForAdapterPosition(lastPos)?.itemView
            val rvLoc = IntArray(2).also { recyclerView.getLocationOnScreen(it) }
            val rvBottomScreen = rvLoc[1] + recyclerView.height
            val cardBottomScreen = lastView?.let { rvLoc[1] + it.bottom }
            val decorH = activity?.window?.decorView?.height ?: 0
            val chromeTop = decorH - bottomNavChromePad
            android.util.Log.i("FPDA_CLEAR",
                    "$tag decorH=$decorH chromePad=$bottomNavChromePad chromeTop=$chromeTop " +
                    "overlap=${listBottomChromeOverlapPx()} padB=${recyclerView.paddingBottom} rvH=${recyclerView.height} " +
                    "rvBottomScreen=$rvBottomScreen cardH=${lastView?.height} cardBottomScreen=$cardBottomScreen " +
                    "lastVisiblePos=${lm.findLastVisibleItemPosition()}/$lastPos " +
                    "clearanceToChrome=${cardBottomScreen?.let { chromeTop - it }}")
        }
    }

    /**
     * Pull the post at [concatPos] up if its bottom is CLIPPED below the (padded) bottom edge — e.g. a mid
     * post whose action buttons fell behind the tab-bar chrome. For the true last post the gap comes from
     * [bottomRestGapPx] padding, not from here (scroll can't open space past the content end).
     */
    private fun bottomAlignPost(concatPos: Int) {
        recyclerView.post {
            if (view == null) return@post
            val itemView = recyclerView.findViewHolderForAdapterPosition(concatPos)?.itemView ?: return@post
            // A post TALLER than the viewport must never be bottom-anchored — that would cut its TOP (user:
            // «большой пост обрезается по верху, а должен выравниваться по верху»). Bottom-align only applies
            // to a post that fits; a tall one is left TOP-pinned by the caller.
            val visible = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
            if (itemView.height > visible) return@post
            val bottomLimit = recyclerView.height - recyclerView.paddingBottom
            val overshoot = itemView.bottom - bottomLimit
            if (overshoot > 0) recyclerView.scrollBy(0, overshoot)
        }
    }

    /**
     * Anchor the opened target post ([concatPos]) by the rule the user expects:
     *  • post FITS the viewport → show it FULLY — top-aligned when there is content below; for the LAST
     *    post ([isLast]) bottom-clamped so there is no empty area below AND its action buttons clear the
     *    tab-bar chrome;
     *  • post is TALLER than the viewport → align its TOP to the top edge («выравнивать по верху»), so it is
     *    NEVER cut on both sides — the user reads it from the beginning and scrolls down for the rest.
     */
    private fun anchorPost(concatPos: Int, isLast: Boolean, topAlign: Boolean = false) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(concatPos, 0) // provisional top-align
        recyclerView.post {
            if (view == null) return@post
            val itemView = recyclerView.findViewHolderForAdapterPosition(concatPos)?.itemView ?: return@post
            val visible = recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
            if (itemView.height > visible) return@post // taller than the screen → keep the TOP pinned
            if (isLast) {
                // Fitting last post: natural bottom clamp fills the space above (no empty block) and
                // bottomAlignPost lifts its buttons above the tab bar.
                lm.scrollToPosition(concatPos)
                bottomAlignPost(concatPos)
            } else if (topAlign) {
                // Explicit deep-link / quote target: bring it to the TOP for parity with the same tap on an
                // earlier page. Near the end of the topic there can be less than a viewport of content below
                // it, so the provisional scroll above clamped at the content edge and the target sits lower
                // than the top (its top offset by `shortfall`). Reserve that shortfall as transient bottom
                // room so a second top-align actually lands — see [deepLinkAnchorExtraBottomPad]. When the
                // target already reached the top (plenty below it), shortfall is 0 and nothing changes.
                val shortfall = itemView.top - recyclerView.paddingTop
                if (shortfall > 0) {
                    deepLinkAnchorExtraBottomPad = shortfall
                    applyListBottomPadding()
                    lm.scrollToPositionWithOffset(concatPos, 0)
                }
            } else {
                // Fitting mid post: pull it fully in if its bottom is clipped by the tab-bar chrome.
                val overflow = itemView.bottom - (recyclerView.height - recyclerView.paddingBottom)
                if (overflow > 0) recyclerView.scrollBy(0, overflow)
            }
        }
    }

    private fun applyInitialAnchor(
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
            items: List<NativePostItem>,
    ) {
        // Drop any deep-link top-align bottom room reserved by a PREVIOUS anchor so this fresh landing
        // measures against the real content end (a stale reservation would leave phantom space at the bottom).
        if (deepLinkAnchorExtraBottomPad != 0) {
            deepLinkAnchorExtraBottomPad = 0
            applyListBottomPadding()
        }
        val ids = items.map { it.postId }
        val targetId = anchorPostId?.toIntOrNull()
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_CLEAR", "applyInitialAnchor ids=${ids.size} jumpToBottom=$pendingJumpToBottom " +
                    "restore=$pendingRestorePostId jumpToTop=$pendingJumpToTop target=$targetId unread=$hasUnreadTarget")
        }
        val refreshSeenUpTo = pendingRefreshSeenUpToPostId
        pendingRefreshSeenUpToPostId = 0
        // «Тихий резюм» к границе прочитанного приходит как FIND_POST-посадка — её вспышка должна
        // подчиняться «Подсветке непрочитанного» (как посадка на первый непрочитанный), а не вспыхивать
        // всегда. Захватываем и гасим флаг здесь; он взводится только в [maybeResumeToReadBoundary].
        val silentResumeLanding = pendingSilentResumeLanding
        pendingSilentResumeLanding = false
        // Restore-scroll «где остановился»: вернуться на сохранённый пост и его точный offset (после
        // пересоздания фрагмента). Приоритетнее серверного якоря и границы прочитанного — это ровно то
        // место, где стоял пользователь.
        if (pendingRestorePostId > 0) {
            val restoreId = pendingRestorePostId
            val restoreOffset = pendingRestoreOffset
            pendingRestorePostId = 0
            pendingRestoreOffset = 0
            pendingJumpToBottom = false
            pendingJumpToTop = false
            val idx = ids.indexOf(restoreId)
            if (idx >= 0) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(idx + headerOffset(), restoreOffset)
                recyclerView.post { markVisiblePostsRead(); maybeMarkTopicReadAtEnd() }
                return
            }
            // Пост не в загруженном окне (findpost вернул другую страницу) — падаем в обычный якорь ниже.
        }
        // «В конец темы» / открытие прочитанной темы (getlastpost): последний пост. Если он ВМЕЩАЕТСЯ —
        // показываем целиком у нижнего края (кнопки над таббаром, без пустого блока); если он ВЫШЕ экрана —
        // выравниваем по верху (не режем с обеих сторон) — см. [anchorPost].
        if (pendingJumpToBottom) {
            pendingJumpToBottom = false
            // Обновление снизу принесло новые посты → садимся на ПЕРВЫЙ непрочитанный, а не на низ
            // (иначе новое уезжает вверх и тут же метится прочитанным).
            val firstUnseen = forpdateam.ru.forpda.presentation.theme.TopicRefreshAnchorPolicy
                    .firstUnseenPostId(ids, refreshSeenUpTo)
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
                android.util.Log.i("FPDA_REFRESH_ANCHOR", "topic=$pageTopicId seenUpTo=$refreshSeenUpTo " +
                        "firstUnseen=$firstUnseen lastOnPage=${ids.lastOrNull()} " +
                        "newCount=${ids.count { it > refreshSeenUpTo }}")
            }
            if (firstUnseen != null) {
                val idx = ids.indexOf(firstUnseen)
                anchoredBottomPostId = null
                anchorPost(idx + headerOffset(), isLast = idx == ids.size - 1)
                if (topicPreferencesHolder.getHighlightUnreadPost()) postsAdapter.requestHighlight(firstUnseen)
                recyclerView.post { markVisiblePostsRead(); maybeMarkTopicReadAtEnd() }
                return
            }
            val lastPos = (ids.size - 1 + headerOffset()).coerceAtLeast(0)
            anchoredBottomPostId = ids.lastOrNull()
            if (forpdateam.ru.forpda.BuildConfig.DEBUG) android.util.Log.i("FPDA_CLEAR", "anchor SET(jumpToBottom)=$anchoredBottomPostId lastPos=$lastPos")
            anchorPost(lastPos, isLast = true)
            // Highlight the last post — parity with the first-unread open; the read-topic open
            // lands on the last post, so flash it too. Gated by the same «highlight unread post» setting.
            if (topicPreferencesHolder.getHighlightUnreadPost()) {
                ids.lastOrNull()?.let { postsAdapter.requestHighlight(it) }
            }
            recyclerView.post { markVisiblePostsRead(); maybeMarkTopicReadAtEnd() }
            // Re-anchor once the viewport has SETTLED. On the send-reply reload (getlastpost right after
            // hideMessagePanel/hideKeyboard) the first anchor can measure a still-collapsing viewport, so a
            // FITTING post is mis-judged as taller-than-screen and top-pinned → cut on the bottom. A settle-
            // delayed [reanchorBottomAfterGrowth] re-measures with the final viewport (height-aware +
            // idempotent, so it no-ops for an already-correct open). Two ticks cover slow keyboard anims.
            recyclerView.postDelayed({ reanchorBottomAfterGrowth() }, 250)
            recyclerView.postDelayed({ reanchorBottomAfterGrowth(); logLastPostClearance("jumpToBottom+550") }, 550)
            return
        }
        // An explicit page-jump lands on the first post of the requested page, ignoring the server's
        // unread/find anchor (cf. «go to page N lands on last post» — we force the page top).
        val jumpToTop = pendingJumpToTop
        pendingJumpToTop = false
        val request = when {
            jumpToTop -> AnchorRequest.Top
            hasUnreadTarget && targetId != null ->
                AnchorRequest.Post(targetId, AnchorRequest.Post.Reason.FIRST_UNREAD)
            targetId != null ->
                AnchorRequest.Post(targetId, AnchorRequest.Post.Reason.FIND_POST)
            else -> AnchorRequest.Top
        }
        when (val resolution = anchorResolver.resolve(ids, request)) {
            is AnchorResolution.Position -> {
                // For a fresh "to top" open, land on the very top (the poll header, if any, then #1),
                // matching the WebView which shows the poll first. For post targets, offset past the
                // poll header (adapter position 0) to the resolved POST.
                val target = if (request is AnchorRequest.Top) 0 else resolution.index + headerOffset()
                // Height-aware anchoring (see [anchorPost]): a FITTING target is shown fully (last post
                // bottom-clamped with its buttons above the tab bar; others top-aligned); a target TALLER
                // than the screen is pinned by its TOP, never cut on both sides.
                val isLastPost = request !is AnchorRequest.Top && resolution.index == ids.size - 1
                when {
                    request is AnchorRequest.Top ->
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(target, 0)
                    isLastPost -> {
                        anchoredBottomPostId = ids.last()
                        if (forpdateam.ru.forpda.BuildConfig.DEBUG) android.util.Log.i("FPDA_CLEAR", "anchor SET(resolver)=$anchoredBottomPostId")
                        anchorPost(target, isLast = true)
                    }
                    // An explicit deep-link / quote / findpost target top-aligns (reserving room at the topic
                    // end if needed); a first-unread landing keeps the empty-space-averse behaviour.
                    else -> anchorPost(target, isLast = false,
                            topAlign = request is AnchorRequest.Post &&
                                    request.reason == AnchorRequest.Post.Reason.FIND_POST)
                }
                // Flash the resolved post once so the user sees where a link/find/unread open landed. The
                // first-unread landing — and the AUTOMATIC read-boundary resume, which arrives as FIND_POST
                // but is really an unread landing (see [silentResumeLanding]) — obey the «highlight unread
                // post» setting; deliberate link/find/quote landings always flash (that flash is a «where did
                // I land» cue, not the unread highlight).
                val isUnreadStyleLanding = request is AnchorRequest.Post &&
                        (request.reason == AnchorRequest.Post.Reason.FIRST_UNREAD || silentResumeLanding)
                if (request is AnchorRequest.Post &&
                        (!isUnreadStyleLanding || topicPreferencesHolder.getHighlightUnreadPost())) {
                    postsAdapter.requestHighlight(request.postId)
                }
                // Лог 11_07-11-32: посадка на ПЕРВЫЙ НЕПРОЧИТАННЫЙ штампует границу прочитанного на сам
                // якорный пост. [recordReadBoundaryAtRest] пишет только по жесту — но виз «открыл-глянул-
                // закрыл» тогда не оставляет границы вовсе, а сам GET уже пометил страницу прочитанной на
                // сервере: повторное открытие получало all-read bottom-редирект и резюму не от чего было
                // оттолкнуться (улёт на последний пост мимо непрочитанного). Штампуем ТОЛЬКО якорь (не
                // вьюпорт) и ТОЛЬКО для unread-посадки: резюм при переоткрытии сядет ровно сюда же.
                // Explicit-посадки (page jump / deep-link / restore) не штампуем — monotonic recordSeen
                // сжёг бы непрочитанное выше глубокой цели.
                if (request is AnchorRequest.Post &&
                        request.reason == AnchorRequest.Post.Reason.FIRST_UNREAD &&
                        pageTopicId > 0) {
                    readBoundaryStore.recordSeen(pageTopicId, request.postId, barCurrentPage)
                }
            }
            // PostNotLoaded / Empty: nothing to do — downward pagination will bring later pages in.
            else -> Unit
        }
        // Mentions on the initially-visible posts must clear even without a scroll gesture; and a
        // short topic that fully fits on screen (no scroll) still counts as read-to-end.
        recyclerView.post {
            markVisiblePostsRead()
            maybeMarkTopicReadAtEnd()
        }
    }

    /**
     * Clear «Ответы»/mentions for posts currently on screen — mirrors the WebView path's
     * [forpdateam.ru.forpda.presentation.theme.ThemeViewModel.onVisiblePageChanged] →
     * `onRenderedTopicPosts`. [EventsRepository.onTopicPostsRead] itself filters to this topic's
     * real mentions, so feeding it the visible post ids is safe and idempotent; the local
     * [mentionScannedPostIds] guard just avoids re-feeding posts already scanned this session.
     */
    private fun markVisiblePostsRead() {
        if (pageTopicId <= 0) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION ||
                last == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            return
        }
        val header = headerOffset()
        val visibleIds = ArrayList<Int>()
        var hasNew = false
        for (pos in first..last) {
            val item = loadedItems.getOrNull(pos - header) ?: continue
            if (item.postId <= 0) continue
            visibleIds.add(item.postId)
            if (mentionScannedPostIds.add(item.postId)) hasNew = true
        }
        if (hasNew && visibleIds.isNotEmpty()) {
            eventsRepository.onTopicPostsRead(pageTopicId, visibleIds)
        }
        // Границу прочитанного здесь НЕ двигаем: этот метод зовётся на каждый кадр onScrolled, и запись
        // «самого дальнего частично-видимого» отсюда завышала границу (мелькнул краем при флинге =
        // «прочитан» навсегда — recordSeen монотонен). Граница пишется в [recordReadBoundaryAtRest]
        // по устоявшемуся вьюпорту; упоминаниям же частичная видимость подходит — «Ответы» должны
        // гаснуть, как только пост показался.
    }

    /**
     * Клиентская граница прочитанного ([TopicReadBoundaryStore]): продвигаем по УСТОЯВШЕМУСЯ вьюпорту —
     * только из [onScrollStateChanged] (IDLE). Правила против завышения (симптом «якорь проскакивает
     * непрочитанные», см. [maybeResumeToReadBoundary]):
     *  - пост, торчащий частично у НИЖНЕГО края, не считается виденным — берём самый глубокий ЦЕЛИКОМ
     *    видимый ([LinearLayoutManager.findLastCompletelyVisibleItemPosition]), а не последний частичный;
     *  - покадровой записи из [onScrolled] нет вовсе: флинг «вниз глянуть и обратно» больше не сжигает
     *    границу до самой глубокой мелькнувшей точки;
     *  - виз без единого жеста (открыл-глянул-закрыл) вьюпорт НЕ записывает: безопасное направление —
     *    максимум перечитывание, но не пропуск; дочитку до конца закрывает [maybeMarkTopicReadAtEnd].
     *    Исключение — unread-посадка штампует САМ якорный пост в [applyInitialAnchor] (иначе у резюма
     *    при переоткрытии нет опоры против серверного walk-down от GET этого же открытия).
     * Первый видимый (частично прокручен НАД верхним краем) считается прочитанным: юзер уже прошёл его
     * верх; это же покрывает пост выше экрана, у которого «целиком видимых» просто нет — иначе граница
     * застряла бы перед ним навсегда. Направление ошибки безопасно: резюм садится НА граничный пост.
     */
    private fun recordReadBoundaryAtRest() {
        if (pageTopicId <= 0) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val header = headerOffset()
        var deepestSeen = 0
        for (pos in intArrayOf(lm.findLastCompletelyVisibleItemPosition(), lm.findFirstVisibleItemPosition())) {
            if (pos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) continue
            val id = loadedItems.getOrNull(pos - header)?.postId ?: continue
            if (id > deepestSeen) deepestSeen = id
        }
        if (deepestSeen > 0) readBoundaryStore.recordSeen(pageTopicId, deepestSeen, barCurrentPage)
    }

    /**
     * Отметить, что ЭТО устройство загрузило страницу [page] с сервера (реально видел юзер её или нет —
     * даже предзагрузку гибридным скроллом). Питает трек `maxLoaded* ` в [readBoundaryStore], по которому
     * [maybeResumeToReadBoundary] отличает серверный walk-down от прогресса на другом устройстве. Зовётся
     * из ВСЕХ точек загрузки страницы (первичный рендер, догрузка next/prev, страница после отправки).
     * maxOf id — шапка (эхо первого поста темы) имеет наименьший id и максимум не искажает.
     */
    private fun recordMaxLoaded(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage) {
        if (page.id <= 0) return
        val maxPostId = page.posts.maxOfOrNull { it.id } ?: return
        if (maxPostId > 0) readBoundaryStore.recordLoaded(page.id, maxPostId, page.pagination.current)
    }

    /**
     * When the user scrolls to the very bottom of the LAST page, mark the whole topic read —
     * mirrors the WebView path's [forpdateam.ru.forpda.presentation.theme.ThemeViewModel]
     * `markTopicReadIfEndReached`. Fires once per topic load. The single chokepoint
     * [ThemeUseCase.markTopicRead] clears shade notifications, updates cross-screen state and
     * fires the server mark-read, so «прочитал до конца» reliably un-bolds the topic in favorites.
     */
    private fun maybeMarkTopicReadAtEnd() {
        if (markedTopicReadAtEnd || pageTopicId <= 0) return
        if (pagination.hasNextPage()) return // ещё есть незагруженные страницы ниже — это не конец темы
        if (loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        // «Конец темы» = список ФИЗИЧЕСКИ упёрт в самый низ последней страницы ([canScrollVertically]).
        // Это устойчивее прежнего сравнения findLastVisibleItemPosition ≥ lastItemPosition: последний
        // измеритель сбивают клип последнего поста таббаром и нижний паддинг — на реальном устройстве
        // «низ виден» мог не наступить ни на одном кадре, хотя ниже физически скроллить уже некуда
        // (симптом из видео: доскроллил в конец, но тема осталась непрочитанной). Оставляем и старый
        // сигнал как запасной — на случай, если canScrollVertically временно врёт при доросте списка.
        val atAbsoluteBottom = !recyclerView.canScrollVertically(1)
        val lastVisible = lm.findLastVisibleItemPosition()
        val lastItemPosition = headerOffset() + loadedItems.size - 1
        val reachedEnd = atAbsoluteBottom ||
                (lastVisible != androidx.recyclerview.widget.RecyclerView.NO_POSITION &&
                        lastVisible >= lastItemPosition)
        if (!reachedEnd) return
        // Гейт против «открыл-глянул-закрыл»: пока НЕТ доказательства чтения — не метим. Открытие на
        // первом непрочитанном сажает у низа сразу при рендере ([suppressEndMarkReadUntilUserScroll]),
        // и «низ достигнут» само по себе ещё не «дочитал». Доказательство — любое из: жест-скролл,
        // касание списка пальцем (overscroll в самом низу на устройстве может НЕ сменить scroll-state
        // на DRAGGING — тогда флаг жеста не взводится, но касание фиксируется), либо заметный dwell.
        // Как только suppress снят (жест уже был) — короткое замыкание, метим сразу.
        val hasReadEvidence = userScrollGestureThisSession || userDraggedListThisSession ||
                (android.os.SystemClock.elapsedRealtime() - sessionRenderedAtMs) >=
                        forpdateam.ru.forpda.presentation.theme.TopicNoGestureDwellReadPolicy.MIN_DWELL_MS
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) android.util.Log.i("FPDA_MARKEND",
                "topic=$pageTopicId reachedEnd=$reachedEnd atBottom=$atAbsoluteBottom " +
                "gesture=$userScrollGestureThisSession drag=$userDraggedListThisSession " +
                "suppress=$suppressEndMarkReadUntilUserScroll evidence=$hasReadEvidence " +
                "dwellMs=${android.os.SystemClock.elapsedRealtime() - sessionRenderedAtMs} " +
                "→ ${if (!(suppressEndMarkReadUntilUserScroll && !hasReadEvidence)) "FIRE" else "hold"}")
        if (suppressEndMarkReadUntilUserScroll && !hasReadEvidence) return
        markedTopicReadAtEnd = true
        themeUseCase.markTopicRead(pageTopicId, reason = "last_page_bottom_reached", source = "native")
        // Тема реально дочитана до конца — клиентская граница больше не нужна: следующее открытие пусть
        // идёт по серверу (getnewpost → первый непрочитанный, если появятся новые посты). Иначе стухшая
        // граница удерживала бы якорь на старом посте.
        readBoundaryStore.clear(pageTopicId)
    }

    private companion object {
        /** `topicOpenSource` values that mean «opened from an external list», not an in-topic link tap.
         *  Reusing the topic tab for one of these must NOT push in-tab Back history — Back should exit the
         *  tab back to the list (search/«Мои сообщения»/избранное/…). Mirrors the source strings set by the
         *  navigation entry points; an in-topic post link uses "link"/"unknown" and is intentionally absent. */
        val EXTERNAL_LIST_OPEN_SOURCES = setOf(
                "search", "search_result",
                "qms", "my_messages", "mymessages",
                "favorites", "favorite", "fav",
                "mentions", "mention",
                "tracker", "tracking",
                "news", "article", "announce",
                "profile", "topics", "forum", "forum_list",
        )

        /** Arm hybrid page prefetch when scrolled within this fraction of a viewport from an edge
         *  (WebView parity: an ~800px pixel threshold rather than an item count). */
        const val HYBRID_PREFETCH_VIEWPORT_FRACTION = 0.75f

        /** A deep page's leading post is the prepended topic hat when its id is at least this much OLDER
         *  (smaller) than the page's next post — the hat is the topic's ancient first post, the page's own
         *  posts are recent and clustered. Large enough to never trip on normal consecutive posts. */
        const val HAT_LEADING_ID_GAP = 1_000_000L

        /** …and the leading gap must also be at least this many times the page's typical intra-post gap,
         *  so a merely-slow topic with large but uniform gaps isn't mistaken for a prepended hat. */
        const val HAT_LEADING_GAP_RATIO = 20L

        /** Font-size pref value that maps to textScale 1.0 (matches the WebView default defaultFontSize). */
        const val REFERENCE_FONT_SIZE = 16f

        /** Minimum horizontal travel (dp) for a page-swipe drag to register on release. Deliberately far
         *  above the claim threshold: a short lateral wobble during a vertical scroll must never turn a page. */
        const val SWIPE_MIN_DISTANCE_DP = 110f

        /** Per-frame scroll delta (px) beyond which the FAB direction arrow flips. */
        const val SCROLL_HIDE_THRESHOLD = 8

        /** Idle delay after which the smart button auto-hides (appears again on the next scroll). */
        const val FAB_AUTO_HIDE_MS = 2500L

        /** После отправки ответа: свой пост не дальше стольких элементов под нижней кромкой → плавный
         *  доскролл (smoothScrollToPosition); дальше — мгновенный прыжок, чтобы анимация не тянулась. */
        const val POSTED_SMOOTH_SCROLL_MAX_ITEMS = 8

        /** onSaveInstanceState keys for restore-scroll «где остановился» / устойчивость состояния. */
        private const val STATE_RESTORE_POST_ID = "native_topic_restore_post_id"
        private const val STATE_RESTORE_OFFSET = "native_topic_restore_offset"
        private const val STATE_RESTORE_BAR_PAGE = "native_topic_restore_bar_page"

        private const val MENU_SEARCH = 0x4E01
        private const val MENU_REFRESH = 0x4E03
        private const val MENU_CREATE = 0x4E06
        private const val MENU_POLL = 0x4E07
        private const val MENU_HAT = 0x4E08
        private const val MENU_OVERFLOW = 0x4E0C
    }
}
