package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.util.AttributeSet

/**
 * Created by isanechek on 8/12/17.
 */
class DetailsTransition : TransitionSet {
    constructor() {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        ordering = ORDERING_TOGETHER
        addTransition(ChangeBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeImageTransform())
    }
}
