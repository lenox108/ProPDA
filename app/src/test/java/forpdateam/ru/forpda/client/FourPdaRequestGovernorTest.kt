package forpdateam.ru.forpda.client

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FourPdaRequestGovernorTest {

    @Before
    fun setUp() = FourPdaRequestGovernor.resetForTest()

    @After
    fun tearDown() = FourPdaRequestGovernor.resetForTest()

    @Test
    fun `user request passes while budget is free`() {
        FourPdaRequestGovernor.acquire(RequestPriority.USER)
        FourPdaRequestGovernor.acquire(RequestPriority.USER)
    }

    @Test
    fun `429 with retry-after starts cooldown`() {
        FourPdaRequestGovernor.onResponse(code = 429, retryAfterSeconds = 30L)

        assertTrue(FourPdaRequestGovernor.isCoolingDown())
        assertTrue(FourPdaRequestGovernor.cooldownRemainingMs() > 20_000L)
    }

    @Test
    fun `429 without retry-after still pauses background for the minimum window`() {
        FourPdaRequestGovernor.onResponse(code = 429, retryAfterSeconds = null)

        assertTrue(FourPdaRequestGovernor.cooldownRemainingMs() > 10_000L)
    }

    @Test
    fun `successful response leaves budget untouched`() {
        FourPdaRequestGovernor.onResponse(code = 200, retryAfterSeconds = null)

        assertFalse(FourPdaRequestGovernor.isCoolingDown())
    }

    @Test(expected = FourPdaRequestGovernor.BackgroundThrottledException::class)
    fun `background request is dropped during cooldown`() {
        FourPdaRequestGovernor.onResponse(code = 429, retryAfterSeconds = 60L)

        // Ждать до победного фон не будет: BACKGROUND_MAX_WAIT_MS много меньше минуты кулдауна.
        FourPdaRequestGovernor.acquire(RequestPriority.BACKGROUND)
    }

    @Test
    fun `user request still goes through during cooldown`() {
        FourPdaRequestGovernor.onResponse(code = 429, retryAfterSeconds = 60L)

        // Пользовательский запрос не отменяется — он лишь ждёт пополнения бюджета не дольше лимита.
        FourPdaRequestGovernor.acquire(RequestPriority.USER)
    }
}
