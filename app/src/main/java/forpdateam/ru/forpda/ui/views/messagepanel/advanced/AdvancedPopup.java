package forpdateam.ru.forpda.ui.views.messagepanel.advanced;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.core.view.WindowCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import forpdateam.ru.forpda.App;
import forpdateam.ru.forpda.R;
import forpdateam.ru.forpda.ui.DimensionHelper;
import forpdateam.ru.forpda.ui.DimensionsProvider;
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel;
import io.reactivex.disposables.CompositeDisposable;

/**
 * BBCode и смайлы.
 * <p>
 * Полная форма (редактор поста): {@link BottomSheetDialog} — отдельное окно, не участвует в борьбе
 * с IME внутри CardView (избегаем «панель уехала, клавиатура внизу»).
 * Компактная форма (ответ в теме): {@link PopupWindow} как раньше.
 */
public class AdvancedPopup {
    private PopupWindow popupWindow;
    private BottomSheetDialog formatSheet;
    private View bottomSheetView;
    private final boolean fullFormEditor;
    private ViewGroup fragmentContainer;
    private View popupAnchorAboveControls;
    private boolean isShowingKeyboard = false;
    private StateListener stateListener;
    private MessagePanel messagePanel;
    private Context context;

    private DimensionsProvider dimensionsProvider = App.get().Di().getDimensionsProvider();
    private CompositeDisposable disposables = new CompositeDisposable();

