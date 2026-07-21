package forpdateam.ru.forpda.model.repository.events

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeConnectPolicyTest {

    private fun open(
            foreground: Boolean = true,
            network: Boolean = true,
            auth: Boolean = true,
            wantsPush: Boolean = true,
            realtimeScreen: Boolean = false,
    ) = RealtimeConnectPolicy.shouldOpenWebSocket(foreground, network, auth, wantsPush, realtimeScreen)

    @Test
    fun `opens only when foreground, online, authorized and notifications wanted`() {
        assertTrue(open())
    }

    @Test
    fun `never opens in background`() {
        assertFalse(open(foreground = false))
    }

    @Test
    fun `never opens without network`() {
        assertFalse(open(network = false))
    }

    @Test
    fun `never opens for an anonymous user`() {
        assertFalse(open(auth = false))
    }

    /**
     * The regression this policy exists for: `stop()` leaves `foregroundRealtimeEnabled` set, so after
     * NotificationsService killed the socket on a disabled push preference, a network or auth change
     * used to re-open it — 45-second pings with nothing to deliver.
     */
    @Test
    fun `never opens while push notifications are disabled, even in a live foreground session`() {
        assertFalse(open(wantsPush = false))
    }

    @Test
    fun `disabled push wins over every other reason to connect`() {
        for (foreground in listOf(true, false)) {
            for (network in listOf(true, false)) {
                for (auth in listOf(true, false)) {
                    assertFalse(open(foreground, network, auth, wantsPush = false))
                }
            }
        }
    }

    /**
     * P1: an open QMS chat (a foreground realtime screen) needs a live socket for instant message
     * delivery even with push notifications disabled — the screen override beats the push toggle.
     */
    @Test
    fun `an active realtime screen opens the socket even with push disabled`() {
        assertTrue(open(wantsPush = false, realtimeScreen = true))
    }

    @Test
    fun `the realtime-screen override still respects foreground, network and auth`() {
        assertFalse(open(foreground = false, wantsPush = false, realtimeScreen = true))
        assertFalse(open(network = false, wantsPush = false, realtimeScreen = true))
        assertFalse(open(auth = false, wantsPush = false, realtimeScreen = true))
    }
}
