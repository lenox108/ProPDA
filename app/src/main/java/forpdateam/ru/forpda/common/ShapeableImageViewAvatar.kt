package forpdateam.ru.forpda.common

import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel

/** Matches forum setting «Круглые аватарки» / [forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder.getCircleAvatars]. */
fun ShapeableImageView.applyForumAvatarShape(circle: Boolean) {
    shapeAppearanceModel = ShapeAppearanceModel.builder()
            .apply {
                if (circle) {
                    setAllCornerSizes(RelativeCornerSize(0.5f))
                } else {
                    setAllCornerSizes(0f)
                }
            }
            .build()
}
