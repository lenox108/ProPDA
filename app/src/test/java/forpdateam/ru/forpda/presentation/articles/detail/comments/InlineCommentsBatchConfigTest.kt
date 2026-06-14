package forpdateam.ru.forpda.presentation.articles.detail.comments

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCommentsBatchConfigTest {

    @Test
    fun batchSize_isTwenty() {
        assertEquals(20, InlineCommentsBatchConfig.BATCH_SIZE)
    }
}
