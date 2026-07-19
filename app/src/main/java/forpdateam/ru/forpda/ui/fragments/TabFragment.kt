package forpdateam.ru.forpda.ui.fragments
import forpdateam.ru.forpda.ui.applyM3RefreshStyle
import forpdateam.ru.forpda.BuildConfig

import forpdateam.ru.forpda.common.getColorFromAttr
import android.os.Bundle
import android.content.res.ColorStateList
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import timber.log.Timber
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.*
import com.google.android.material.chip.ChipGroup

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.fragment.app.Fragment

import android.graphics.Color
import android.view.ViewOutlineProvider

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.FragmentBaseBinding
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp48
import forpdateam.ru.forpda.ui.tuneForListPerformance
import com.google.android.material.R as MaterialR
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.fragments.MessagePanelHelper
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.ScrollAwareFABBehavior
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.imageview.ShapeableImageView
import forpdateam.ru.forpda.common.applyForumAvatarShape
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.applyTopBarPlaqueChrome
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.ui.createTopAppBarShapeDrawable
import forpdateam.ru.forpda.ui.resolveTopAppBarShapeStyle
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import androidx.annotation.AttrRes

/**
 * Created by radiationx on 07.08.16.
 */
@AndroidEntryPoint
open class TabFragment : Fragment() {
    @Inject lateinit var countersHolder: CountersHolder
    @Inject lateinit var dimensionsProvider: DimensionsProvider
    @Inject lateinit var networkState: NetworkStateProvider
    @Inject lateinit var topicPreferencesHolder: TopicPreferencesHolder



    val configuration = TabConfiguration()

    private var titleText: String? = null
    private var tabTitleText: String? = null
    private var subtitleText: String? = null

    protected var _binding: FragmentBaseBinding? = null
    protected val binding get() = _binding!!

    // Legacy field accessors for backward compatibility with subclasses
    protected val toolbarProgress: ProgressBar get() = binding.toolbarProgress
    protected val fragmentContainer: RelativeLayout get() = binding.fragmentContainer
    /** Нижняя зона под [MessagePanel] (тема/QMS); не внутри coordinator — иначе IME ломает измерение панели. */
    protected val messagePanelHost: FrameLayout get() = binding.messagePanelHost
    protected val fragmentContent: ViewGroup get() = binding.fragmentContent
    protected val additionalContent: ViewGroup get() = binding.additionalContent
    protected val contentProgress: com.google.android.material.loadingindicator.LoadingIndicator get() = binding.contentProgress
    protected val titlesWrapper: LinearLayout get() = binding.toolbarTitlesWrapper
    protected val coordinatorLayout: androidx.coordinatorlayout.widget.CoordinatorLayout get() = binding.coordinatorLayout
    protected val appBarLayout: AppBarLayout get() = binding.appbarLayout
    protected val toolbarLayout: CollapsingToolbarLayout get() = binding.toolbarLayout
    protected val toolbar: Toolbar get() = binding.toolbar
    protected val toolbarBackground: ImageView get() = binding.toolbarImageBackground
    protected val toolbarImageView: ImageView get() = binding.toolbarImageIcon
    protected val toolbarTitleView: TextView get() = binding.toolbarTitle
    protected val toolbarSubtitleView: TextView get() = binding.toolbarSubtitle
    /** M3 single-select chip-фильтр категорий (бывший toolbarSpinner). */
    protected val toolbarFilterChips: ChipGroup get() = binding.toolbarFilterChips
    /** Контейнер горизонтального скролла chip-фильтра; его видимость и есть «фильтр показан». */
    protected val toolbarFilterScroll: View get() = binding.toolbarFilterScroll
    /** Невидимая растяжка справа от chip-фильтра: держит его у левого края блока действий без «улёта» к поиску. */
    protected val toolbarSpinnerEndSpacer: View get() = binding.toolbarSpinnerEndSpacer
    protected lateinit var viewFragment: View
    protected val fab: FloatingActionButton get() = binding.fab
    protected lateinit var contentController: ContentController
    protected val preLpShadow: View get() = binding.toolbarShadowPrelp


    protected open fun isShadowVisible(): Boolean = true

    /** Main sections use flat AppBar chrome: status bar and toolbar merge into one color block. */
    protected open fun useFlatTopBarChrome(): Boolean =
            topBarSurfaceColorAttr() == R.attr.main_toolbar_accent_surface

    /**
     * Плашка AppBar с горизонтальным inset ([R.dimen.top_bar_plaque_horizontal_inset]) и скруглением как у списков / Шапки темы в WebView.
     */
    protected open fun useTopBarHorizontalPlaqueInset(): Boolean = !useFlatTopBarChrome()

    /**
     * Скругление верхней плашки (inset или full-width). По умолчанию Material 3 — скругление при inset ([TopAppBarShapeStyle.INSET_PLAQUE]);
     * подкласс может вернуть false для прямоугольной плашки ([TopAppBarShapeStyle.INSET_PLAQUE_RECT] / [TopAppBarShapeStyle.FULL_WIDTH_RECT]).
     */
    protected open fun useTopBarRoundedCorners(): Boolean = !useFlatTopBarChrome()

