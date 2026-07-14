package forpdateam.ru.forpda.presentation.editpost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Регрессия «вложения пропадают в полноэкранном редакторе»: ShowForm эмитился до того, как фрагмент
 * успевал подписаться (start() зовётся из onViewCreated, collect стартует с onStart), а SharedFlow
 * без replay молча роняет событие при нуле подписчиков. Вложения приходят в UI только из ShowForm,
 * поэтому терялись именно они.
 */
class EditPostUiEventDeliveryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `plain emit before subscription drops the event`() = runTest(dispatcher) {
        val events = MutableSharedFlow<String>()
        val received = mutableListOf<String>()

        events.emit("ShowForm") // подписчиков ещё нет

        val collector = launch { events.collect { received.add(it) } }
        runCurrent()
        collector.cancel()

        assertEquals(emptyList<String>(), received)
    }

    @Test
    fun `awaiting a subscriber before emit delivers the event`() = runTest(dispatcher) {
        val events = MutableSharedFlow<String>()
        val received = mutableListOf<String>()

        val emitter = launch {
            if (events.subscriptionCount.value == 0) {
                events.subscriptionCount.first { it > 0 }
            }
            events.emit("ShowForm")
        }

        val collector = launch { events.collect { received.add(it) } }
        runCurrent()
        emitter.join()
        runCurrent()
        collector.cancel()

        assertEquals(listOf("ShowForm"), received)
    }
}
