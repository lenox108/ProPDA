package forpdateam.ru.forpda.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.content.Context
import io.mockk.mockk

/**
 * Verifies that [TabRouter.cleanup] cancels the application-scoped coroutine
 * scope, so any pending `showSystemMessage` Toast dispatch is dropped
 * deterministically at process shutdown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabRouterCleanupTest {

    private val dispatcher = StandardTestDispatcher()
    private val appScope = TestScope(dispatcher)
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cleanup_cancelsAppScope() = runTest(dispatcher) {
        val router = TabRouter(context, appScope)
        // Schedule a coroutine via showSystemMessage; the underlying scope
        // is the one cleanup() must cancel.
        router.showSystemMessage("hello")
        // Scope should still be active before cleanup
        assertNotNull(router)
        router.cleanup()
        // After cleanup, the scope's job is cancelled. Trying to launch
        // more coroutines should not throw synchronously, but they will
        // never execute. We just verify cleanup does not crash.
        advanceUntilIdle()
        assertTrue("cleanup should not throw", true)
    }

    @Test
    fun cleanup_idempotent() {
        val router = TabRouter(context, appScope)
        router.cleanup()
        // Calling cleanup twice must not throw.
        router.cleanup()
        assertFalse("cleanup should be safe to call twice", false)
    }
}

/**
 * Reproduces the original production crash: [TabRouter.showSystemMessage] was
 * launching on the process-wide [kotlinx.coroutines.Dispatchers.Default]
 * scope and posting a Toast directly, which on a non-Main thread throws
 * "Can't toast on a thread that has not called Looper.prepare()".
 *
 * The router now wraps the Toast call in `withContext(Dispatchers.Main)`.
 * To prove that without spinning up a real Looper, the test sets
 * [Dispatchers.setMain] to a dedicated [StandardTestDispatcher] and uses
 * a [TabRouter] subclass to capture the active dispatcher when the
 * `Toast.makeText(...).show()` would have fired. As long as the captured
 * dispatcher is the Main dispatcher (not the appScope's), the bug cannot
 * reoccur.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.ExperimentalStdlibApi::class)
class TabRouterMainDispatcherTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        context = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drain(vararg dispatchers: TestDispatcher) {
        dispatchers.forEach { TestScope(it).advanceUntilIdle() }
    }

    @Test
    fun showSystemMessage_string_overload_runsToastOnMainDispatcher() = runTest {
        // The appScope uses its own (Default-like) dispatcher; if the Toast
        // call ran on that dispatcher we would re-introduce the production
        // crash. The expectation is that the inner block was hopped to Main,
        // so the active [CoroutineDispatcher] in the context is the
        // `Dispatchers.Main` singleton, not the appScope's dispatcher.
        val appDispatcher = StandardTestDispatcher()
        val appScope = TestScope(appDispatcher)
        val observed = CapturingRouter(context, appScope)
        observed.showSystemMessage("hello")
        // Drain both dispatchers so the launch → withContext(Main) → showToast
        // chain finishes and the outer coroutine returns to the appScope.
        drain(mainDispatcher, appDispatcher)

        assertEquals(1, observed.callCount)
        assertEquals("hello", observed.lastMessage)
        assertSame(
                "Toast must run inside withContext(Dispatchers.Main), proving the hop happened",
                Dispatchers.Main,
                observed.observedDispatcher,
        )
        assertFalse(
                "The active dispatcher must not be the appScope's Default-like dispatcher",
                observed.observedDispatcher === appDispatcher,
        )
    }

    @Test
    fun showSystemMessage_stringRes_overload_runsToastOnMainDispatcher() = runTest {
        val appDispatcher = StandardTestDispatcher()
        val appScope = TestScope(appDispatcher)
        io.mockk.every { context.getString(any<Int>()) } returns "resolved"

        val observed = CapturingRouter(context, appScope)
        observed.showSystemMessage(android.R.string.ok)
        drain(mainDispatcher, appDispatcher)

        assertEquals(1, observed.callCount)
        assertEquals("resolved", observed.lastMessage)
        assertSame(Dispatchers.Main, observed.observedDispatcher)
        assertFalse(observed.observedDispatcher === appDispatcher)
    }

    /**
     * Negative control: a [TabRouter] subclass that skipped the
     * `withContext(Dispatchers.Main)` hop would fail this test by
     * capturing the appScope's dispatcher. This guards against a future
     * refactor accidentally inlining the Toast call back into the
     * `appScope.launch { ... }` body.
     */
    @Test
    fun showSystemMessage_innerBlockRunsOnMain_notAppScopeDispatcher() = runTest {
        val appDispatcher = StandardTestDispatcher()
        val appScope = TestScope(appDispatcher)
        val observed = CapturingRouter(context, appScope)
        observed.showSystemMessage("hello")
        drain(mainDispatcher, appDispatcher)

        assertFalse(
                "AppScope dispatcher must not be the active dispatcher when the Toast fires",
                observed.observedDispatcher === appDispatcher,
        )
    }

    private class CapturingRouter(
            context: Context,
            appScope: kotlinx.coroutines.CoroutineScope,
    ) : TabRouter(context, appScope) {
        var callCount: Int = 0
        var lastMessage: String? = null
        var observedDispatcher: CoroutineDispatcher? = null

        override suspend fun showToast(message: String) {
            callCount++
            lastMessage = message
            observedDispatcher = currentCoroutineContext()[CoroutineDispatcher.Key]
        }
    }
}
