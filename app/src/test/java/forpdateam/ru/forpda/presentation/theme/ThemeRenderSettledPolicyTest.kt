package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRenderSettledPolicyTest {

    @Test
    fun blockingScrollKinds_includeInitialAnchorAndRestore() {
        assertTrue(
                ThemeRenderSettledPolicy.isBlockingScrollKind(ThemeScrollCommand.Kind.INITIAL_ANCHOR)
        )
        assertTrue(
                ThemeRenderSettledPolicy.isBlockingScrollKind(ThemeScrollCommand.Kind.REFRESH_RESTORE)
        )
        assertTrue(
                ThemeRenderSettledPolicy.isBlockingScrollKind(ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM)
        )
        assertFalse(ThemeRenderSettledPolicy.isBlockingScrollKind(ThemeScrollCommand.Kind.BOTTOM))
    }
}
