package forpdateam.ru.forpda.notifications

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Диагностика «Мгновенного канала» одним тапом: DNS → HTTPS → WebSocket-хендшейк → вердикт.
 * Отвечает на полевой вопрос «почему не соединяется: провайдер, VPN, Private DNS или сам хост?»
 * без ручной возни с браузером. Классическая сигнатура DPI: HTTPS к app.4pda.to проходит
 * (любой HTTP-код = TCP+TLS живы), а ws-хендшейк молча дропается по таймауту.
 *
 * Свой лёгкий OkHttpClient: без кук, интерсепторов и кэша приложения — проба должна мерить
 * сеть, а не наше окружение. Вызывать на IO-потоке.
 */
object RealtimeChannelProbe {

    private const val HOST = "app.4pda.to"
    private const val WS_URL = "wss://app.4pda.to/ws/"
    private const val TIMEOUT_S = 6L

    data class ProbeReport(val lines: List<String>, val verdict: String)

    private fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    fun run(): ProbeReport {
        val lines = mutableListOf<String>()

        // 1. DNS: не резолвится → Private DNS/блокировщик/резолвер сети.
        val dnsOk = runCatching { InetAddress.getAllByName(HOST) }
                .fold(
                        onSuccess = { addrs ->
                            lines += "DNS: ok (${addrs.joinToString { it.hostAddress ?: "?" }})"
                            true
                        },
                        onFailure = { e ->
                            lines += "DNS: FAIL ${e.javaClass.simpleName}"
                            false
                        }
                )

        val probeClient = client()

        // 2. HTTPS до того же хоста: ЛЮБОЙ HTTP-код (хоть 400) = TCP+TLS проходят.
        val httpsOk = if (!dnsOk) {
            lines += "HTTPS $HOST: пропущено (нет DNS)"
            false
        } else {
            runCatching {
                probeClient.newCall(Request.Builder().url("https://$HOST/").build())
                        .execute().use { it.code }
            }.fold(
                    onSuccess = { code ->
                        lines += "HTTPS $HOST: ok (HTTP $code)"
                        true
                    },
                    onFailure = { e ->
                        lines += "HTTPS $HOST: FAIL ${e.javaClass.simpleName}"
                        false
                    }
            )
        }

        // 3. Собственно WebSocket-хендшейк.
        var wsError: String? = null
        var wsOk = false
        if (dnsOk) {
            val latch = CountDownLatch(1)
            val ws = probeClient.newWebSocket(
                    Request.Builder().url(WS_URL).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            wsOk = true
                            webSocket.cancel()
                            latch.countDown()
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            wsError = t.javaClass.simpleName +
                                    (response?.code?.let { " (HTTP $it)" } ?: "")
                            latch.countDown()
                        }
                    }
            )
            if (!latch.await(TIMEOUT_S + 2, TimeUnit.SECONDS)) {
                wsError = "timeout"
                ws.cancel()
            }
            lines += if (wsOk) "WebSocket: ok" else "WebSocket: FAIL ${wsError ?: "?"}"
        } else {
            lines += "WebSocket: пропущено (нет DNS)"
        }

        // 4. Контроль: базовый сайт (виден ли форум вообще из этой сети).
        val siteOk = runCatching {
            probeClient.newCall(Request.Builder().url("https://4pda.to/").build())
                    .execute().use { it.code }
        }.fold(
                onSuccess = { code ->
                    lines += "HTTPS 4pda.to: ok (HTTP $code)"
                    true
                },
                onFailure = { e ->
                    lines += "HTTPS 4pda.to: FAIL ${e.javaClass.simpleName}"
                    false
                }
        )

        val verdict = when {
            wsOk -> "Канал доступен из этой сети — проблема была временной, соединение должно установиться."
            !dnsOk && siteOk -> "DNS не резолвит $HOST, хотя 4pda.to доступен: похоже на Private DNS или блокировщик рекламы — отключите и повторите."
            !dnsOk -> "DNS не работает в этой сети."
            httpsOk -> "TCP и TLS до $HOST проходят, но WebSocket-хендшейк режется: типично для DPI оператора или VPN. Попробуйте другую сеть / выключить VPN."
            siteOk -> "$HOST недоступен из этой сети (4pda.to при этом открывается): узел блокируется провайдером/VPN."
            else -> "Сеть недоступна или форум блокируется целиком."
        }
        return ProbeReport(lines, verdict)
    }
}
