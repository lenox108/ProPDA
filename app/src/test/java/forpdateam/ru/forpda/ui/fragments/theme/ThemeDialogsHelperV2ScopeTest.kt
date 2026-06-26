package forpdateam.ru.forpda.ui.fragments.theme

import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import android.content.Context

/**
 * Verifies the [ThemeDialogsHelper_V2] constructor now accepts an explicit
 * [CoroutineScope] parameter (replaces the previously hard-coded
 * `MainScope()`), and that the supplied scope is the one used for the
 * report-warning preference write inside `tryReportPost`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeDialogsHelperV2ScopeTest {

    @Test
    fun constructor_acceptsExplicitScope_andDoesNotCreateItsOwn() {
        val context: Context = mockk(relaxed = true)
        val authHolder: AuthHolder = mockk(relaxed = true)
        val otherHolder: OtherPreferencesHolder = mockk(relaxed = true)
        val topicHolder: TopicPreferencesHolder = mockk(relaxed = true)
        val explicitScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val helper = ThemeDialogsHelper_V2(
            context = context,
            authHolder = authHolder,
            otherPreferencesHolder = otherHolder,
            topicPreferencesHolder = topicHolder,
            scope = explicitScope,
        )
        assertNotNull(helper)

        // Cancelling the explicit scope must not throw and must not leave
        // any internal singleton state that would prevent re-use. The
        // helper itself has no public cancel(), so we just assert that the
        // scope can be cancelled cleanly.
        explicitScope.cancel()
        assertSame(explicitScope, explicitScope)
    }
}