    public AdvancedPopup(Context context, MessagePanel panel, boolean fullForm) {
        this.context = context;
        this.fullFormEditor = fullForm;
        fragmentContainer = panel.getFragmentContainer();
        messagePanel = panel;
        popupAnchorAboveControls = panel.findViewById(R.id.message_wrapper);

        View popupView = View.inflate(context, R.layout.message_panel_advanced, null);
        ViewPager viewPager = popupView.findViewById(R.id.pager);

        List<BasePanelItem> viewList = new ArrayList<>();
        viewList.add(new CodesPanelItem(context, messagePanel));
        viewList.add(new SmilesPanelItem(context, messagePanel));
        viewPager.setAdapter(new MyPagerAdapter(viewList));

        ((TabLayout) popupView.findViewById(R.id.tab_layout)).setupWithViewPager(viewPager);

        popupView.findViewById(R.id.keyboard_button).setOnClickListener(v -> {
            // Toggle: если IME уже открыт — закрываем; иначе — закрываем панель и показываем IME.
            if (dimensionsProvider.getDimensions().isKeyboardShow()) {
                messagePanel.hideImeFromEditor();
            } else {
                hidePopup();
                messagePanel.showKeyboard();
            }
        });

        popupView.findViewById(R.id.delete_button).setOnClickListener(v -> {
            EditText messageField = messagePanel.getMessageField();
            int selectionStart = messageField.getSelectionStart();
            int selectionEnd = messageField.getSelectionEnd();
            if (selectionEnd < selectionStart && selectionEnd != -1) {
                int c = selectionStart;
                selectionStart = selectionEnd;
                selectionEnd = c;
            }
            if (selectionStart != -1 && selectionStart != selectionEnd) {
                messageField.getText().delete(selectionStart, selectionEnd);
                return;
            }
            if (selectionStart > 0) {
                messageField.getText().delete(selectionStart - 1, selectionStart);
            }
        });

        if (fullFormEditor) {
            formatSheet = new BottomSheetDialog(context);
            formatSheet.setContentView(popupView);
            formatSheet.setOnDismissListener(dialog -> {
                DimensionHelper.Dimensions d = dimensionsProvider.getDimensions();
                if (d.isFakeKeyboardShow()) {
                    d.setFakeKeyboardShow(false);
                    dimensionsProvider.update(d);
                }
                messagePanel.getAdvancedButton().setImageDrawable(App.getVecDrawable(context, R.drawable.ic_add));
                if (fragmentContainer.getPaddingBottom() != 0) {
                    fragmentContainer.setPadding(
                            fragmentContainer.getPaddingLeft(),
                            fragmentContainer.getPaddingTop(),
                            fragmentContainer.getPaddingRight(),
                            0
                    );
                }
                if (stateListener != null) {
                    stateListener.onHide();
                }
                messagePanel.setCanScrolling(true);
            });
            Window w = formatSheet.getWindow();
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            bottomSheetView = formatSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetView != null) {
                // Высота контейнера — на весь экран (для жеста «дотянуть до конца»), видимая доля — через peek/state.
                bottomSheetView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetView);
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                int minKb = context.getResources().getDimensionPixelSize(R.dimen.default_keyboard_height);
                // ~половина экрана, как у встроенной панели; не меньше минимальной «клавиатурной» высоты
                int peek = Math.max(minKb, (int) (dm.heightPixels * 0.48f));
                behavior.setPeekHeight(peek);
                behavior.setFitToContents(false);
                behavior.setSkipCollapsed(false);
                // Промежуточная позиция при перетягивании вверх (~середина между peek и полным экраном)
                behavior.setHalfExpandedRatio(0.5f);
            }
            popupWindow = null;
        } else {
            formatSheet = null;
            popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT,
                    effectiveKeyboardPanelHeight(), false);
            popupWindow.setOnDismissListener(() -> {
                dimensionsProvider.getDimensions().setFakeKeyboardShow(false);
                dimensionsProvider.update(dimensionsProvider.getDimensions());
            });
        }

        messagePanel.addAdvancedOnClickListener(v -> {
            if (isAdvancedPopupShowing()) {
                hidePopup();
            } else {
                showPopup();
            }
        });
        disposables.add(
                dimensionsProvider
                        .observeDimensions()
                        .subscribe(dimensions -> {
                            if (messagePanel != null) {
                                messagePanel.post(() -> {
                                    if (messagePanel != null) {
                                        updateDimens(dimensions);
                                    }
                                });
                            }
                            updateDimens(dimensions);
                        })
        );
    }

    private boolean isAdvancedPopupShowing() {
        if (formatSheet != null) {
            return formatSheet.isShowing();
        }
        return popupWindow != null && popupWindow.isShowing();
    }

    private int effectiveKeyboardPanelHeight() {
        int kh = dimensionsProvider.getDimensions().getSavedKeyboardHeight();
        int minH = context.getResources().getDimensionPixelSize(R.dimen.default_keyboard_height);
        return Math.max(kh, minH);
    }

    /**
     * Когда IME скрыт, MainActivity снова даёт padding под нижнюю навигацию. Дополнительный
     * подъём контента под панель BBCode — только на (высота «клавиатуры» − высота навбара),
     * иначе сумма с отступом активности дублирует ~48dp и панель ответа уезжает слишком вверх.
     */
    private int paddingBottomForFakeKeyboardOverNav(DimensionHelper.Dimensions dimensions) {
        int kh = Math.max(dimensions.getSavedKeyboardHeight(),
                context.getResources().getDimensionPixelSize(R.dimen.default_keyboard_height));
        int nav = context.getResources().getDimensionPixelSize(R.dimen.dp48);
        return Math.max(0, kh - nav);
    }

    private void updateDimens(DimensionHelper.Dimensions dimensions) {
        if (messagePanel == null) {
            return;
        }
        if (fullFormEditor) {
            return;
        }
        if (popupWindow == null) {
            return;
        }
        if (dimensions.isKeyboardShow()) {
            if (fragmentContainer.getPaddingBottom() != 0) {
                fragmentContainer.setPadding(
                        fragmentContainer.getPaddingLeft(),
                        fragmentContainer.getPaddingTop(),
                        fragmentContainer.getPaddingRight(),
                        0
                );
            }
            int kh = Math.max(dimensions.getSavedKeyboardHeight(),
                    context.getResources().getDimensionPixelSize(R.dimen.default_keyboard_height));
            if (popupWindow.isShowing()) {
                popupWindow.setHeight(kh);
                popupWindow.update();
            }
            isShowingKeyboard = true;
        } else {
            if (isShowingKeyboard) {
                isShowingKeyboard = false;
                if (popupWindow.isShowing() && dimensions.isFakeKeyboardShow()) {
                    int pb = paddingBottomForFakeKeyboardOverNav(dimensions);
                    if (fragmentContainer.getPaddingBottom() != pb) {
                        fragmentContainer.setPadding(
                                fragmentContainer.getPaddingLeft(),
                                fragmentContainer.getPaddingTop(),
                                fragmentContainer.getPaddingRight(),
                                pb
                        );
                    }
                }
            }
        }
        messagePanel.setCanScrolling(!(isShowingKeyboard || isAdvancedPopupShowing()));
    }

    private void hidePopup() {
        if (formatSheet != null) {
            if (formatSheet.isShowing()) {
                formatSheet.dismiss();
            }
            return;
        }

        DimensionHelper.Dimensions localDimensions = dimensionsProvider.getDimensions();
        messagePanel.getAdvancedButton().setImageDrawable(App.getVecDrawable(context, R.drawable.ic_add));

        if (localDimensions.isFakeKeyboardShow()) {
            localDimensions.setFakeKeyboardShow(false);
            dimensionsProvider.update(localDimensions);
        }

        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        if (fragmentContainer.getPaddingBottom() != 0) {
            fragmentContainer.setPadding(
                    fragmentContainer.getPaddingLeft(),
                    fragmentContainer.getPaddingTop(),
                    fragmentContainer.getPaddingRight(),
                    0
            );
        }

        if (stateListener != null) {
            stateListener.onHide();
        }

        messagePanel.setCanScrolling(true);
    }

    private void showPopup() {
        DimensionHelper.Dimensions localDimensions = dimensionsProvider.getDimensions();
        messagePanel.getAdvancedButton().setImageDrawable(App.getVecDrawable(context, R.drawable.ic_keyboard));

        if (!localDimensions.isFakeKeyboardShow()) {
            localDimensions.setFakeKeyboardShow(true);
            dimensionsProvider.update(localDimensions);
        }

        if (formatSheet != null) {
            if (!formatSheet.isShowing()) {
                formatSheet.show();
                // Старт на высоте peek (~половина экрана), не expanded; вверх можно дотянуть до half-expanded / full.
                if (bottomSheetView != null) {
                    bottomSheetView.post(() -> {
                        if (bottomSheetView == null || formatSheet == null || !formatSheet.isShowing()) {
                            return;
                        }
                        BottomSheetBehavior.from(bottomSheetView).setState(BottomSheetBehavior.STATE_COLLAPSED);
                    });
                }
            }
            if (stateListener != null) {
                stateListener.onShow();
            }
            messagePanel.setCanScrolling(false);
            return;
        }

        if (!isShowingKeyboard) {
            int pb = paddingBottomForFakeKeyboardOverNav(localDimensions);
            if (fragmentContainer.getPaddingBottom() != pb) {
                fragmentContainer.setPadding(
                        fragmentContainer.getPaddingLeft(),
                        fragmentContainer.getPaddingTop(),
                        fragmentContainer.getPaddingRight(),
                        pb
                );
            }
        } else {
            fragmentContainer.setPadding(
                    fragmentContainer.getPaddingLeft(),
                    fragmentContainer.getPaddingTop(),
                    fragmentContainer.getPaddingRight(),
                    0
            );
        }

        if (popupWindow != null && !popupWindow.isShowing()) {
            messagePanel.post(() -> {
                if (popupWindow == null || messagePanel == null || popupWindow.isShowing()) {
                    return;
                }
                int kh = effectiveKeyboardPanelHeight();
                popupWindow.setHeight(kh);
                View anchor = popupAnchorAboveControls != null ? popupAnchorAboveControls : fragmentContainer;
                popupWindow.showAtLocation(anchor, Gravity.BOTTOM, 0, 0);
            });
        }

        if (stateListener != null) {
            stateListener.onShow();
        }

        messagePanel.setCanScrolling(false);
    }


    public boolean onBackPressed() {
        if (!isAdvancedPopupShowing()) {
            return false;
        }
        hidePopup();
        return true;
    }

    public void onResume() {
    }

    public void onPause() {
        hidePopup();
    }

    public void onDestroy() {
        disposables.dispose();
        hidePopup();
    }

    public void hidePopupWindows() {
        hidePopup();
    }

    public void setStateListener(StateListener stateListener) {
        this.stateListener = stateListener;
    }

    public interface StateListener {
        void onShow();

        void onHide();
    }

    private class MyPagerAdapter extends PagerAdapter {
        List<BasePanelItem> pages = null;

        MyPagerAdapter(List<BasePanelItem> pages) {
            this.pages = pages;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = pages.get(position);
            container.addView(v, 0);
            return v;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return pages.get(position).getTitle();
        }
    }
}
