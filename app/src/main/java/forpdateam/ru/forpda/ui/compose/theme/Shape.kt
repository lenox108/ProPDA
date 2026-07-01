package forpdateam.ru.forpda.ui.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Скругления Compose-острова, зеркалят `ShapeAppearance.ForPDA.*` из
 * `res/values/styles.xml` (Small=10dp, Medium=16dp) + крупный радиус 28dp для
 * больших поверхностей.
 */
val ForpdaShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