    /**
     * Compact toolbar + pagination headers touch list content below. Rounded lower corners expose the list background
     * as a gray strip at the left/right arcs in light themes, so those screens can keep only the top rounding.
     */
    protected open fun useTopBarRoundedBottomCorners(): Boolean = !useFlatTopBarChrome()

    /**
     * Screens that place compact pagination inside the AppBar use the AppBar plaque itself as the separator.
     * The legacy 2dp shadow is a second divider and remains visible as a gray line under the unified header.
     */
    protected open fun useCompactToolbarPaginationChrome(): Boolean = false

    /** Main AppBar/status-bar chrome should look like one flat color block. */
    protected open fun syncStatusBarWithTopBar(): Boolean = true

    @AttrRes
    open fun topBarSurfaceColorAttr(): Int = R.attr.chrome_plane_background

    @AttrRes
    protected open fun pinnedToolbarUnderlayColorAttr(): Int = R.attr.background_for_lists

    /**
     * Разрешённый цвет верхней плашки. Для «плоского» хрома главных разделов
     * (main_toolbar_accent_surface) идёт через [ChromeCanvas]: под Material You
     * (SYSTEM light/dark) — динамический тон обоев, единый со всем полотном
     * (фон страниц/нижний бар/статус-бар); в статических палитрах — ровно
     * значение атрибута (no-op). Прочие плашки (chrome_plane_background)
     * остаются на своих атрибутах.
     */
    protected fun topBarSurfaceColor(): Int {
        val attr = topBarSurfaceColorAttr()
        return if (attr == R.attr.main_toolbar_accent_surface) {
            requireContext().chromeCanvasColor(attr)
        } else {
            requireContext().getColorFromAttr(attr)
        }
    }

    private val mainActivity: MainActivity
        get() = activity as MainActivity

    private var attachedWebView: ExtendedWebView? = null
    private var messagePanelHelper: MessagePanelHelper? = null

    fun getTitle(): String {
        return titleText ?: configuration.defaultTitle
    }

    fun setTitle(newTitle: String?) {
        this.titleText = newTitle
        if (tabTitleText == null) {
            mainActivity.tabNavigator.notifyUpdate(this)
        }
        toolbarTitleView.text = getTitle()
    }

    protected fun getSubtitle(): String? {
        return subtitleText
    }

    fun setSubtitle(newSubtitle: String?) {
        this.subtitleText = newSubtitle
        if (subtitleText == null) {
            if (toolbarSubtitleView.visibility != View.GONE)
                toolbarSubtitleView.visibility = View.GONE
        } else {
            if (toolbarSubtitleView.visibility != View.VISIBLE)
                toolbarSubtitleView.visibility = View.VISIBLE
            toolbarSubtitleView.text = getSubtitle()
        }
    }

    fun getTabTitle(): String {
        return tabTitleText ?: getTitle()
    }

    fun setTabTitle(tabTitle: String) {
        this.tabTitleText = tabTitle
        mainActivity.tabNavigator.notifyUpdate(this)
    }

    //False - можно закрывать
    //True - еще нужно что-то сделать, не закрывать
    @CallSuper
    open fun onBackPressed(): Boolean {
        Timber.d("onBackPressed %s", this)
        return false
    }

    /**
     * Side-effect-free: перехватит ли фрагмент «назад» ПРЯМО СЕЙЧАС (открытый
     * диалог/панель/selection/поиск/черновик) — read-only зеркало условий
     * [onBackPressed]. Нужно [forpdateam.ru.forpda.ui.activities.MainActivity],
     * чтобы точно знать, выйдет ли следующий «назад» из приложения, и показать
     * предиктивную анимацию отслаивания к лаунчеру (Android 13+) ТОЛЬКО когда
     * перехватывать нечего. Реализации ДОЛЖНЫ быть без сайд-эффектов (только
     * чтение состояния) — иначе можно исказить back-логику. Дефолт false
     * (обычный список без внутреннего состояния — «назад» с корневой вкладки
     * выходит из приложения).
     */
    open fun hasBackHandling(): Boolean = false

    @CallSuper
    open fun onToolbarNavigationClick(): Boolean {
        return false
    }

    open fun hideKeyboard() {
        mainActivity.hideKeyboard()
    }

    open fun showKeyboard(view: View) {
        mainActivity.showKeyboard(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            val msg = "TabFragment.onCreate: subscribing ${this.javaClass.simpleName}, tag=${this.tag}"
            Timber.d(msg)
            mainActivity.tabNavigator.subscribe(this)
            val msg2 = "TabFragment.onCreate: subscribed, tabs count=${mainActivity.tabNavigator.subscribersFlow.value.size}"
            Timber.d(msg2)
            Timber.d("onCreate %s", this)
        } else {
            mainActivity.tabNavigator.subscribe(this)
        }

        savedInstanceState?.also {
            titleText = it.getString(BUNDLE_TITLE)
            subtitleText = it.getString(BUNDLE_SUBTITLE)
            tabTitleText = it.getString(BUNDLE_TAB_TITLE)
            configuration.isAlone = it.getBoolean(BUNDLE_CONFIG_ALONE, configuration.isAlone)
            configuration.isMenu = it.getBoolean(BUNDLE_CONFIG_MENU, configuration.isMenu)
        }

        arguments?.also {
            titleText = it.getString(ARG_TITLE)
            subtitleText = it.getString(ARG_SUBTITLE)
        }
    }

