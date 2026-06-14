package forpdateam.ru.forpda.presentation.qms.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QmsWebRenderProbeTest {

    @Test
    fun `parseRenderProbe reads escaped json payload`() {
        val raw = """"{\"page\":\"qms\",\"messList\":true,\"bridge\":true,\"domReady\":true,\"containers\":3}""""
        val probe = QmsWebRenderProbe.parseRenderProbe(raw)
        assertEquals("qms", probe.pageId)
        assertTrue(probe.messListPresent)
        assertTrue(probe.bridgeReady)
        assertTrue(probe.domReady)
        assertEquals(3, probe.containerCount)
    }

    @Test
    fun `isMessagesRendered requires full expected container count`() {
        assertTrue(QmsWebRenderProbe.isMessagesRendered(3, 3))
        assertTrue(QmsWebRenderProbe.isMessagesRendered(5, 3))
        assertFalse(QmsWebRenderProbe.isMessagesRendered(2, 3))
        assertFalse(QmsWebRenderProbe.isMessagesRendered(0, 3))
        assertTrue(QmsWebRenderProbe.isMessagesRendered(1, 0))
    }

    @Test
    fun `isMessagesRendered accepts relaxed threshold for visible partial batch`() {
        assertTrue(QmsWebRenderProbe.isMessagesRendered(4, 5))
        assertTrue(QmsWebRenderProbe.isMessagesRendered(8, 10))
        assertFalse(QmsWebRenderProbe.isMessagesRendered(3, 10))
    }

    @Test
    fun `hasVisibleMessages detects any rendered containers`() {
        assertTrue(QmsWebRenderProbe.hasVisibleMessages(1))
        assertFalse(QmsWebRenderProbe.hasVisibleMessages(0))
    }

    @Test
    fun `shouldResendOnZeroInjectCount detects parse_ok blank race`() {
        assertTrue(QmsWebRenderProbe.shouldResendOnZeroInjectCount(0, 5, true))
        assertFalse(QmsWebRenderProbe.shouldResendOnZeroInjectCount(0, 0, true))
        assertFalse(QmsWebRenderProbe.shouldResendOnZeroInjectCount(0, 5, false))
        assertFalse(QmsWebRenderProbe.shouldResendOnZeroInjectCount(3, 5, true))
        assertFalse(QmsWebRenderProbe.shouldResendOnZeroInjectCount(-1, 5, true))
    }

    @Test
    fun `buildShowMessagesScript wraps injection in try catch IIFE`() {
        val script = QmsWebRenderProbe.buildShowMessagesScript(
                messagesArg = "\"<div></div>\"",
                forceScroll = true,
                clearExisting = true
        )
        assertTrue(script.contains("resetQmsMessageList"))
        assertTrue(script.contains("showNewMess"))
        assertTrue(script.contains("countQmsMessageContainers"))
        assertTrue(script.contains("catch(e){return -1;}"))
    }

    @Test
    fun `collapse keeps incremental showNewMess scripts`() {
        val incremental = QmsWebRenderProbe.buildShowMessagesScript("\"<div></div>\"", false, clearExisting = false)
        val fullReset = QmsWebRenderProbe.buildShowMessagesScript("\"<div></div>\"", true, clearExisting = true)
        val collapsed = QmsWebRenderProbe.collapsePendingFullResetShowNewMessScripts(
                listOf(incremental, fullReset, incremental)
        )
        assertEquals(3, collapsed.size)
        assertTrue(collapsed.count { it.contains("showNewMess(") } == 3)
    }

    @Test
    fun `collapse keeps only last full reset showNewMess`() {
        val fullResetA = QmsWebRenderProbe.buildShowMessagesScript("\"<div>a</div>\"", true, clearExisting = true)
        val fullResetB = QmsWebRenderProbe.buildShowMessagesScript("\"<div>b</div>\"", true, clearExisting = true)
        val collapsed = QmsWebRenderProbe.collapsePendingFullResetShowNewMessScripts(listOf(fullResetA, fullResetB))
        assertEquals(1, collapsed.size)
        assertTrue(collapsed.single().contains("<div>b</div>"))
    }

    @Test
    fun `domReadyProbeScript accepts mess_list without isQmsChatPage`() {
        val script = QmsWebRenderProbe.domReadyProbeScript()
        assertTrue(script.contains(".mess_list"))
        assertTrue(script.contains("showNewMess"))
        assertTrue(script.contains("isQmsMessageListReady"))
    }

    @Test
    fun `bootstrapMissedDomContentLoadedScript invokes nativeEvents`() {
        val script = QmsWebRenderProbe.bootstrapMissedDomContentLoadedScript()
        assertTrue(script.contains("onNativeDomComplete"))
        assertTrue(script.contains("isQmsMessageListReady"))
    }

    @Test
    fun `qms chat fragment defers shell until tab shown`() {
        val fragment = File("src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt")
        val content = fragment.readText()
        assertTrue(content.contains("completeQmsRenderSuccess"))
        assertTrue(content.contains("hideQmsPersistentOverlays()"))
        assertTrue(content.contains("probeQmsRenderBeforeErrorOverlay"))
        assertTrue(content.contains("dispatchQmsShellLoad"))
        assertTrue(content.contains("shouldAcceptShellPageFinished"))
    }
}

