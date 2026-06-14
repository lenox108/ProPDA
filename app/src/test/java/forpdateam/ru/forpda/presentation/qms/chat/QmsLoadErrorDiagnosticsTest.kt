package forpdateam.ru.forpda.presentation.qms.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsLoadErrorDiagnosticsTest {

    @Test
    fun `parser debug snackbar includes trace prefix`() {
        val message = formatQmsDebugSnackbarMessage(
                kind = QmsLoadErrorKind.PARSER,
                failureDetail = "parser_empty_thread:selector_mismatch_container",
                baseMessage = "Не удалось разобрать диалог",
                traceId = "trace-mismatch-99",
                debugBuild = true,
        )
        assertNotNull(message)
        assertTrue(message!!.contains("trace=trace-mi"))
        assertTrue(message.contains("selector_mismatch_container"))
    }

    @Test
    fun `parser debug snackbar keeps trace when failure detail equals base`() {
        val base = "Не удалось разобрать диалог"
        val message = formatQmsDebugSnackbarMessage(
                kind = QmsLoadErrorKind.PARSER,
                failureDetail = base,
                baseMessage = base,
                traceId = "abcd1234efgh",
                debugBuild = true,
        )
        assertEquals("$base — trace=abcd1234", message)
    }

    @Test
    fun `parser release build omits snackbar detail`() {
        assertNull(
                formatQmsDebugSnackbarMessage(
                        kind = QmsLoadErrorKind.PARSER,
                        failureDetail = "parser_empty_thread:selector_mismatch_container",
                        baseMessage = "Parser error",
                        traceId = "trace-xyz",
                        debugBuild = false,
                )
        )
    }

    @Test
    fun `non-parser debug snackbar omits trace`() {
        val message = formatQmsDebugSnackbarMessage(
                kind = QmsLoadErrorKind.NETWORK,
                failureDetail = "offline",
                baseMessage = "Нет сети",
                traceId = "trace-should-not-appear",
                debugBuild = true,
        )
        assertEquals("Нет сети — offline", message)
        assertTrue(message?.contains("trace=") == false)
    }
}
