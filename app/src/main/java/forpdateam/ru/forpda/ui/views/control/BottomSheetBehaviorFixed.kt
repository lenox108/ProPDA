package forpdateam.ru.forpda.ui.views.control
import timber.log.Timber

/*
 * Copyright (C) 2015 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewParent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.NonNull
import androidx.annotation.RestrictTo
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.math.MathUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.customview.view.AbsSavedState
import androidx.customview.widget.ViewDragHelper
import com.google.android.material.R as MaterialR
import com.google.android.material.internal.ViewUtils
import com.google.android.material.internal.ViewUtils.RelativePadding
import com.google.android.material.resources.MaterialResources
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("RestrictedApi")
open class BottomSheetBehaviorFixed<V : View> : CoordinatorLayout.Behavior<V>, ICustomBottomSheetBehavior<V> {

    abstract class BottomSheetCallback {
        abstract fun onStateChanged(bottomSheet: View, newState: Int)
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(STATE_DRAGGING, STATE_SETTLING, STATE_EXPANDED, STATE_COLLAPSED, STATE_HIDDEN, STATE_HALF_EXPANDED)
    annotation class State

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(flag = true, value = [SAVE_PEEK_HEIGHT, SAVE_FIT_TO_CONTENTS, SAVE_HIDEABLE, SAVE_SKIP_COLLAPSED, SAVE_ALL, SAVE_NONE])
    annotation class SaveFlags

    companion object {
        const val STATE_DRAGGING = 1
        const val STATE_SETTLING = 2
        const val STATE_EXPANDED = 3
        const val STATE_COLLAPSED = 4
        const val STATE_HIDDEN = 5
        const val STATE_HALF_EXPANDED = 6
        const val PEEK_HEIGHT_AUTO = -1
        const val SAVE_PEEK_HEIGHT = 0x1
        const val SAVE_FIT_TO_CONTENTS = 1 shl 1
        const val SAVE_HIDEABLE = 1 shl 2
        const val SAVE_SKIP_COLLAPSED = 1 shl 3
        const val SAVE_ALL = -1
        const val SAVE_NONE = 0
        private const val TAG = "BottomSheetBehavior"
        private const val SIGNIFICANT_VEL_THRESHOLD = 500
        private const val HIDE_THRESHOLD = 0.5f
        private const val HIDE_FRICTION = 0.1f
        private const val CORNER_ANIMATION_DURATION = 500
        private val DEF_STYLE_RES = MaterialR.style.Widget_Design_BottomSheet_Modal

        @Suppress("UNCHECKED_CAST")
        fun <V : View> from(view: V): BottomSheetBehaviorFixed<V> {
            val params = view.layoutParams
            require(params is CoordinatorLayout.LayoutParams) { "The view is not a child of CoordinatorLayout" }
            val behavior = params.behavior
            require(behavior is BottomSheetBehaviorFixed) { "The view is not associated with BottomSheetBehavior" }
            return behavior as BottomSheetBehaviorFixed<V>
        }
    }

    // Fields - Part 1
    @SaveFlags
    private var saveFlags: Int = SAVE_NONE
    private var fitToContents: Boolean = true
    private var updateImportantForAccessibilityOnSiblings: Boolean = false
    private var maximumVelocity: Float = 0f
    internal var peekHeight: Int = 0
    private var peekHeightAuto: Boolean = false
    private var peekHeightMin: Int = 0
    private var peekHeightGestureInsetBuffer: Int = 0
    private var shapeThemingEnabled: Boolean = false
    private var materialShapeDrawable: MaterialShapeDrawable? = null
    private var gestureInsetBottom: Int = 0
    var gestureInsetBottomIgnored: Boolean = false
    private var shapeAppearanceModelDefault: ShapeAppearanceModel? = null
    private var isShapeExpanded: Boolean = false
    private var settleRunnable: SettleRunnable? = null
    private var interpolatorAnimator: ValueAnimator? = null

    // Fields - Part 2
    internal var expandedOffset: Int = 0
    internal var fitToContentsOffset: Int = 0
    internal var halfExpandedOffset: Int = 0
    internal var halfExpandedRatio: Float = 0.5f
    internal var collapsedOffset: Int = 0
    internal var elevation: Float = -1f
    internal var hideable: Boolean = false
    internal var skipCollapsed: Boolean = false
    internal var draggable: Boolean = true
    internal var state: Int = STATE_COLLAPSED
    internal var viewDragHelper: ViewDragHelper? = null
    private var ignoreEvents: Boolean = false
    private var lastNestedScrollDy: Int = 0
    private var nestedScrolled: Boolean = false
    private var childHeight: Int = 0
    internal var parentWidth: Int = 0
    internal var parentHeight: Int = 0
    internal var viewRef: WeakReference<V>? = null
    internal var mNestedScrollingChildRefList: List<View>? = null
    private val callbacks: ArrayList<BottomSheetCallback> = ArrayList()
    private var velocityTracker: VelocityTracker? = null
    internal var activePointerId: Int = 0
    private var initialY: Int = 0
    internal var touchingScrollingChild: Boolean = false
    private var importantForAccessibilityMap: MutableMap<View, Int>? = null
    private var expandHalfwayActionId: Int = View.NO_ID

    constructor()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        peekHeightGestureInsetBuffer =
            context.resources.getDimensionPixelSize(MaterialR.dimen.mtrl_min_touch_target_size)

        val a = context.obtainStyledAttributes(attrs, MaterialR.styleable.BottomSheetBehavior_Layout)
        shapeThemingEnabled = a.hasValue(MaterialR.styleable.BottomSheetBehavior_Layout_shapeAppearance)
        val hasBackgroundTint = a.hasValue(MaterialR.styleable.BottomSheetBehavior_Layout_backgroundTint)
        if (hasBackgroundTint) {
            val bottomSheetColor = MaterialResources.getColorStateList(
                context, a, MaterialR.styleable.BottomSheetBehavior_Layout_backgroundTint)
            createMaterialShapeDrawable(context, attrs, hasBackgroundTint, bottomSheetColor)
        } else {
            createMaterialShapeDrawable(context, attrs, hasBackgroundTint)
        }
        createShapeValueAnimator()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = a.getDimension(MaterialR.styleable.BottomSheetBehavior_Layout_android_elevation, -1f)
        }

        val value = a.peekValue(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_peekHeight)
        if (value != null && value.data == PEEK_HEIGHT_AUTO) {
            setPeekHeight(value.data)
        } else {
            setPeekHeight(a.getDimensionPixelSize(
                MaterialR.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO))
        }
        isHideable = a.getBoolean(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_hideable, false)
        gestureInsetBottomIgnored = a.getBoolean(MaterialR.styleable.BottomSheetBehavior_Layout_gestureInsetBottomIgnored, false)
        isFitToContents = a.getBoolean(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_fitToContents, true)
        skipCollapsed = a.getBoolean(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed, false)
        isDraggable = a.getBoolean(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_draggable, true)
        saveFlags = a.getInt(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_saveFlags, SAVE_NONE)
        halfExpandedRatio = a.getFloat(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_halfExpandedRatio, 0.5f)

        val expandedValue = a.peekValue(MaterialR.styleable.BottomSheetBehavior_Layout_behavior_expandedOffset)
        if (expandedValue != null && expandedValue.type == TypedValue.TYPE_FIRST_INT) {
            expandedOffset = expandedValue.data
        } else {
            expandedOffset = a.getDimensionPixelOffset(
                MaterialR.styleable.BottomSheetBehavior_Layout_behavior_expandedOffset, 0)
        }
        a.recycle()
        maximumVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    }

    override fun onSaveInstanceState(parent: CoordinatorLayout, child: V): Parcelable =
        SavedState(super.onSaveInstanceState(parent, child) ?: AbsSavedState.EMPTY_STATE, this)

    override fun onRestoreInstanceState(parent: CoordinatorLayout, child: V, state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(parent, child, ss.superState!!)
        restoreOptionalState(ss)
        this.state = if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) STATE_COLLAPSED else ss.state
    }

    override fun onAttachedToLayoutParams(layoutParams: CoordinatorLayout.LayoutParams) {
        super.onAttachedToLayoutParams(layoutParams)
        viewRef = null
        viewDragHelper = null
    }

    override fun onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams()
        viewRef = null
        viewDragHelper = null
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            child.fitsSystemWindows = true
        }
        if (viewRef == null) {
            peekHeightMin = parent.resources.getDimensionPixelSize(MaterialR.dimen.design_bottom_sheet_peek_height_min)
            setSystemGestureInsets(child)
            viewRef = WeakReference(child)
            if (shapeThemingEnabled) materialShapeDrawable?.let { ViewCompat.setBackground(child, it) }
            materialShapeDrawable?.let {
                it.elevation = if (elevation == -1f) ViewCompat.getElevation(child) else elevation
                isShapeExpanded = state == STATE_EXPANDED
                it.interpolation = if (isShapeExpanded) 0f else 1f
            }
            updateAccessibilityActions()
            if (ViewCompat.getImportantForAccessibility(child) == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                ViewCompat.setImportantForAccessibility(child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
            }
        }
        if (viewDragHelper == null) viewDragHelper = ViewDragHelper.create(parent, dragCallback)

        val savedTop = child.top
        parent.onLayoutChild(child, layoutDirection)
        parentWidth = parent.width
        parentHeight = parent.height
        childHeight = child.height
        fitToContentsOffset = max(0, parentHeight - childHeight)
        calculateHalfExpandedOffset()
        calculateCollapsedOffset()

        when (state) {
            STATE_EXPANDED -> ViewCompat.offsetTopAndBottom(child, getExpandedOffset())
            STATE_HALF_EXPANDED -> ViewCompat.offsetTopAndBottom(child, halfExpandedOffset)
            STATE_HIDDEN -> if (hideable) ViewCompat.offsetTopAndBottom(child, parentHeight)
            STATE_COLLAPSED -> ViewCompat.offsetTopAndBottom(child, collapsedOffset)
            STATE_DRAGGING, STATE_SETTLING -> ViewCompat.offsetTopAndBottom(child, savedTop - child.top)
        }
        return true
    }

    override fun setNestedScrollingChildRefList(nestedScrollingChildRefList: List<View>) {
        mNestedScrollingChildRefList = nestedScrollingChildRefList
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!child.isShown || !draggable) { ignoreEvents = true; return false }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) reset()
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker!!.addMovement(event)
        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchingScrollingChild = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (ignoreEvents) { ignoreEvents = false; return false }
            }
            MotionEvent.ACTION_DOWN -> {
                val initialX = event.x.toInt()
                initialY = event.y.toInt()
                if (state != STATE_SETTLING) {
                    mNestedScrollingChildRefList?.let { list ->
                        for (childView in list) {
                            if (childView != null && parent.isPointInChildBounds(childView, initialX, initialY)) {
                                activePointerId = event.getPointerId(event.actionIndex)
                                touchingScrollingChild = true
                            }
                        }
                    }
                }
                ignoreEvents = activePointerId == MotionEvent.INVALID_POINTER_ID &&
                    !parent.isPointInChildBounds(child, initialX, initialY)
            }
        }
        if (!ignoreEvents && viewDragHelper != null && viewDragHelper!!.shouldInterceptTouchEvent(event)) return true
        return action == MotionEvent.ACTION_MOVE && !ignoreEvents && state != STATE_DRAGGING &&
            !isPointInsideChildScrollView(parent, event.x.toInt(), event.y.toInt()) &&
            viewDragHelper != null && abs(initialY - event.y) > viewDragHelper!!.touchSlop
    }

    private fun isPointInsideChildScrollView(parent: CoordinatorLayout, x: Int, y: Int): Boolean {
        mNestedScrollingChildRefList?.let { list ->
            for (child in list) { if (child != null && parent.isPointInChildBounds(child, x, y)) return true }
        }
        return false
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (!child.isShown) return false
        if (state == STATE_DRAGGING && event.actionMasked == MotionEvent.ACTION_DOWN) return true
        viewDragHelper?.processTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) reset()
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker!!.addMovement(event)
        if (viewDragHelper != null && event.actionMasked == MotionEvent.ACTION_MOVE && !ignoreEvents) {
            if (abs(initialY - event.y) > viewDragHelper!!.touchSlop) {
                viewDragHelper!!.captureChildView(child, event.getPointerId(event.actionIndex))
            }
        }
        return !ignoreEvents
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V,
        directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
        lastNestedScrollDy = 0; nestedScrolled = false
        return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View,
        dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (type == ViewCompat.TYPE_NON_TOUCH) return
        if (!isOneOfChild(target)) return
        val currentTop = child.top
        val newTop = currentTop - dy
        if (dy > 0) {
            val expanded = getExpandedOffset()
            if (newTop < expanded) {
                consumed[1] = currentTop - expanded
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                if (!draggable) return
                consumed[1] = dy; ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) {
            if (!target.canScrollVertically(-1)) {
                if (newTop <= collapsedOffset || hideable) {
                    if (!draggable) return
                    consumed[1] = dy; ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - collapsedOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        dispatchOnSlide(child.top); lastNestedScrollDy = dy; nestedScrolled = true
    }

    private fun isOneOfChild(childView: View): Boolean {
        mNestedScrollingChildRefList?.let { list ->
            for (child in list) { if (child != null && child === childView) return true }
        }
        return false
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {
        if (child.top == getExpandedOffset()) { setStateInternal(STATE_EXPANDED); return }
        if (!isOneOfChild(target) || !nestedScrolled) return
        val top: Int; val targetState: Int
        if (lastNestedScrollDy > 0) {
            if (fitToContents) { top = fitToContentsOffset; targetState = STATE_EXPANDED }
            else { val currentTop = child.top; if (currentTop > halfExpandedOffset) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = getExpandedOffset(); targetState = STATE_EXPANDED } }
        } else if (hideable && shouldHide(child, yVelocity)) {
            top = parentHeight; targetState = STATE_HIDDEN
        } else if (lastNestedScrollDy == 0) {
            val currentTop = child.top
            if (fitToContents) {
                if (abs(currentTop - fitToContentsOffset) < abs(currentTop - collapsedOffset)) { top = fitToContentsOffset; targetState = STATE_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED }
            } else {
                if (currentTop < halfExpandedOffset) { if (currentTop < abs(currentTop - collapsedOffset)) { top = expandedOffset; targetState = STATE_EXPANDED } else { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } }
                else { if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED } }
            }
        } else {
            if (fitToContents) { top = collapsedOffset; targetState = STATE_COLLAPSED }
            else { val currentTop = child.top; if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED } }
        }
        startSettlingAnimation(child, targetState, top, false); nestedScrolled = false
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View,
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int, consumed: IntArray) {}

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V, target: View,
        velocityX: Float, velocityY: Float): Boolean = isOneOfChild(target) &&
        (state != STATE_EXPANDED || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY))

    var isFitToContents: Boolean
        get() = fitToContents
        set(value) { if (fitToContents == value) return; fitToContents = value; if (viewRef != null) calculateCollapsedOffset(); setStateInternal(if (fitToContents && state == STATE_HALF_EXPANDED) STATE_EXPANDED else state); updateAccessibilityActions() }

    fun setPeekHeight(peekHeight: Int) = setPeekHeight(peekHeight, false)

    fun setPeekHeight(peekHeight: Int, animate: Boolean) {
        var layout = false
        if (peekHeight == PEEK_HEIGHT_AUTO) { if (!peekHeightAuto) { peekHeightAuto = true; layout = true } }
        else if (peekHeightAuto || this.peekHeight != peekHeight) { peekHeightAuto = false; this.peekHeight = max(0, peekHeight); layout = true }
        if (layout) updatePeekHeight(animate)
    }

    private fun updatePeekHeight(animate: Boolean) {
        if (viewRef != null) { calculateCollapsedOffset(); if (state == STATE_COLLAPSED) { viewRef!!.get()?.let { if (animate) settleToStatePendingLayout(state) else it.requestLayout() } } }
    }

    fun getPeekHeight(): Int = if (peekHeightAuto) PEEK_HEIGHT_AUTO else peekHeight

    fun setHalfExpandedRatio(@FloatRange(from = 0.0, to = 1.0) ratio: Float) {
        require(ratio > 0 && ratio < 1) { "ratio must be a float value between 0 and 1" }
        halfExpandedRatio = ratio; if (viewRef != null) calculateHalfExpandedOffset()
    }

    @FloatRange(from = 0.0, to = 1.0) fun getHalfExpandedRatio(): Float = halfExpandedRatio

    fun setExpandedOffset(offset: Int) { require(offset >= 0) { "offset must be greater than or equal to 0" }; expandedOffset = offset }
    fun getExpandedOffset(): Int = if (fitToContents) fitToContentsOffset else expandedOffset

    var isHideable: Boolean
        get() = hideable
        set(value) { if (hideable == value) return; hideable = value; if (!hideable && state == STATE_HIDDEN) setState(STATE_COLLAPSED); updateAccessibilityActions() }

    fun setSkipCollapsed(skipCollapsed: Boolean) { this.skipCollapsed = skipCollapsed }
    fun getSkipCollapsed(): Boolean = skipCollapsed

    var isDraggable: Boolean
        get() = draggable
        set(value) { draggable = value }

    fun setSaveFlags(@SaveFlags flags: Int) { saveFlags = flags }
    @SaveFlags
    fun getSaveFlags(): Int = saveFlags

    @Deprecated("Use addBottomSheetCallback and removeBottomSheetCallback")
    fun setBottomSheetCallback(callback: BottomSheetCallback?) {
        Timber.w(TAG, "BottomSheetBehavior now supports multiple callbacks. `setBottomSheetCallback()` removes all existing callbacks, including ones set internally by library authors, which may result in unintended behavior. This may change in the future. Please use `addBottomSheetCallback()` and `removeBottomSheetCallback()` instead to set your own callbacks.")
        callbacks.clear(); callback?.let { callbacks.add(it) }
    }

    fun addBottomSheetCallback(callback: BottomSheetCallback) { if (!callbacks.contains(callback)) callbacks.add(callback) }
    fun removeBottomSheetCallback(callback: BottomSheetCallback) { callbacks.remove(callback) }

    fun setState(state: Int) {
        if (state == this.state) return
        if (viewRef == null) { if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_HALF_EXPANDED || (hideable && state == STATE_HIDDEN)) this.state = state; return }
        settleToStatePendingLayout(state)
    }

    private fun settleToStatePendingLayout(state: Int) {
        val child = viewRef?.get() ?: return
        val parent = child.parent
        if (parent != null && parent.isLayoutRequested && ViewCompat.isAttachedToWindow(child)) child.post { settleToState(child, state) }
        else settleToState(child, state)
    }

    fun getState(): Int = state

    internal fun setStateInternal(state: Int) {
        if (this.state == state) return; this.state = state
        val bottomSheet = viewRef?.get() ?: return
        when (state) { STATE_EXPANDED -> updateImportantForAccessibility(true); STATE_HALF_EXPANDED, STATE_HIDDEN, STATE_COLLAPSED -> updateImportantForAccessibility(false) }
        updateDrawableForTargetState(state)
        for (callback in callbacks) callback.onStateChanged(bottomSheet, state)
        updateAccessibilityActions()
    }

    private fun updateDrawableForTargetState(state: Int) {
        if (state == STATE_SETTLING) return
        val expand = state == STATE_EXPANDED
        if (isShapeExpanded != expand) {
            isShapeExpanded = expand
            materialShapeDrawable?.let { drawable -> interpolatorAnimator?.let { animator ->
                if (animator.isRunning) animator.reverse()
                else { val to = if (expand) 0f else 1f; animator.setFloatValues(1f - to, to); animator.start() }
            } }
        }
    }

    private fun calculatePeekHeight(): Int {
        if (peekHeightAuto) { val desiredHeight = max(peekHeightMin, parentHeight - parentWidth * 9 / 16); return min(desiredHeight, childHeight) }
        if (!gestureInsetBottomIgnored && gestureInsetBottom > 0) return max(peekHeight, gestureInsetBottom + peekHeightGestureInsetBuffer)
        return peekHeight
    }

    private fun calculateCollapsedOffset() {
        val peek = calculatePeekHeight()
        collapsedOffset = if (fitToContents) max(parentHeight - peek, fitToContentsOffset) else parentHeight - peek
    }

    private fun calculateHalfExpandedOffset() { halfExpandedOffset = (parentHeight * (1 - halfExpandedRatio)).toInt() }

    private fun reset() { activePointerId = ViewDragHelper.INVALID_POINTER; velocityTracker?.recycle(); velocityTracker = null }

    private fun restoreOptionalState(ss: SavedState) {
        if (saveFlags == SAVE_NONE) return
        if (saveFlags == SAVE_ALL || saveFlags and SAVE_PEEK_HEIGHT == SAVE_PEEK_HEIGHT) peekHeight = ss.peekHeight
        if (saveFlags == SAVE_ALL || saveFlags and SAVE_FIT_TO_CONTENTS == SAVE_FIT_TO_CONTENTS) fitToContents = ss.fitToContents
        if (saveFlags == SAVE_ALL || saveFlags and SAVE_HIDEABLE == SAVE_HIDEABLE) hideable = ss.hideable
        if (saveFlags == SAVE_ALL || saveFlags and SAVE_SKIP_COLLAPSED == SAVE_SKIP_COLLAPSED) skipCollapsed = ss.skipCollapsed
    }

    internal fun shouldHide(child: View, yvel: Float): Boolean {
        if (skipCollapsed) return true; if (child.top < collapsedOffset) return false
        val peek = calculatePeekHeight(); val newTop = child.top + yvel * HIDE_FRICTION
        return abs(newTop - collapsedOffset).toFloat() / peek > HIDE_THRESHOLD
    }

    @VisibleForTesting fun findScrollingChild(view: View): View? {
        if (ViewCompat.isNestedScrollingEnabled(view)) return view
        if (view is ViewGroup) { for (i in 0 until view.childCount) { val child = findScrollingChild(view.getChildAt(i)); if (child != null) return child } }
        return null
    }

    private fun createMaterialShapeDrawable(context: Context, attrs: AttributeSet?, hasBackgroundTint: Boolean, bottomSheetColor: ColorStateList? = null) {
        if (shapeThemingEnabled) {
            shapeAppearanceModelDefault = ShapeAppearanceModel.builder(context, attrs, MaterialR.attr.bottomSheetStyle, DEF_STYLE_RES).build()
            materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModelDefault!!).also { drawable ->
                drawable.initializeElevationOverlay(context)
                if (hasBackgroundTint && bottomSheetColor != null) drawable.fillColor = bottomSheetColor
                else { val defaultColor = TypedValue(); context.theme.resolveAttribute(android.R.attr.colorBackground, defaultColor, true); drawable.setTint(defaultColor.data) }
            }
        }
    }

    private fun createShapeValueAnimator() {
        interpolatorAnimator = ValueAnimator.ofFloat(0f, 1f).also { animator ->
            animator.duration = CORNER_ANIMATION_DURATION.toLong()
            animator.addUpdateListener { animation -> materialShapeDrawable?.interpolation = animation.animatedValue as Float }
        }
    }

    private fun setSystemGestureInsets(child: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !gestureInsetBottomIgnored && !peekHeightAuto) {
            ViewUtils.doOnApplyWindowInsets(child, object : ViewUtils.OnApplyWindowInsetsListener {
                override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat, initialPadding: RelativePadding): WindowInsetsCompat {
                    gestureInsetBottom = insets.mandatorySystemGestureInsets.bottom
                    updatePeekHeight(false); return insets
                }
            })
        }
    }

    private val yVelocity: Float
        get() { velocityTracker?.let { it.computeCurrentVelocity(1000, maximumVelocity); return it.getYVelocity(activePointerId) }; return 0f }

    internal fun settleToState(child: View, state: Int) {
        val top: Int; val finalState: Int = state
        when (state) {
            STATE_COLLAPSED -> { top = collapsedOffset }
            STATE_HALF_EXPANDED -> { top = halfExpandedOffset; if (fitToContents && top <= fitToContentsOffset) { settleToState(child, STATE_EXPANDED); return } }
            STATE_EXPANDED -> { top = getExpandedOffset() }
            STATE_HIDDEN -> if (hideable) { top = parentHeight } else throw IllegalArgumentException("Illegal state argument: $state")
            else -> throw IllegalArgumentException("Illegal state argument: $state")
        }
        startSettlingAnimation(child, finalState, top, false)
    }

    internal fun startSettlingAnimation(child: View, state: Int, top: Int, settleFromViewDragHelper: Boolean) {
        val startedSettling = viewDragHelper != null &&
            (if (settleFromViewDragHelper) viewDragHelper!!.settleCapturedViewAt(child.left, top) else viewDragHelper!!.smoothSlideViewTo(child, child.left, top))
        if (startedSettling) {
            setStateInternal(STATE_SETTLING); updateDrawableForTargetState(state)
            if (settleRunnable == null) settleRunnable = SettleRunnable(child, state)
            if (!settleRunnable!!.isPosted) { settleRunnable!!.targetState = state; ViewCompat.postOnAnimation(child, settleRunnable!!); settleRunnable!!.isPosted = true }
            else settleRunnable!!.targetState = state
        } else setStateInternal(state)
    }

    private val dragCallback = object : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (state == STATE_DRAGGING) return false
            if (touchingScrollingChild) return false
            if (state == STATE_EXPANDED && activePointerId == pointerId) {
                mNestedScrollingChildRefList?.let { list -> for (childView in list) { if (childView != null && ViewCompat.canScrollVertically(childView, -1)) return false } }
            }
            return viewRef?.get() === child
        }
        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) = dispatchOnSlide(top)
        override fun onViewDragStateChanged(state: Int) { if (state == ViewDragHelper.STATE_DRAGGING && draggable) setStateInternal(STATE_DRAGGING) }
        private fun releasedLow(child: View): Boolean = child.top > (parentHeight + getExpandedOffset()) / 2
        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val top: Int; val targetState: Int
            if (yvel < 0) {
                if (fitToContents) { top = fitToContentsOffset; targetState = STATE_EXPANDED }
                else { val currentTop = releasedChild.top; if (currentTop > halfExpandedOffset) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = expandedOffset; targetState = STATE_EXPANDED } }
            } else if (hideable && shouldHide(releasedChild, yvel)) {
                if ((abs(xvel) < abs(yvel) && yvel > SIGNIFICANT_VEL_THRESHOLD) || releasedLow(releasedChild)) { top = parentHeight; targetState = STATE_HIDDEN }
                else if (fitToContents) { top = fitToContentsOffset; targetState = STATE_EXPANDED }
                else if (abs(releasedChild.top - expandedOffset) < abs(releasedChild.top - halfExpandedOffset)) { top = expandedOffset; targetState = STATE_EXPANDED }
                else { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED }
            } else if (yvel == 0f || abs(xvel) > abs(yvel)) {
                val currentTop = releasedChild.top
                if (fitToContents) { if (abs(currentTop - fitToContentsOffset) < abs(currentTop - collapsedOffset)) { top = fitToContentsOffset; targetState = STATE_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED } }
                else { if (currentTop < halfExpandedOffset) { if (currentTop < abs(currentTop - collapsedOffset)) { top = expandedOffset; targetState = STATE_EXPANDED } else { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } } else { if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED } } }
            } else {
                if (fitToContents) { top = collapsedOffset; targetState = STATE_COLLAPSED }
                else { val currentTop = releasedChild.top; if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) { top = halfExpandedOffset; targetState = STATE_HALF_EXPANDED } else { top = collapsedOffset; targetState = STATE_COLLAPSED } }
            }
            startSettlingAnimation(releasedChild, targetState, top, true)
        }
        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = MathUtils.clamp(top, getExpandedOffset(), if (hideable) parentHeight else collapsedOffset)
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = child.left
        override fun getViewVerticalDragRange(child: View): Int = if (hideable) parentHeight else collapsedOffset
    }

    internal fun dispatchOnSlide(top: Int) {
        val bottomSheet = viewRef?.get()
        if (bottomSheet != null && callbacks.isNotEmpty()) {
            val slideOffset = if (top > collapsedOffset || collapsedOffset == expandedOffset)
                (collapsedOffset - top).toFloat() / (parentHeight - collapsedOffset)
            else (collapsedOffset - top).toFloat() / (collapsedOffset - expandedOffset)
            for (callback in callbacks) callback.onSlide(bottomSheet, slideOffset)
        }
    }

    @VisibleForTesting fun getPeekHeightMin(): Int = peekHeightMin

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting
    fun disableShapeAnimations() { interpolatorAnimator = null }

    fun setUpdateImportantForAccessibilityOnSiblings(updateImportantForAccessibilityOnSiblings: Boolean) {
        this.updateImportantForAccessibilityOnSiblings = updateImportantForAccessibilityOnSiblings
    }

    private fun updateImportantForAccessibility(expanded: Boolean) {
        val viewRef = this.viewRef ?: return
        val viewParent = viewRef.get()?.parent ?: return
        if (viewParent !is CoordinatorLayout) return
        val parent = viewParent
        val childCount = parent.childCount
        if (expanded) {
            if (importantForAccessibilityMap == null) importantForAccessibilityMap = HashMap(childCount) else return
        }
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            if (child === viewRef.get()) continue
            if (expanded) {
                importantForAccessibilityMap!![child] = child.importantForAccessibility
                if (updateImportantForAccessibilityOnSiblings) ViewCompat.setImportantForAccessibility(child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            } else {
                if (updateImportantForAccessibilityOnSiblings && importantForAccessibilityMap != null && importantForAccessibilityMap!!.containsKey(child))
                    ViewCompat.setImportantForAccessibility(child, importantForAccessibilityMap!![child]!!)
            }
        }
        if (!expanded) importantForAccessibilityMap = null
        else if (updateImportantForAccessibilityOnSiblings) viewRef.get()?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    private fun updateAccessibilityActions() {
        val child = viewRef?.get() ?: return
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_COLLAPSE)
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_EXPAND)
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_DISMISS)
        if (expandHalfwayActionId != View.NO_ID) ViewCompat.removeAccessibilityAction(child, expandHalfwayActionId)
        if (state != STATE_HALF_EXPANDED) expandHalfwayActionId = addAccessibilityActionForState(child, MaterialR.string.bottom_sheet_behavior, STATE_HALF_EXPANDED)
        if (hideable && state != STATE_HIDDEN) replaceAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_DISMISS, STATE_HIDDEN)
        when (state) {
            STATE_EXPANDED -> { val nextState = if (fitToContents) STATE_COLLAPSED else STATE_HALF_EXPANDED; replaceAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_COLLAPSE, nextState) }
            STATE_HALF_EXPANDED -> { replaceAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_COLLAPSE, STATE_COLLAPSED); replaceAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_EXPAND, STATE_EXPANDED) }
            STATE_COLLAPSED -> { val nextState = if (fitToContents) STATE_EXPANDED else STATE_HALF_EXPANDED; replaceAccessibilityActionForState(child, AccessibilityActionCompat.ACTION_EXPAND, nextState) }
        }
    }

    private fun replaceAccessibilityActionForState(child: V, action: AccessibilityActionCompat, state: Int) {
        ViewCompat.replaceAccessibilityAction(child, action, null, createAccessibilityViewCommandForState(state))
    }

    private fun addAccessibilityActionForState(child: V, @StringRes stringResId: Int, state: Int): Int =
        ViewCompat.addAccessibilityAction(child, child.resources.getString(stringResId), createAccessibilityViewCommandForState(state))

    private fun createAccessibilityViewCommandForState(state: Int): AccessibilityViewCommand =
        AccessibilityViewCommand { _, _ -> setState(state); true }

    private inner class SettleRunnable(private val view: View, @State var targetState: Int) : Runnable {
        var isPosted: Boolean = false
        override fun run() {
            if (viewDragHelper != null && viewDragHelper!!.continueSettling(true)) ViewCompat.postOnAnimation(view, this)
            else setStateInternal(targetState)
            isPosted = false
        }
    }

    internal class SavedState : AbsSavedState {
        val state: Int
        var peekHeight: Int = 0
        var fitToContents: Boolean = true
        var hideable: Boolean = false
        var skipCollapsed: Boolean = false

        constructor(source: Parcel) : this(source, null)
        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            state = source.readInt(); peekHeight = source.readInt(); fitToContents = source.readInt() == 1; hideable = source.readInt() == 1; skipCollapsed = source.readInt() == 1
        }
        constructor(superState: Parcelable, behavior: BottomSheetBehaviorFixed<*>) : super(superState) {
            state = behavior.state; peekHeight = behavior.peekHeight; fitToContents = behavior.fitToContents; hideable = behavior.hideable; skipCollapsed = behavior.skipCollapsed
        }
        @Deprecated("Use constructor(superState, behavior)")
        constructor(superState: Parcelable, state: Int) : super(superState) { this.state = state }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(state); out.writeInt(peekHeight); out.writeInt(if (fitToContents) 1 else 0); out.writeInt(if (hideable) 1 else 0); out.writeInt(if (skipCollapsed) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.ClassLoaderCreator<SavedState> = object : Parcelable.ClassLoaderCreator<SavedState> {
                override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState = SavedState(source, loader)
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source, null)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
