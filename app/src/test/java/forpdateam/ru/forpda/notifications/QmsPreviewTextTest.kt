package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила превращения HTML сообщения QMS в одну строку шторки.
 */
class QmsPreviewTextTest {

    @Test
    fun plainText_isUnwrappedAndTrimmed() {
        assertEquals("Привет!", QmsPreviewText.fromHtml("<div class=\"msg-content\">  Привет! </div>"))
    }

    @Test
    fun lineBreaks_collapseIntoSingleSpaces() {
        assertEquals(
                "первая строка вторая строка",
                QmsPreviewText.fromHtml("первая строка<br><br>вторая строка")
        )
    }

    @Test
    fun entities_areDecoded() {
        assertEquals("Кот & пёс <тут>", QmsPreviewText.fromHtml("Кот &amp; пёс &lt;тут&gt;"))
    }

    @Test
    fun smiles_keepTheirAltCode() {
        assertEquals(
                "Ну ты даёшь :D",
                QmsPreviewText.fromHtml("Ну ты даёшь <img src=\"/s/emot.gif\" alt=\":D\">")
        )
    }

    @Test
    fun imageWithoutAlt_becomesPlaceholder() {
        assertEquals(
                "Смотри [изображение]",
                QmsPreviewText.fromHtml("Смотри <img src=\"https://i.4pda.to/a.png\">")
        )
    }

    @Test
    fun quotedBlock_isDroppedSoTheAnswerFits() {
        assertEquals(
                "согласен",
                QmsPreviewText.fromHtml("<blockquote>длинная чужая цитата</blockquote>согласен")
        )
    }

    @Test
    fun emptyInput_givesEmptyString() {
        assertEquals("", QmsPreviewText.fromHtml(null))
        assertEquals("", QmsPreviewText.fromHtml("   "))
        assertEquals("", QmsPreviewText.fromHtml("<div></div>"))
    }

    @Test
    fun longText_isCutOnWordBoundaryWithEllipsis() {
        val word = "сообщение "
        val result = QmsPreviewText.fromHtml(word.repeat(60))
        assertTrue("длина=${result.length}", result.length <= QmsPreviewText.MAX_LENGTH + 1)
        assertTrue("хвост=${result.takeLast(12)}", result.endsWith("…"))
        assertTrue("обрыв посреди слова: $result", result.dropLast(1).endsWith("сообщение"))
    }

    @Test
    fun nonBreakingSpaces_areNormalized() {
        assertEquals("а б", QmsPreviewText.fromHtml("а&nbsp;&nbsp;б"))
    }
}
