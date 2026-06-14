package forpdateam.ru.forpda.ui.views.pagination

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.view.WindowManager
import android.widget.ListView
import android.util.TypedValue
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.common.getToolBarHeight
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.applyTopBarPlaqueChrome
import androidx.annotation.AttrRes
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PaginationHelper(private val context: androidx.fragment.app.FragmentActivity, private val dimensionsProvider: DimensionsProvider) {
    
    companion object {
        private const val TAG_FIRST = 0
        private const val TAG_PREV = 1
        private const val TAG_SELECT = 2
        private const val TAG_NEXT = 3
        private const val TAG_LAST = 4
        private const val ICON_ALPHA_DISABLED = 100
        private const val ICON_ALPHA_ENABLED = 255
        private const val TAG_DETACHED_OPAQUE_UNDERLAY = "pagination_detached_opaque_underlay"
        /** Matches `.theme_bottom_pagination_current { flex: 1.45 1 0; }` in themes.less. */
        private const val SELECT_TAB_WEIGHT = 1.45f
        private const val ICON_TAB_WEIGHT = 1f
    }
    
    private var tabLayoutInToolbar: TabLayout? = null
    private var tabLayoutInList: TabLayout? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** [addInToolbar] вызывается из [onCreateView] до [TabFragment.onViewCreated] / [applyTopBarPlaqueChrome] — margins AppBar ещё 0. */
    private var toolbarPaginationCollapsing: CollapsingToolbarLayout? = null
    private var toolbarPaginationAppBar: AppBarLayout? = null
    private var toolbarPaginationGeometryListener: View.OnLayoutChangeListener? = null
    private var toolbarPaginationOpaqueUnderlay: View? = null
    private var toolbarPaginationIncludeStatusBar: Boolean = false
    private var toolbarPaginationDetachFromChrome: Boolean = false
    private var toolbarPaginationUnifiedHeader: Boolean = true
    private var toolbarPaginationRoundedBottomCorners: Boolean = true
    private var toolbarPaginationFlatHeader: Boolean = false
    private var toolbarPaginationScrollEnabled: Boolean = false
    private var toolbarPaginationEnabled: Boolean = true
    private var toolbarPaginationShowSinglePage: Boolean = false
    private var toolbarPaginationTopOffsetPx: Int? = null
    private var toolbarPaginationElevationPx: Float? = null
    @AttrRes
    private var toolbarPaginationSurfaceColorAttr: Int = R.attr.chrome_plane_background
    private var selectPageInputOnLongClickEnabled: Boolean = false
    
    private var currentPage = 0
    private val tabLayouts = mutableListOf<TabLayout>()
    private var pagination: Pagination? = null
    private var listener: PaginationListener? = null
    private val tabLayoutsWithWeightsListener = mutableSetOf<TabLayout>()
    
    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            if (listener?.onTabSelected(tab) == true || tab.tag == null) return
            when (tab.tag as Int) {
                TAG_FIRST -> firstPage()
                TAG_PREV -> prevPage()
                TAG_NEXT -> nextPage()
                TAG_LAST -> lastPage()
                TAG_SELECT -> selectPageDialog()
            }
        }
        
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        
        override fun onTabReselected(tab: TabLayout.Tab) {
            onTabSelected(tab)
        }
    }
    
    private fun applyToolbarPaginationTopMargin(dimensions: DimensionHelper.Dimensions, includeStatusBarInset: Boolean) {
        tabLayoutInToolbar?.let { tabLayout ->
            val params = tabLayout.layoutParams as CollapsingToolbarLayout.LayoutParams
            val belowToolbarGap = if (toolbarPaginationUnifiedHeader) {
                0
            } else {
                tabLayout.resources.getDimensionPixelSize(R.dimen.pagination_toolbar_below_toolbar_gap)
            }
            val status = if (includeStatusBarInset) dimensions.statusBar else 0
            val toolbarOffset = toolbarPaginationTopOffsetPx ?: tabLayout.context.getToolBarHeight()
            val targetTopMargin = toolbarOffset + status + belowToolbarGap
            if (params.topMargin != targetTopMargin) {
                params.topMargin = targetTopMargin
                tabLayout.layoutParams = params
            }
        }
    }

    private fun syncToolbarPaginationHorizontalInsets(tabLayout: TabLayout) {
        val listInsetPx = if (toolbarPaginationUnifiedHeader) {
            0
        } else {
            tabLayout.resources.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
        }
        val params = tabLayout.layoutParams as? CollapsingToolbarLayout.LayoutParams ?: return
        if (params.marginStart != listInsetPx || params.marginEnd != listInsetPx) {
            params.marginStart = listInsetPx
            params.marginEnd = listInsetPx
            tabLayout.layoutParams = params
        }
    }

    private fun removeToolbarPaginationGeometryListener() {
        toolbarPaginationGeometryListener?.let { listener ->
            toolbarPaginationAppBar?.removeOnLayoutChangeListener(listener)
        }
        toolbarPaginationOpaqueUnderlay?.let { underlay ->
            (underlay.parent as? ViewGroup)?.removeView(underlay)
        }
        toolbarPaginationGeometryListener = null
        toolbarPaginationAppBar = null
        toolbarPaginationCollapsing = null
        toolbarPaginationOpaqueUnderlay = null
    }

    /** TabLayout часто игнорирует tabTextAppearance (bold, maxLines) для вкладки с текстом. */
    private fun applySelectTabPresentation(tabLayout: TabLayout) {
        ensurePaginationTabWeightsListener(tabLayout)
        applyPaginationTabWeights(tabLayout)
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            if (tab.tag != TAG_SELECT) continue
            tab.view.post {
                applyPaginationTabWeights(tabLayout)
                applySelectTabPresentationToView(tab.view)
            }
        }
    }

    private fun applySelectTabPresentationToView(view: View) {
        if (view is TextView) {
            val typeface = view.typeface ?: Typeface.DEFAULT
            view.setTypeface(typeface, Typeface.BOLD)
            view.maxLines = 1
            view.isSingleLine = true
            view.ellipsize = null
            view.includeFontPadding = false
            TextViewCompat.setAutoSizeTextTypeWithDefaults(view, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
            view.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    view.resources.getDimension(R.dimen.pagination_page_autosize_max_text_size),
            )
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applySelectTabPresentationToView(view.getChildAt(i))
            }
        }
    }

    /**
     * В MODE_FIXED TabLayout даёт каждой вкладке weight=1 — центру (~20%) не хватает на «5208/5208».
     * Те же доли, что у bottom bar: flex 1.45 для «текущая/всего», flex 1 для стрелок.
     */
    private fun ensurePaginationTabWeightsListener(tabLayout: TabLayout) {
        if (!tabLayoutsWithWeightsListener.add(tabLayout)) return
        tabLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyPaginationTabWeights(tabLayout)
        }
    }

    private fun applyPaginationTabWeights(tabLayout: TabLayout) {
        if (tabLayout.tabCount == 0) return
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            val weight = if (tab.tag == TAG_SELECT) SELECT_TAB_WEIGHT else ICON_TAB_WEIGHT
            tab.view.updateLayoutParams<LinearLayout.LayoutParams> {
                width = 0
                this.weight = weight
            }
        }
    }

    private fun syncToolbarPaginationGeometryFromLayout() {
        val collapsing = toolbarPaginationCollapsing ?: return
        val tabLayout = tabLayoutInToolbar ?: return
        if (toolbarPaginationDetachFromChrome) {
            detachToolbarPaginationChrome(collapsing)
        } else if (toolbarPaginationUnifiedHeader && toolbarPaginationFlatHeader) {
            toolbarPaginationAppBar?.let { appBar ->
                appBar.background = ColorDrawable(appBar.context.getColorFromAttr(toolbarPaginationSurfaceColorAttr))
                appBar.clipToOutline = false
                appBar.outlineProvider = ViewOutlineProvider.BOUNDS
                appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = 0
                    marginEnd = 0
                    topMargin = 0
                }
                val elevation = toolbarPaginationElevationPx ?: 0f
                ViewCompat.setElevation(appBar, elevation)
                ViewCompat.setTranslationZ(appBar, elevation)
                appBar.bringToFront()
            }
        } else if (toolbarPaginationUnifiedHeader) {
            toolbarPaginationAppBar?.applyTopBarPlaqueChrome(
                    useHorizontalInset = true,
                    roundedCorners = true,
                    roundedBottomCorners = toolbarPaginationRoundedBottomCorners,
                    surfaceColorAttr = toolbarPaginationSurfaceColorAttr,
            )
            toolbarPaginationAppBar?.let { appBar ->
                val elevation = toolbarPaginationElevationPx ?: appBar.resources.getDimension(R.dimen.dp4)
                ViewCompat.setElevation(appBar, elevation)
                ViewCompat.setTranslationZ(appBar, elevation)
                appBar.bringToFront()
            }
        }
        applyToolbarPaginationScrollFlags(collapsing)
        applyToolbarPaginationChrome(tabLayout)
        ViewCompat.setElevation(tabLayout, 0f)
        ViewCompat.setTranslationZ(tabLayout, 0f)
        syncToolbarPaginationHorizontalInsets(tabLayout)
        applyToolbarPaginationTopMargin(dimensionsProvider.getDimensions(), toolbarPaginationIncludeStatusBar)
    }

    private fun applyToolbarPaginationChrome(tabLayout: TabLayout) {
        if (toolbarPaginationUnifiedHeader) {
            tabLayout.setBackgroundColor(Color.TRANSPARENT)
        } else {
            tabLayout.setBackgroundResource(R.drawable.pref_plate_single)
        }
    }

    private fun detachToolbarPaginationChrome(target: CollapsingToolbarLayout) {
        val appBar = target.parent as? AppBarLayout ?: return
        val listBg = target.context.getColorFromAttr(R.attr.background_for_lists)
        appBar.background = ColorDrawable(listBg)
        appBar.clipToOutline = false
        appBar.outlineProvider = ViewOutlineProvider.BOUNDS
        appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = 0
            marginEnd = 0
            topMargin = 0
        }
        target.background = ColorDrawable(listBg)
        target.clipToOutline = false
        target.setContentScrimColor(listBg)
        target.setStatusBarScrimColor(listBg)
        ensureDetachedToolbarOpaqueUnderlay(target, listBg)
        ViewCompat.setElevation(appBar, target.resources.getDimension(R.dimen.dp4))
        ViewCompat.setTranslationZ(appBar, target.resources.getDimension(R.dimen.dp4))
        appBar.bringToFront()
        target.findViewById<Toolbar>(R.id.toolbar)?.let { toolbar ->
            val inset = toolbar.resources.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            toolbar.setBackgroundResource(R.drawable.pref_plate_single)
            ViewCompat.setElevation(toolbar, 0f)
            ViewCompat.setTranslationZ(toolbar, 0f)
            toolbar.updateLayoutParams<CollapsingToolbarLayout.LayoutParams> {
                marginStart = inset
                marginEnd = inset
                bottomMargin = toolbar.resources.getDimensionPixelSize(R.dimen.content_spacing)
            }
        }
    }

    private fun ensureDetachedToolbarOpaqueUnderlay(target: CollapsingToolbarLayout, color: Int) {
        val existing = toolbarPaginationOpaqueUnderlay
                ?.takeIf { it.parent === target }
                ?: (0 until target.childCount)
                        .asSequence()
                        .map { target.getChildAt(it) }
                        .firstOrNull { it.tag == TAG_DETACHED_OPAQUE_UNDERLAY }

        val underlay = existing ?: View(target.context).apply {
            tag = TAG_DETACHED_OPAQUE_UNDERLAY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = CollapsingToolbarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            }
            target.addView(this, 0)
        }

        underlay.background = ColorDrawable(color)
        toolbarPaginationOpaqueUnderlay = underlay
    }

    private fun applyToolbarPaginationScrollFlags(target: CollapsingToolbarLayout) {
        val params = target.layoutParams as? AppBarLayout.LayoutParams ?: return
        val flags = if (toolbarPaginationScrollEnabled) {
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
        } else {
            0
        }
        if (params.scrollFlags != flags) {
            params.scrollFlags = flags
            target.layoutParams = params
        }
    }
    
    fun setPagination(pagination: Pagination) {
        this.pagination = pagination
    }
    
    fun addInToolbar(
        inflater: LayoutInflater,
        target: CollapsingToolbarLayout,
        enablePadding: Boolean,
        unifiedHeader: Boolean = true,
        detachFromToolbarChrome: Boolean = false,
        roundedBottomCorners: Boolean = true,
        flatHeader: Boolean = false,
        toolbarScrollEnabled: Boolean = false,
        showSinglePageInToolbar: Boolean = false,
        toolbarTopOffsetPx: Int? = null,
        toolbarElevationPx: Float? = null,
        @AttrRes surfaceColorAttr: Int = R.attr.chrome_plane_background,
    ) {
        removeToolbarPaginationGeometryListener()
        toolbarPaginationIncludeStatusBar = enablePadding
        toolbarPaginationUnifiedHeader = unifiedHeader
        toolbarPaginationDetachFromChrome = detachFromToolbarChrome
        toolbarPaginationRoundedBottomCorners = roundedBottomCorners
        toolbarPaginationFlatHeader = flatHeader || surfaceColorAttr == R.attr.main_toolbar_accent_surface
        toolbarPaginationScrollEnabled = toolbarScrollEnabled
        toolbarPaginationShowSinglePage = showSinglePageInToolbar
        toolbarPaginationTopOffsetPx = toolbarTopOffsetPx
        toolbarPaginationElevationPx = toolbarElevationPx
        toolbarPaginationSurfaceColorAttr = surfaceColorAttr
        toolbarPaginationCollapsing = target
        toolbarPaginationAppBar = target.parent as? AppBarLayout
        val tabLayout = inflater.inflate(R.layout.pagination_toolbar, target, false) as TabLayout
        target.addView(tabLayout, target.indexOfChild(target.findViewById(R.id.toolbar)))
        tabLayoutInToolbar = tabLayout
        if (detachFromToolbarChrome) {
            detachToolbarPaginationChrome(target)
            target.post { detachToolbarPaginationChrome(target) }
        }
        syncToolbarPaginationHorizontalInsets(tabLayout)
        toolbarPaginationGeometryListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncToolbarPaginationGeometryFromLayout()
        }
        toolbarPaginationAppBar?.addOnLayoutChangeListener(toolbarPaginationGeometryListener)
        scope.launch {
            dimensionsProvider.dimensionsFlow.collect { dimensions ->
                tabLayoutInToolbar?.post {
                    tabLayoutInToolbar?.let { tl ->
                        applyToolbarPaginationTopMargin(dimensions, enablePadding)
                        syncToolbarPaginationHorizontalInsets(tl)
                    }
                }
                applyToolbarPaginationTopMargin(dimensions, enablePadding)
                tabLayoutInToolbar?.let { tl ->
                    syncToolbarPaginationHorizontalInsets(tl)
                }
            }
        }
        
        applyToolbarPaginationScrollFlags(target)
        target.scrimVisibleHeightTrigger = context.resources.getDimensionPixelSize(R.dimen.dp56) + context.resources.getDimensionPixelSize(R.dimen.dp24)
        setupTabLayout(tabLayout, true)
        tabLayouts.add(tabLayout)
        applySelectTabPresentation(tabLayout)
        target.post { syncToolbarPaginationGeometryFromLayout() }
        target.requestLayout()
    }
    
    fun addInList(inflater: LayoutInflater, target: ViewGroup): TabLayout {
        tabLayoutInList?.let { oldTabLayout ->
            oldTabLayout.removeOnTabSelectedListener(tabSelectedListener)
            tabLayouts.remove(oldTabLayout)
            (oldTabLayout.parent as? ViewGroup)?.removeView(oldTabLayout)
        }
        val tabLayout = inflater.inflate(R.layout.pagination_list, target, false) as TabLayout
        target.addView(tabLayout)
        setupTabLayout(tabLayout, true)
        tabLayouts.add(tabLayout)
        tabLayoutInList = tabLayout
        pagination?.let { updatePagination(it) }
        applySelectTabPresentation(tabLayout)
        target.requestLayout()
        return tabLayout
    }
    
    private fun setupTabLayout(tabLayout: TabLayout, firstLast: Boolean) {
        if (firstLast) {
            tabLayout.addTab(tabLayout.newTab()
                    .setIcon(R.drawable.ic_toolbar_chevron_double_left)
                    .setTag(TAG_FIRST)
                    .setContentDescription(R.string.pagination_first))
        }
        
        tabLayout.addTab(tabLayout.newTab()
                .setIcon(R.drawable.ic_toolbar_chevron_left)
                .setTag(TAG_PREV)
                .setContentDescription(R.string.pagination_prev))
        
        tabLayout.addTab(tabLayout.newTab()
                .setText(R.string.pagination_select)
                .setTag(TAG_SELECT)
                .setContentDescription(R.string.pagination_select_desc))
        
        tabLayout.addTab(tabLayout.newTab()
                .setIcon(R.drawable.ic_toolbar_chevron_right)
                .setTag(TAG_NEXT)
                .setContentDescription(R.string.pagination_next))
        
        if (firstLast) {
            tabLayout.addTab(tabLayout.newTab()
                    .setIcon(R.drawable.ic_toolbar_chevron_double_right)
                    .setTag(TAG_LAST)
                    .setContentDescription(R.string.pagination_last))
        }
        
        tabLayout.addOnTabSelectedListener(tabSelectedListener)
        tabLayout.tabIconTint = null
        tabLayout.post { applySelectTabLongClickState(tabLayout) }
    }

    private fun applySelectTabLongClickState(tabLayout: TabLayout) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i) ?: continue
            if (tab.tag != TAG_SELECT) continue
            tab.view.setOnLongClickListener(
                    if (selectPageInputOnLongClickEnabled) {
                        View.OnLongClickListener {
                            selectPageInputDialog()
                            true
                        }
                    } else {
                        null
                    }
            )
        }
    }

    fun setSelectPageInputOnLongClickEnabled(enabled: Boolean) {
        if (selectPageInputOnLongClickEnabled == enabled) return
        selectPageInputOnLongClickEnabled = enabled
        for (tabLayout in tabLayouts) {
            applySelectTabLongClickState(tabLayout)
        }
    }

    fun selectPageInputDialog() {
        val pag = pagination ?: return
        val totalPages = pag.all.coerceAtLeast(1)

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_GO
            maxLines = 1
            hint = context.getString(R.string.smart_nav_page_input_hint)
            setText(pag.current.toString())
            selectAll()
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = resources.getDimensionPixelSize(R.dimen.dp24)
            val vertical = resources.getDimensionPixelSize(R.dimen.dp8)
            setPadding(horizontal, vertical, horizontal, 0)
            addView(editText, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.smart_nav_enter_page)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .showWithStyledButtons()

        dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        fun submit() {
            val input = editText.text.toString().toIntOrNull()
            if (input == null || input < 1 || input > totalPages) {
                editText.error = context.getString(R.string.smart_nav_page_input_invalid, 1, totalPages)
                return
            }
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText.windowToken, 0)
            selectPage(pag.getPage(input - if (pag.isForum) 1 else 0))
            dialog.dismiss()
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }
        editText.doAfterTextChanged {
            it?.toString()?.toIntOrNull()?.let { value ->
                if (value in 1..totalPages) editText.error = null
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        editText.post {
            editText.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun getCurrentPage(): Int = currentPage
    
    private fun selectPage(pageNumber: Int) {
        currentPage = pageNumber
        listener?.onSelectedPage(pageNumber)
    }
    
    fun firstPage() {
        val pag = pagination ?: return
        if (pag.current <= 1) return
        selectPage(if (pag.isForum) 0 else 1)
    }
    
    fun prevPage() {
        val pag = pagination ?: return
        if (pag.current <= 1) return
        selectPage(pag.getPage(pag.current - if (pag.isForum) 2 else 1))
    }
    
    fun nextPage() {
        val pag = pagination ?: return
        if (pag.current == pag.all) return
        selectPage(pag.getPage(pag.current + if (pag.isForum) 0 else 1))
    }
    
    fun lastPage() {
        val pag = pagination ?: return
        if (listener?.onLastPageSelected(pag) == true) return
        if (pag.current == pag.all) return
        selectPage(pag.getPage(pag.all - if (pag.isForum) 1 else 0))
    }
    
    fun updatePagination(newPagination: Pagination) {
        pagination = newPagination
        for (tabLayout in tabLayouts) {
            val pag = pagination ?: continue
            val isToolbarPagination = tabLayout === tabLayoutInToolbar
            val shouldHideSinglePage = pag.all <= 1 && (!isToolbarPagination || !toolbarPaginationShowSinglePage)
            if (shouldHideSinglePage || isToolbarPagination && !toolbarPaginationEnabled) {
                tabLayout.visibility = View.GONE
                continue
            }
            tabLayout.visibility = View.VISIBLE
            if (isToolbarPagination) {
                applyToolbarPaginationChrome(tabLayout)
                ViewCompat.setElevation(tabLayout, 0f)
                ViewCompat.setTranslationZ(tabLayout, 0f)
            }
            val prevDisabled = pag.current <= 1
            val nextDisabled = pag.current == pag.all
            
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i) ?: continue
                val tag = tab.tag as? Int ?: continue
                
                if (tag == TAG_SELECT) {
                    val titleStr = if (isToolbarPagination && toolbarPaginationShowSinglePage) {
                        "${pag.current}/${pag.all.coerceAtLeast(pag.current)}"
                    } else {
                        title
                    }
                    if (titleStr != null) {
                        tab.text = titleStr
                    }
                    continue
                }
                
                tab.icon?.let { icon ->
                    icon.clearColorFilter()
                    val disabled = (tag == TAG_FIRST || tag == TAG_PREV) && prevDisabled ||
                                   (tag == TAG_NEXT || tag == TAG_LAST) && nextDisabled
                    icon.alpha = if (disabled) ICON_ALPHA_DISABLED else ICON_ALPHA_ENABLED
                }
            }
        }
        for (tl in tabLayouts) {
            applySelectTabPresentation(tl)
        }
    }

    fun setToolbarPaginationEnabled(enabled: Boolean) {
        if (toolbarPaginationEnabled == enabled) return
        toolbarPaginationEnabled = enabled
        pagination?.let { updatePagination(it) } ?: run {
            tabLayoutInToolbar?.visibility = View.GONE
        }
    }

    fun setToolbarTopOffsetPx(topOffsetPx: Int) {
        if (toolbarPaginationTopOffsetPx == topOffsetPx) return
        toolbarPaginationTopOffsetPx = topOffsetPx
        applyToolbarPaginationTopMargin(dimensionsProvider.getDimensions(), toolbarPaginationIncludeStatusBar)
    }

    fun setToolbarElevationPx(elevationPx: Float) {
        if (toolbarPaginationElevationPx == elevationPx) return
        toolbarPaginationElevationPx = elevationPx
        toolbarPaginationAppBar?.let { appBar ->
            ViewCompat.setElevation(appBar, elevationPx)
            ViewCompat.setTranslationZ(appBar, elevationPx)
        }
    }

    fun setToolbarFlatHeader(flatHeader: Boolean) {
        if (toolbarPaginationFlatHeader == flatHeader) return
        toolbarPaginationFlatHeader = flatHeader
        syncToolbarPaginationGeometryFromLayout()
    }

    fun shouldShowToolbarPagination(): Boolean =
            toolbarPaginationEnabled && pagination?.let {
                it.all > 1 || toolbarPaginationShowSinglePage
            } == true

    fun getToolbarPaginationView(): View? = tabLayoutInToolbar
    
    val title: String?
        get() {
            val pag = pagination ?: return null
            return if (pag.all <= 1) null else "${pag.current}/${pag.all}"
        }
    
    fun selectPageDialog() {
        val pag = pagination ?: return
        val pages = IntArray(pag.all) { it + 1 }
        
        val listView = ListView(context)
        listView.divider = null
        listView.dividerHeight = 0
        // Раньше fast-scroll thumb справа перехватывал тапы по правому краю строки
        // (где радио-индикатор) — визуально выглядел как «кнопка выбора», но клики не работали.
        listView.isFastScrollEnabled = false
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.adapter = PaginationAdapter(context, pages)
        listView.setItemChecked(pag.current - 1, true)
        listView.setSelection(pag.current - 1)
        
        val dialog = MaterialAlertDialogBuilder(context)
                .setView(listView)
                .showWithStyledButtons()
        
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        listView.setOnItemClickListener { _, _, position, _ ->
            if (listView.tag == false) return@setOnItemClickListener
            selectPage(position * pag.perPage)
            dialog.cancel()
        }
    }

    fun setListener(listener: PaginationListener?) {
        this.listener = listener
    }
    
    fun destroy() {
        scope.cancel()
        removeToolbarPaginationGeometryListener()
        for (tabLayout in tabLayouts) {
            tabLayout.removeOnTabSelectedListener(tabSelectedListener)
        }
        tabLayouts.clear()
        tabLayoutsWithWeightsListener.clear()
        tabLayoutInToolbar = null
        tabLayoutInList = null
        listener = null
        pagination = null
    }
    
    interface PaginationListener {
        fun onTabSelected(tab: TabLayout.Tab): Boolean
        fun onSelectedPage(pageNumber: Int)
        fun onLastPageSelected(pagination: Pagination): Boolean = false
    }
}