    @CallSuper
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentBaseBinding.inflate(inflater, container, false)
        viewFragment = binding.root
        contentController = ContentController(binding.contentProgress, binding.additionalContent, binding.fragmentContent)
        // ChromeCanvas: coordinator и fragment_content в XML стоят на статическом
        // ?attr/colorSurfaceContainerLowest (инфлейтер не умеет в рантайм-блендинг).
        // Под Material You перекрашиваем их в динамическое полотно обоев сразу после
        // инфляции; вне MY fallback = тот же Lowest — пиксельный no-op.
        val canvas = requireContext().chromeCanvasColor(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        binding.coordinatorLayout.setBackgroundColor(canvas)
        binding.fragmentContent.setBackgroundColor(canvas)
        return viewFragment
    }

    @CallSuper
    override fun onDestroyView() {
        onDestroyViewBinding()
        super.onDestroyView()
        _binding = null
    }

    protected open fun onDestroyViewBinding() {
        // Override in subclasses to clean their bindings
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.post { this.updateToolbarShadow() }

        // Базовая отрисовка AppBar: делаем непрозрачным, чтобы при hide/show на скролле
        // не “просвечивал” список и визуально не наезжал прозрачной плашкой.
        // Фон-картинку (toolbar_image_background) по умолчанию скрываем — она нужна точечно (профиль и т.п.).
        appBarLayout.applyTopBarPlaqueChrome(
                useHorizontalInset = useTopBarHorizontalPlaqueInset(),
                roundedCorners = useTopBarRoundedCorners(),
                roundedBottomCorners = useTopBarRoundedBottomCorners(),
                surfaceColorAttr = topBarSurfaceColorAttr(),
        )
        appBarLayout.isClickable = true
        toolbarLayout.isClickable = true
        // CollapsingToolbarLayout рисует content/status scrim прямоугольником — непрозрачный цвет
        // маскирует скругление MaterialShapeDrawable на AppBarLayout.
        toolbarLayout.setBackgroundColor(Color.TRANSPARENT)
        toolbarLayout.setContentScrimColor(Color.TRANSPARENT)
        toolbarLayout.setStatusBarScrimColor(Color.TRANSPARENT)
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        toolbarBackground.visibility = View.GONE

        toolbarTitleView.apply {
            ellipsize = TextUtils.TruncateAt.MARQUEE
            setHorizontallyScrolling(true)
            marqueeRepeatLimit = 3
            isSelected = true
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp16)
        }


        toolbar.apply {
            applyPopupTheme()
            // Scroll-to-top только по полосе заголовка: слушатель на весь Toolbar перехватывал тачи у дочерних
            // контролов (Spinner категорий DevDB — стрелка «вниз» слева от текста не открывала список).
            if (this@TabFragment is TabTopScroller) {
                val topScroller = this@TabFragment as TabTopScroller
                this@TabFragment.titlesWrapper.apply {
                    isClickable = true
                    setOnClickListener { topScroller.toggleScrollTop() }
                }
            }

            val isToggle = configuration.isAlone || configuration.isMenu
            if (!isToggle) {
                setNavigationOnClickListener {
                    if (!onToolbarNavigationClick()) {
                        mainActivity.removeTabListener.invoke(it)
                    }
                }
                setNavigationIcon(R.drawable.ic_toolbar_arrow_back)
                navigationContentDescription = getString(R.string.close_tab)
                contentInsetEndWithActions = 0
                contentInsetStartWithNavigation = 0
                setContentInsetsRelative(0, contentInsetEnd)
            }
            // CRITICAL: Force tint for navigation icon and overflow icon
            tintToolbarIcons()
        }
        // Dynamic override поверх XML-concrete @color/collapsing_toolbar_title_color.
        // XML-атрибуты app:collapsedTitleTextColor / app:expandedTitleTextColor в
        // fragment_base.xml / activity_updater.xml оставлены конкретными, чтобы
        // CollapsingToolbarLayout.<init> не падал на TYPE_ATTRIBUTE (см. 7f1de68).
        // Здесь накладываем ?attr/colorOnSurface.
        tintCollapsingToolbarTitle()

        setTitle(titleText)
        setSubtitle(subtitleText)
        addBaseToolbarMenu(toolbar.menu)
        // Tint menu item icons after menu is created
        tintMenuItems(toolbar.menu)

