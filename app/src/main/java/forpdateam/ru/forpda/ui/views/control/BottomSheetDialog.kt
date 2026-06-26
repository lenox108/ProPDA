package forpdateam.ru.forpda.ui.views.control

import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import forpdateam.ru.forpda.databinding.DesignBottomSheetFixedBinding
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.R as MaterialR

/**
 * Base class for [android.app.Dialog]s styled as a bottom sheet.
 */
class BottomSheetDialog : AppCompatDialog {
    private var behavior: BottomSheetBehaviorFixed<FrameLayout>? = null
    private var bottomSheetView: FrameLayout? = null

    private var cancelable: Boolean = true
    private var canceledOnTouchOutside: Boolean = true
    private var canceledOnTouchOutsideSet: Boolean = false

    constructor(context: Context) : this(context, 0)

    constructor(context: Context, @StyleRes theme: Int) : super(context, getThemeResId(context, theme)) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    protected constructor(context: Context, cancelable: Boolean, cancelListener: DialogInterface.OnCancelListener?) : super(context, cancelable, cancelListener) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        this.cancelable = cancelable
    }

    override fun setContentView(@LayoutRes layoutResId: Int) {
        super.setContentView(wrapInBottomSheet(layoutResId, null, null))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        window?.let {
            it.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun setContentView(view: View) {
        super.setContentView(wrapInBottomSheet(0, view, null))
    }

    @JvmName("setContentViewWithParams")
    fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(wrapInBottomSheet(0, view, params))
    }

    override fun setCancelable(cancelable: Boolean) {
        super.setCancelable(cancelable)
        if (this.cancelable != cancelable) {
            this.cancelable = cancelable
            behavior?.isHideable = cancelable
        }
    }

    override fun onStart() {
        super.onStart()
        if (behavior?.state == BottomSheetBehaviorFixed.STATE_HIDDEN) {
            behavior?.state = BottomSheetBehaviorFixed.STATE_COLLAPSED
        }
    }

    override fun setCanceledOnTouchOutside(cancel: Boolean) {
        super.setCanceledOnTouchOutside(cancel)
        if (cancel && !cancelable) {
            cancelable = true
        }
        canceledOnTouchOutside = cancel
        canceledOnTouchOutsideSet = true
    }

    fun getBehavior(): BottomSheetBehaviorFixed<FrameLayout>? = behavior

    fun getBottomSheetView(): FrameLayout? = bottomSheetView

    private fun wrapInBottomSheet(layoutResId: Int, view: View?, params: ViewGroup.LayoutParams?): View {
        val binding = DesignBottomSheetFixedBinding.inflate(LayoutInflater.from(context), null, false)
        val container: ViewGroup = binding.root
        val coordinator: CoordinatorLayout = binding.coordinator
        var contentView = view
        if (layoutResId != 0 && contentView == null) {
            contentView = layoutInflater.inflate(layoutResId, coordinator, false)
        }
        val sheetView = binding.designBottomSheet
        bottomSheetView = sheetView
        val sheetBehavior = BottomSheetBehaviorFixed.from(sheetView)
        behavior = sheetBehavior
        sheetBehavior.addBottomSheetCallback(bottomSheetCallback)
        sheetBehavior.isHideable = cancelable
        if (params == null) {
            sheetView.addView(contentView)
        } else {
            sheetView.addView(contentView, params)
        }
        binding.touchOutside.setOnClickListener {
            if (cancelable && isShowing && shouldWindowCloseOnTouchOutside()) {
                cancel()
            }
        }
        ViewCompat.setAccessibilityDelegate(sheetView, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (cancelable) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_DISMISS)
                    info.isDismissable = true
                } else {
                    info.isDismissable = false
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfoCompat.ACTION_DISMISS && cancelable) {
                    cancel()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })
        sheetView.setOnTouchListener { _, _ -> true }
        return container
    }

    private fun shouldWindowCloseOnTouchOutside(): Boolean {
        if (!canceledOnTouchOutsideSet) {
            val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowCloseOnTouchOutside))
            canceledOnTouchOutside = a.getBoolean(0, true)
            a.recycle()
            canceledOnTouchOutsideSet = true
        }
        return canceledOnTouchOutside
    }

    private val bottomSheetCallback = object : BottomSheetBehaviorFixed.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehaviorFixed.STATE_HIDDEN) {
                cancel()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    companion object {
        private fun getThemeResId(context: Context, themeId: Int): Int {
            var tid = themeId
            if (tid == 0) {
                val outValue = TypedValue()
                if (context.theme.resolveAttribute(MaterialR.attr.bottomSheetDialogTheme, outValue, true)) {
                    tid = outValue.resourceId
                } else {
                    tid = MaterialR.style.Theme_Design_Light_BottomSheetDialog
                }
            }
            return tid
        }
    }
}
