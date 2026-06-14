package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MovedTopicResolverTest {

    @Before
    fun setUp() {
        MovedTopicResolver.clearForTests()
    }

    @After
    fun tearDown() {
        MovedTopicResolver.clearForTests()
    }

    @Test
    fun unknownIdResolvesToNull() {
        assertNull(MovedTopicResolver.resolve(123))
    }

    @Test
    fun rememberedIdResolvesToNewId() {
        MovedTopicResolver.remember(oldTopicId = 1121632, newTopicId = 1121568)
        assertEquals(1121568, MovedTopicResolver.resolve(1121632))
    }

    @Test
    fun ignoresIdenticalMappings() {
        MovedTopicResolver.remember(oldTopicId = 99, newTopicId = 99)
        assertNull(MovedTopicResolver.resolve(99))
    }

    @Test
    fun ignoresNonPositiveIds() {
        MovedTopicResolver.remember(oldTopicId = 0, newTopicId = 5)
        MovedTopicResolver.remember(oldTopicId = 5, newTopicId = -1)
        assertNull(MovedTopicResolver.resolve(0))
        assertNull(MovedTopicResolver.resolve(5))
    }
}