        // Initialize messagePanelHelper BEFORE collecting dimensionsFlow
        // to avoid UninitializedPropertyAccessException on immediate emission
        messagePanelHelper = MessagePanelHelper(messagePanelHost)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dimensionsProvider.dimensionsFlow.collect { dimensions ->
                    updateDimens(dimensions)
                }
            }
        }

        (toolbarImageView as? ShapeableImageView)
                ?.applyForumAvatarShape(topicPreferencesHolder.getCircleAvatars())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                topicPreferencesHolder.observeCircleAvatarsFlow().collect { circle ->
                    (toolbarImageView as? ShapeableImageView)?.applyForumAvatarShape(circle)
                }
            }
        }

        // view.post: к моменту выполнения фрагмент мог быть уничтожен (_binding=null
        // в onDestroyView) → доступ к fragmentContainer (binding!!) ронял NPE.
        view.post { _binding?.fragmentContainer?.let { ViewCompat.requestApplyInsets(it) } }

        // MainActivity объявляет configChanges="orientation|screenSize", поэтому при повороте
        // фрагменты не пересоздаются. Горизонтальные инсеты (боковой navbar в landscape при
        // 3-кнопочной навигации) применялись только в updateDimens по эмиссии dimensionsFlow —
        // а к моменту поворота dimensionsFlow уже не переэмитит с новыми insets, и справа/слева
        // остаётся «залипшая» цветная полоса шириной с бывший боковой navbar. Слушатель прямо на
        // fragmentContainer переприменяет L/R padding из СВЕЖИХ insets при каждом их изменении
        // (в т.ч. повороте). Подклассы со своим слушателем на fragmentContainer (NativeTopic/EditPost)
        // вызывают super.onViewCreated раньше и штатно переопределяют этот listener.
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { _, insets ->
            applyHorizontalInsets(insets)
            insets
        }
        syncToolbarSpinnerEndSpacer()
    }

    private fun Toolbar.applyPopupTheme() {
        val outValue = TypedValue()
        if (requireContext().theme.resolveAttribute(R.attr.popup_overlay, outValue, true) && outValue.resourceId != 0) {
            setPopupTheme(outValue.resourceId)
        }
    }

    /**
     * Когда заголовки скрыты, а категории — [Spinner], растяжка заполняет ширину справа, чтобы выпадающий список
     * не визуально «прилипал» к иконке поиска и оставался слева от области меню.
     */
    protected fun syncToolbarSpinnerEndSpacer() {
        if (_binding == null) return
        toolbarSpinnerEndSpacer.visibility = when {
            toolbarFilterScroll.visibility == View.VISIBLE && titlesWrapper.visibility != View.VISIBLE ->
                View.VISIBLE
            else -> View.GONE
        }
    }

    /**
     * Поднимать message_panel_host через IME bottomMargin только когда там активна компактная панель
     * (тема, QMS, компактный режим EditPost). Иначе — [MessagePanelHelper.resetImeInsets].
     */
    protected open fun shouldApplyMessagePanelImeInsets(): Boolean = true

    /**
     * Some full-height scroll surfaces (Theme WebView) need to render under the app bottom bar
     * and handle the overlap inside their own content. Otherwise parent bottom padding is visible
     * as a theme-colored band after the last item.
     */
    open fun shouldDrawBehindBottomNav(): Boolean = false

    open fun onBottomChromePaddingChanged(padding: Int) = Unit

    protected open fun messagePanelBaseBottomMargin(): Int = 0

    /** Синхронизировать margin хоста с текущим [DimensionsProvider] и [shouldApplyMessagePanelImeInsets]. */
    protected fun syncMessagePanelImeWithDimensions() {
        if (!isAdded) return
        val dimensions = dimensionsProvider.getDimensions()
        if (shouldApplyMessagePanelImeInsets()) {
            messagePanelHelper?.updateImeInsets(dimensions, messagePanelBaseBottomMargin())
        } else {
            messagePanelHelper?.resetImeInsets()
        }
    }

    /**
     * Боковые (L/R) отступы под системные бары и вырез экрана. Вынесено, чтобы
     * OnApplyWindowInsetsListener на [fragmentContainer] и [updateDimens] применяли одну и ту же
     * формулу из одних и тех же insets. Верхний padding fragmentContainer всегда 0 (верхний inset
     * задаёт activity), поэтому сохраняем текущий top/bottom как есть.
     */
    private fun applyHorizontalInsets(insets: WindowInsetsCompat?) {
        if (_binding == null) return
        val sys = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val cut = insets?.getInsets(WindowInsetsCompat.Type.displayCutout())
        val left = max(sys?.left ?: 0, cut?.left ?: 0)
        val right = max(sys?.right ?: 0, cut?.right ?: 0)
        if (fragmentContainer.paddingLeft != left || fragmentContainer.paddingRight != right) {
            fragmentContainer.setPadding(
                left,
                fragmentContainer.paddingTop,
                right,
                fragmentContainer.paddingBottom
            )
        }
    }

    private fun updateDimens(dimensions: DimensionHelper.Dimensions) {
        if (!isAdded || view == null || isHidden) return
        val wi = ViewCompat.getRootWindowInsets(fragmentContainer)
        val sys = wi?.getInsets(WindowInsetsCompat.Type.systemBars())
        val cut = wi?.getInsets(WindowInsetsCompat.Type.displayCutout())
        val left = max(sys?.left ?: 0, cut?.left ?: 0)
        val right = max(sys?.right ?: 0, cut?.right ?: 0)
        // Нижний отступ под нижнее меню приложения задаёт MainActivity (fragments_container).
        // Повторный navBottom здесь давал просвет между панелью ввода/клавиатурой и таббаром.
        // Нижний padding самого fragmentContainer не обнуляем здесь: его выставляет AdvancedPopup
        // (BBCode/смайлы вместо IME). Иначе каждый emit dimensionsFlow сбрасывал бы его — гонка
        // с компактным редактором и «полноэкранный» разрыв между полем ввода и PopupWindow.

        // На части устройств/OEM adjustResize не "поднимает" нижние вьюхи, IME просто накладывается.
        // Поэтому поднимаем хост MessagePanel (theme/QMS) через bottomMargin = IME inset.
        // Если IME inset отсутствует, но keyboardHeight уже рассчитан — используем его.
        // EditPost: полноэкранный редактор в fragment_content; компактный — в message_panel_host.
        // При GONE у компакта нельзя оставлять bottomMargin у хоста — coordinator остаётся «укороченным»
        // на высоту IME (пустая полоса вместо клавиатуры).
        if (shouldApplyMessagePanelImeInsets()) {
            messagePanelHelper?.updateImeInsets(dimensions, messagePanelBaseBottomMargin())
        } else {
            messagePanelHelper?.resetImeInsets()
        }
        syncTopBarStatusChromeWithInsets()

        if (!configuration.fitSystemWindow) {
            // Верхний inset задаёт MainActivity (fragments_container paddingTop). Не дублируем
            // dimensions.statusBar здесь — иначе двойной отступ под статус-бар (как на «Избранном»).
            if (fragmentContainer.paddingLeft != left || fragmentContainer.paddingTop != 0 ||
                fragmentContainer.paddingRight != right) {
                fragmentContainer.setPadding(
                    left,
                    0,
                    right,
                    fragmentContainer.paddingBottom
                )
            }
            return
        }
        val params = toolbar.layoutParams as CollapsingToolbarLayout.LayoutParams
        // Отступ сверху уже учтён у activity (fragments_container paddingTop в updateDimens).
        if (params.topMargin != 0) {
            params.topMargin = 0
            toolbar.layoutParams = params
        }
        if (fragmentContainer.paddingLeft != left || fragmentContainer.paddingTop != 0 ||
            fragmentContainer.paddingRight != right) {
            fragmentContainer.setPadding(
                left,
                0,
                right,
                fragmentContainer.paddingBottom
            )
        }
    }

    private fun hasToolbarPaginationHeader(): Boolean {
        val paginationView = toolbarLayout.findViewById<View>(R.id.tabs) ?: return false
        return paginationView.parent === toolbarLayout
    }

    protected fun clearToolbarPaginationSubtitle() {
        if (hasToolbarPaginationHeader()) {
            setSubtitle(null)
        }
    }

    protected fun baseInflateFragment(inflater: LayoutInflater, @LayoutRes res: Int) {
        inflater.inflate(res, fragmentContent, true)
    }

    @JvmOverloads
    protected fun setListsBackground(view: View = coordinatorLayout) {
        // Align with settings / grouped lists: page tint = background_base, elevated rows = cards_background (plates).
        // ChromeCanvas: под Material You полотно страницы тонируется обоями (единый тон с шапкой/низом);
        // вне MY fallback = colorSurfaceContainerLowest — прежнее поведение без изменений.
        view.setBackgroundColor(requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
    }

    @JvmOverloads
    protected fun setCardsBackground(view: View = coordinatorLayout) {
        view.setBackgroundColor(requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
    }

    protected fun ensureOpaquePinnedToolbarUnderlay() {
        val tag = R.id.toolbar_opaque_underlay
        val existing = toolbarLayout.findViewWithTag<View>(tag)
        val underlay = existing ?: View(requireContext()).apply {
            this.tag = tag
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = CollapsingToolbarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            }
            toolbarLayout.addView(this, 0)
        }
        val hasGroupedPaginationHeader = hasToolbarPaginationHeader()
        val style = resolveTopAppBarShapeStyle(
                useHorizontalInset = useTopBarHorizontalPlaqueInset(),
                roundedCorners = useTopBarRoundedCorners(),
                roundedBottomCorners = if (hasGroupedPaginationHeader) true else useTopBarRoundedBottomCorners(),
        )
        val plateFill = topBarSurfaceColor()
        // Подложка под pinned-тулбар: для flat/grouped хрома — тот же цвет плашки;
        // иначе — полотно списка (под MY тонируется ChromeCanvas, вне MY = атрибут).
        val underlayFill = if (hasGroupedPaginationHeader || useFlatTopBarChrome()) {
            plateFill
        } else {
            requireContext().chromeCanvasColor(pinnedToolbarUnderlayColorAttr())
        }
        val usesMainToolbarSurface = topBarSurfaceColorAttr() == R.attr.main_toolbar_accent_surface
        val drawAppBarStroke = !usesMainToolbarSurface
        val groupedStrokeWidthAttr = if (hasGroupedPaginationHeader && usesMainToolbarSurface) {
            R.attr.main_toolbar_stroke_width
        } else {
            R.attr.list_plate_stroke_width
        }
        val groupedStrokeColorAttr = if (hasGroupedPaginationHeader && usesMainToolbarSurface) {
            R.attr.main_toolbar_stroke_color
        } else {
            R.attr.list_plate_stroke_color
        }
        val underlayBackground = requireContext().createTopAppBarShapeDrawable(
                underlayFill,
                style,
                strokeWidthAttr = groupedStrokeWidthAttr,
                strokeColorAttr = groupedStrokeColorAttr,
                drawStroke = drawAppBarStroke,
        )
        appBarLayout.background = underlayBackground
        underlay.background = requireContext().createTopAppBarShapeDrawable(
                underlayFill,
                style,
                strokeWidthAttr = groupedStrokeWidthAttr,
                strokeColorAttr = groupedStrokeColorAttr,
                drawStroke = drawAppBarStroke,
        )
        underlay.outlineProvider = ViewOutlineProvider.BACKGROUND
        underlay.clipToOutline = useTopBarHorizontalPlaqueInset()

        if (hasGroupedPaginationHeader) {
            appBarLayout.background = requireContext().createTopAppBarShapeDrawable(
                    plateFill,
                    style,
                    strokeWidthAttr = if (usesMainToolbarSurface) {
                        R.attr.main_toolbar_stroke_width
                    } else {
                        R.attr.list_plate_stroke_width
                    },
                    strokeColorAttr = if (usesMainToolbarSurface) {
                        R.attr.main_toolbar_stroke_color
                    } else {
                        R.attr.list_plate_stroke_color
                    },
                    drawStroke = drawAppBarStroke,
            )
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            toolbar.clipToOutline = false
            return
        }

        toolbar.background = requireContext().createTopAppBarShapeDrawable(
                plateFill,
                style,
                strokeWidthAttr = if (usesMainToolbarSurface) {
                    R.attr.main_toolbar_stroke_width
                } else {
                    R.attr.list_plate_stroke_width
                },
                strokeColorAttr = if (usesMainToolbarSurface) {
                    R.attr.main_toolbar_stroke_color
                } else {
                    R.attr.list_plate_stroke_color
                },
                drawStroke = drawAppBarStroke,
        )
        toolbar.outlineProvider = ViewOutlineProvider.BACKGROUND
        toolbar.clipToOutline = useTopBarHorizontalPlaqueInset()
    }

    protected fun pinStaticOpaqueToolbar() {
        clearToolbarScrollFlags()
        appBarLayout.setExpanded(true, false)
        ensureOpaquePinnedToolbarUnderlay()
        applyTopBarStatusChrome()
        appBarLayout.bringToFront()

        val toolbarChromeElevation = if (useFlatTopBarChrome()) {
            resources.getDimension(R.dimen.dp0)
        } else {
            resources.getDimension(R.dimen.dp4)
        }
        ViewCompat.setElevation(appBarLayout, toolbarChromeElevation)
        ViewCompat.setTranslationZ(appBarLayout, toolbarChromeElevation)

        (toolbar.layoutParams as? CollapsingToolbarLayout.LayoutParams)?.let { params ->
            if (params.collapseMode != CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN) {
                params.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
                toolbar.layoutParams = params
            }
        }
    }

    protected fun updateToolbarShadow() {
        // onViewCreated постит это через view.post{}: к моменту выполнения view
        // мог быть уничтожен (быстрая навигация/пересоздание) и _binding обнулён
        // в onDestroyView → обращение к preLpShadow (binding!!) роняло NPE.
        if (_binding == null) return
        val isVisible = !useFlatTopBarChrome() && !useCompactToolbarPaginationChrome() && isShadowVisible()
        val targetVisibility = if (isVisible) View.VISIBLE else View.GONE
        if (preLpShadow.visibility != targetVisibility) {
            preLpShadow.visibility = targetVisibility
        }
    }

    @CallSuper
    protected open fun addBaseToolbarMenu(menu: Menu) {

    }

    // Helper methods for toolbar icon tinting - can be called when menu changes
    protected fun tintToolbarIcons() {
        // Toolbar navigationIcon / overflowIcon.
        // На widget-ctor-небезопасном слоте ?icon_toolbar, поэтому красим
        // напрямую из ?attr/colorOnSurface: в Material You SURFACE это
        // динамический onSurface от обоев; в остальных палитрах — статический
        // onSurface базовой темы.
        val iconColor = requireContext().getColorFromAttr(R.attr.colorOnSurface)
        toolbar.navigationIcon?.mutate()?.setTint(iconColor)
        toolbar.overflowIcon?.mutate()?.setTint(iconColor)
        // title/subtitle — динамический текст. На widget-ctor-небезопасном
        // слоте (Toolbar.<init> читает titleTextColor через TintTypedArray),
        // поэтому XML в fragment_base.xml / activity_updater.xml держит конкретный
        // @color/toolbar_title_color; здесь после инфлята накладываем dynamic
        // color из Material You SURFACE, если она активна.
        val textColor = requireContext().getColorFromAttr(R.attr.colorOnSurface)
        toolbar.setTitleTextColor(textColor)
        toolbar.setSubtitleTextColor(textColor)
    }

    /**
     * Восстановить dynamic tracking для CollapsingToolbarLayout title.
     * XML-override `app:collapsedTitleTextColor` / `app:expandedTitleTextColor` в
     * fragment_base.xml / activity_updater.xml удерживает конкретный
     * @color/collapsing_toolbar_title_color, чтобы избежать crash в
     * CollapsingToolbarLayout.<init> (см. комментарии в values/styles.xml
     * MaterialYouSurface и коммит 7f1de68). После инфлята накладываем
     * ?attr/colorOnSurface — в Material You SURFACE это
     * динамический onSurface от обоев; в остальных палитрах —
     * @color/light_default_text_color / @color/dark_default_text_color.
     */
    protected fun tintCollapsingToolbarTitle() {
        if (!isAdded) return
        val textColor = requireContext().getColorFromAttr(R.attr.colorOnSurface)
        toolbarLayout.setCollapsedTitleTextColor(textColor)
        toolbarLayout.setExpandedTitleColor(textColor)
    }

    protected fun tintMenuItems(menu: Menu) {
        val iconColor = requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.mutate()?.setTint(iconColor)
        }
    }

    @CallSuper
    protected open fun refreshToolbarMenuItems(enable: Boolean) {
        // Re-apply tint to all menu items when menu is refreshed
        if (enable) {
            tintMenuItems(toolbar.menu)
        }
    }

    protected open fun initFabBehavior() {
        val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val behavior = ScrollAwareFABBehavior(fab.context, null)
        params.behavior = behavior
        fab.requestLayout()
    }

    protected fun refreshLayoutStyle(refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout) {
        val progressColor = requireContext().getColorFromAttr(R.attr.colorAccent)
        refreshLayout.applyM3RefreshStyle()
        contentProgress.setIndicatorColor(progressColor)
        toolbarProgress.indeterminateTintList = ColorStateList.valueOf(progressColor)
        applySwipeRefreshIndicatorBelowToolbar(refreshLayout)
    }

    /**
     * AppBar рисуется поверх контента ([bringToFront]); без смещения круг SwipeRefresh уходит под тулбар.
     */
    protected fun applySwipeRefreshIndicatorBelowToolbar(refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout) {
        fun applyOffset() {
            if (!isAdded) return
            val progressDiameter = resources.getDimensionPixelSize(R.dimen.dp48)
            val gap = resources.getDimensionPixelSize(R.dimen.dp8)
            val toolbarHeight = toolbar.height.takeIf { it > 0 }
                    ?: resources.getDimensionPixelSize(R.dimen.dp48)
            val end = toolbarHeight + gap
            val start = (end - progressDiameter).coerceAtLeast(0)
            refreshLayout.setProgressViewOffset(true, start, end)
        }
        if (toolbar.height > 0) {
            applyOffset()
        } else {
            toolbar.post { applyOffset() }
        }
    }

    protected fun tuneListRecyclerView(recyclerView: RecyclerView) {
        recyclerView.tuneForListPerformance()
    }

    protected fun refreshLayoutLongTrigger(refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout) {
        refreshLayout.setDistanceToTriggerSync(dp48 * 3)
        refreshLayout.setProgressViewEndTarget(false, dp48 * 3)
    }

    protected fun setScrollFlagsExitUntilCollapsed() {
        setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED)
    }

    protected fun setScrollFlagsEnterAlways() {
        setScrollFlags(
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        )
    }

    protected fun setScrollFlagsEnterAlwaysCollapsed() {
        setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED)
    }

    protected fun clearToolbarScrollFlags() {
        setScrollFlags(0)
    }

    protected fun setScrollFlags(flags: Int) {
        val params = toolbarLayout.layoutParams as AppBarLayout.LayoutParams
        if (params.scrollFlags != flags) {
            params.scrollFlags = flags
            toolbarLayout.layoutParams = params
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(BUNDLE_TITLE, titleText)
        outState.putString(BUNDLE_SUBTITLE, subtitleText)
        outState.putString(BUNDLE_TAB_TITLE, tabTitleText)
        outState.putBoolean(BUNDLE_CONFIG_ALONE, configuration.isAlone)
        outState.putBoolean(BUNDLE_CONFIG_MENU, configuration.isMenu)
    }


    fun findViewById(@IdRes id: Int): View {
        return binding.root.findViewById(id)
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            mainActivity.updateDimens(dimensionsProvider.getDimensions())
            onResumeOrShow()
        }
        Timber.d("onResume %s", this)
    }

    // onDestroyView() остаётся у наследников; базовый cleanup не нужен.


    override fun onPause() {
        super.onPause()
        onPauseOrHide()
        Timber.d("onPause %s", this)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            onPauseOrHide()
        } else {
            mainActivity.updateDimens(dimensionsProvider.getDimensions())
            onResumeOrShow()
        }
    }

    @CallSuper
    open fun onResumeOrShow() {
        if (BuildConfig.DEBUG) Timber.d("onResumeOrShow %s", this)
        updateStatusBar()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            attachedWebView?.onResume()
        }
    }

    @CallSuper
    open fun onPauseOrHide() {
        if (BuildConfig.DEBUG) Timber.d("onPauseOrHide %s", this)
        hideKeyboard()
        attachedWebView?.onPause()
    }

    private fun updateStatusBar() {
        if (syncStatusBarWithTopBar()) {
            applyTopBarStatusChrome()
        } else {
            removeStatusBarUnderlay()
            val defaultSb = MainActivity.getDefaultLightStatusBar(mainActivity)
            MainActivity.setLightStatusBar(mainActivity, defaultSb)
        }
    }

    protected fun applyTopBarStatusChrome() {
        if (!isAdded || activity == null) return
        val topBarColor = topBarSurfaceColor()
        toolbarLayout.setStatusBarScrimColor(topBarColor)
        if (useFlatTopBarChrome()) {
            applyStatusBarUnderlay(topBarColor)
        } else {
            removeStatusBarUnderlay()
        }
        SystemBarAppearance.syncStatusBar(requireActivity(), topBarColor)
    }

    private fun syncTopBarStatusChromeWithInsets() {
        if (syncStatusBarWithTopBar()) {
            applyTopBarStatusChrome()
        }
    }

    private fun applyStatusBarUnderlay(topBarColor: Int) {
        val rootInsets = activity
                ?.findViewById<View>(android.R.id.content)
                ?.let(ViewCompat::getRootWindowInsets)
        val statusInset = rootInsets
                ?.getInsets(WindowInsetsCompat.Type.statusBars())
                ?.top ?: 0
        val topInset = maxOf(
                statusInset,
                activity?.findViewById<View>(R.id.fragments_container)?.paddingTop ?: 0,
                dimensionsProvider.getDimensions().statusBar
        )
        val content = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        val underlay = content.findViewWithTag<View>(STATUS_BAR_UNDERLAY_TAG)
                ?: View(requireContext()).apply {
                    tag = STATUS_BAR_UNDERLAY_TAG
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    content.addView(this)
                }
        if (topInset <= 0) {
            underlay.visibility = View.GONE
            return
        }
        underlay.visibility = View.VISIBLE
        underlay.setBackgroundColor(topBarColor)
        underlay.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                topInset
        ).apply {
            gravity = Gravity.TOP
        }
        underlay.bringToFront()
    }

    private fun removeStatusBarUnderlay() {
        val content = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.findViewWithTag<View>(STATUS_BAR_UNDERLAY_TAG)?.let { underlay ->
            content.removeView(underlay)
        }
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        mainActivity.tabNavigator.unsubscribe(this)
        // Correctly release WebView to avoid Activity leak.
        attachedWebView?.let { wv ->
            runCatching {
                wv.removeBaseBridge()
                wv.stopLoading()
                wv.webChromeClient = null
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.destroy()
            }
        }
        attachedWebView = null
        Timber.d("onDestroy %s", this)
        hideKeyboard()
        if (::contentController.isInitialized) {
            contentController.destroy()
        }
    }

    open protected fun attachWebView(webView: ExtendedWebView) {
        this.attachedWebView = webView
    }

    fun runInUiThread(action: Runnable) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            action.run()
        }
    }

    protected fun startRefreshing() {
        contentController.startRefreshing()
    }

    protected fun stopRefreshing() {
        contentController.stopRefreshing()
    }

    open fun setRefreshing(isRefreshing: Boolean) {
        if (isRefreshing)
            startRefreshing()
        else
            stopRefreshing()
    }

    companion object {
        private val LOG_TAG = TabFragment::class.java.simpleName
        private val BUNDLE_PREFIX = "tab_fragment_"
        private val CONFIG_PREFIX = BUNDLE_PREFIX + "config_"
        private val BUNDLE_TITLE = BUNDLE_PREFIX + "title"
        private val BUNDLE_TAB_TITLE = BUNDLE_PREFIX + "tab_title"
        private val BUNDLE_SUBTITLE = BUNDLE_PREFIX + "subtitle"
        private val BUNDLE_CONFIG_MENU = CONFIG_PREFIX + "menu"
        private val BUNDLE_CONFIG_ALONE = CONFIG_PREFIX + "alone"

        const val ARG_TITLE = "TAB_TITLE"
        const val ARG_SUBTITLE = "TAB_SUBTITLE"
        const val ARG_TAB = "TAB_URL"

        const val REQUEST_PICK_FILE = 1228
        const val REQUEST_SAVE_FILE = 1117
        const val REQUEST_STORAGE = 1

        private const val STATUS_BAR_UNDERLAY_TAG = "tab_status_bar_underlay"
    }
}
