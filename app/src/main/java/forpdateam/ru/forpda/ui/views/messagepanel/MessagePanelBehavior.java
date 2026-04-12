package forpdateam.ru.forpda.ui.views.messagepanel;

import com.google.android.material.appbar.AppBarLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.cardview.widget.CardView;
import android.view.View;

import forpdateam.ru.forpda.App;

/**
 * Created by radiationx on 07.01.17.
 */

public class MessagePanelBehavior extends CoordinatorLayout.Behavior<CardView> {
    private boolean canScrolling = true;

    public MessagePanelBehavior() {
        super();
    }

    @Override
    public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final CardView child,
                                       final View directTargetChild, final View target, final int nestedScrollAxes) {
        if (!canScrolling)
            child.setTranslationY(0);
        return canScrolling;
    }


    public void setCanScrolling(boolean canScrolling) {
        this.canScrolling = canScrolling;
    }


    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, CardView child, View dependency) {
        return dependency instanceof AppBarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, CardView child, View dependency) {
        if (!canScrolling) return false;
        int depH = dependency.getMeasuredHeight();
        if (depH <= 0) {
            child.setTranslationY(0);
            return false;
        }
        // Без clamp при смене layout (IME, edge-to-edge) top/height дают percent < 0 или > 1 —
        // панель уезжает к верху экрана, а клавиатура остаётся внизу.
        float percent = 1.0f - ((float) -dependency.getTop() / (float) depH);
        if (percent < 0f) percent = 0f;
        else if (percent > 1f) percent = 1f;
        int scrolled = (int) ((child.getMeasuredHeight() + (2 * App.px8)) * percent);
        child.setTranslationY(scrolled);
        return true;
    }
}
