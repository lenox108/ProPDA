package forpdateam.ru.forpda.client

import forpdateam.ru.forpda.BuildConfig
import okhttp3.Dns
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS с кэшированием результатов для ускорения повторных запросов.
 * Кэш живет 30 секунд — оптимально для мобильного приложения:
 * достаточно долго для серии запросов к одному хосту,
 * достаточно коротко для обновления при смене сети.
 */
class CachedDns : Dns {
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val defaultDns = Dns.SYSTEM
    
    companion object {
        private const val CACHE_TTL_MS = 30_000L // 30 секунд
    }
    
    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long
    )
    
    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cached = cache[hostname]
        
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            if (BuildConfig.DEBUG) {
                Timber.d("DNS cache hit for $hostname")
            }
            return cached.addresses
        }
        
        // Кэш протух или отсутствует — делаем реальный DNS запрос
        return try {
            val addresses = defaultDns.lookup(hostname)
            cache[hostname] = CacheEntry(addresses, now)
            
            if (BuildConfig.DEBUG) {
                Timber.d("DNS cache miss for $hostname, resolved to ${addresses.size} addresses")
            }
            addresses
        } catch (e: Exception) {
            // Не кэшируем failed lookup — при следующем запросе попробуем снова
            // (сеть могла появиться, или DNS заработать)
            if (BuildConfig.DEBUG) {
                Timber.d("DNS lookup failed for $hostname, not caching: ${e.message}")
            }
            throw e
        }
    }
    
    /**
     * Очистить кэш DNS. Полезно при смене сети (WiFi ↔ Mobile).
     */
    fun clearCache() {
        cache.clear()
        if (BuildConfig.DEBUG) {
            Timber.d("DNS cache cleared")
        }
    }
}
