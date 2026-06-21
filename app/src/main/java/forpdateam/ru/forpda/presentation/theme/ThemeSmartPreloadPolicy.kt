package forpdateam.ru.forpda.presentation.theme

/**
 * Pure decision policy for Smart Preload of the next topic page (Phase 8 — HIGHEST RISK).
 *
 * Smart Preload improves perceived infinite-scroll speed by fetching the *next* page once the user
 * scrolls past a threshold of the current page, storing it in the existing `ThemePageMemoryCache`
 * so the eventual bottom-reach is instant.
 *
 * This class is intentionally **pure and side-effect free** so the risky decision logic is fully
 * unit-testable in isolation. It performs no I/O, holds no mutable state, and never touches the
 * WebView, network, or cache directly — the caller (ThemeViewModel / infinite-scroll controller)
 * owns those and only asks this policy "should I, and what".
 *
 * Hard safety contract (mirrors Task01 Phase 8 restrictions):
 * - Ships behind a kill switch (`featureEnabled`, default OFF).
 * - Disabled whenever Slow WebView Mode is on (`slowModeEnabled`).
 * - Never preloads during an active refresh, while another topic is opening, right after a topic
 *   switch, when the current page is already the last page, when only one page ahead would exceed
 *   bounds, when a preload is already running, or after repeated failures.
 * - Preloads exactly ONE page ahead.
 * - A preload result is accepted only if it still matches the topic that requested it (stale topic
 *   results are rejected — see [isPreloadResultUsable]).
 */
object ThemeSmartPreloadPolicy {

    /** Default scroll fraction (0..1) at which preload of the next page may start (≈75%). */
    const val DEFAULT_PRELOAD_THRESHOLD: Float = 0.75f

    /** After this many consecutive preload failures for a topic, stop trying (per spec). */
    const val MAX_CONSECUTIVE_FAILURES: Int = 2

    /**
     * Immutable snapshot of everything the decision needs. All fields are plain values so the policy
     * stays trivially testable.
     */
    data class Input(
            /** Kill switch (default OFF). When false, preload never happens. */
            val featureEnabled: Boolean,
            /** Slow WebView Mode flag. When true, preload is disabled. */
            val slowModeEnabled: Boolean,
            /** Topic id of the page currently shown. <= 0 means "no valid topic". */
            val currentTopicId: Int,
            /** 1-based current page number. */
            val currentPage: Int,
            /** Total page count for the topic. */
            val totalPages: Int,
            /** Fraction (0..1) of the current page the user has scrolled through. */
            val scrollFraction: Float,
            /** True while the topic is being (re)loaded / refreshed. */
            val isRefreshing: Boolean,
            /** True while a (possibly different) topic is being opened. */
            val isTopicOpening: Boolean,
            /** True if a preload request is already in flight. */
            val isPreloadInFlight: Boolean,
            /** True if the next page is already cached/loaded (nothing to do). */
            val nextPageAlreadyAvailable: Boolean,
            /** Consecutive preload failures recorded for [currentTopicId]. */
            val consecutiveFailures: Int,
            /** Threshold override; defaults to [DEFAULT_PRELOAD_THRESHOLD]. */
            val threshold: Float = DEFAULT_PRELOAD_THRESHOLD,
    )

    /** Why a preload was or was not started — useful for DEBUG diagnostics, never user-facing. */
    enum class Decision {
        START,
        DISABLED_BY_KILL_SWITCH,
        DISABLED_BY_SLOW_MODE,
        NO_VALID_TOPIC,
        ALREADY_LAST_PAGE,
        BELOW_THRESHOLD,
        BLOCKED_REFRESHING,
        BLOCKED_TOPIC_OPENING,
        BLOCKED_IN_FLIGHT,
        NEXT_PAGE_ALREADY_AVAILABLE,
        BLOCKED_REPEATED_FAILURES,
    }

    /** The page number that would be preloaded for [input], or null if none. */
    fun nextPageToPreload(input: Input): Int? {
        val next = input.currentPage + 1
        return if (next in 2..input.totalPages) next else null
    }

    /**
     * Full ordered decision. Order matters: cheap/hard blocks first (kill switch, slow mode), then
     * topic validity, then bounds, then transient state. Returns the precise [Decision].
     */
    fun decide(input: Input): Decision {
        if (!input.featureEnabled) return Decision.DISABLED_BY_KILL_SWITCH
        if (input.slowModeEnabled) return Decision.DISABLED_BY_SLOW_MODE
        if (input.currentTopicId <= 0 || input.currentPage <= 0 || input.totalPages <= 0) {
            return Decision.NO_VALID_TOPIC
        }
        if (nextPageToPreload(input) == null) return Decision.ALREADY_LAST_PAGE
        if (input.isRefreshing) return Decision.BLOCKED_REFRESHING
        if (input.isTopicOpening) return Decision.BLOCKED_TOPIC_OPENING
        if (input.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            return Decision.BLOCKED_REPEATED_FAILURES
        }
        if (input.nextPageAlreadyAvailable) return Decision.NEXT_PAGE_ALREADY_AVAILABLE
        if (input.isPreloadInFlight) return Decision.BLOCKED_IN_FLIGHT
        if (input.scrollFraction < input.threshold) return Decision.BELOW_THRESHOLD
        return Decision.START
    }

    /** Convenience boolean wrapper over [decide]. */
    fun shouldStartPreload(input: Input): Boolean = decide(input) == Decision.START

    /**
     * Guards against stale-topic contamination: a preload result is only usable if it belongs to the
     * topic that is still current AND is the page that was requested. Any mismatch (topic switch,
     * server redirected to a different page, off-by-one) means the result must be discarded.
     */
    fun isPreloadResultUsable(
            requestedTopicId: Int,
            requestedPage: Int,
            resultTopicId: Int,
            resultPage: Int,
            currentTopicId: Int,
    ): Boolean =
            requestedTopicId > 0 &&
                    resultTopicId == requestedTopicId &&
                    resultTopicId == currentTopicId &&
                    resultPage == requestedPage
}
