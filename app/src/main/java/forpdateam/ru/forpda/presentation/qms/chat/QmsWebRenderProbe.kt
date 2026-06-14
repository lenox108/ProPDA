package forpdateam.ru.forpda.presentation.qms.chat

/**
 * Builds and parses WebView probes for QMS chat message rendering.
 */
object QmsWebRenderProbe {

    private val intFieldRegex = Regex(""""(\w+)"\s*:\s*(-?\d+)""")
    private val boolFieldRegex = Regex(""""(\w+)"\s*:\s*(true|false)""")
    private val stringFieldRegex = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")

    /** DOM shell + qms.js helpers required before [showNewMess] injection. */
    fun domReadyProbeScript(): String =
            """(function(){
                if(typeof isQmsMessageListReady==='function'&&isQmsMessageListReady()){
                    return true;
                }
                return !!document.body&&
                    !!document.querySelector('.mess_list')&&
                    typeof showNewMess==='function'&&
                    typeof countQmsMessageContainers==='function';
            })();"""

    /**
     * [main.js] DOMContentLoaded → [IBase.domContentLoaded] can be missed after
     * [loadDataWithBaseURL] races with [WebView.stopLoading] / pause-resume.
     */
    fun bootstrapMissedDomContentLoadedScript(): String =
            """(function(){
                try{
                    if(typeof isQmsMessageListReady!=='function'||!isQmsMessageListReady()){
                        return false;
                    }
                    if(typeof nativeEvents!=='undefined'&&
                            typeof nativeEvents.onNativeDomComplete==='function'){
                        nativeEvents.onNativeDomComplete();
                    }
                    return true;
                }catch(e){
                    return false;
                }
            })();"""

    fun renderProbeScript(): String =
            """(function(){
                var page=document.body?document.body.id:'';
                var messList=!!document.querySelector('.mess_list');
                var bridge=typeof showNewMess==='function'&&typeof countQmsMessageContainers==='function';
                var domReady=typeof isQmsMessageListReady==='function'&&isQmsMessageListReady();
                var containers=bridge?countQmsMessageContainers():0;
                return JSON.stringify({
                    page:page,
                    messList:messList,
                    bridge:bridge,
                    domReady:domReady,
                    containers:containers
                });
            })();"""

    fun parseRenderProbe(raw: String?): QmsRenderProbeResult {
        if (raw.isNullOrBlank() || raw == "null") {
            return QmsRenderProbeResult()
        }
        val jsonString = decodeJsJsonPayload(raw) ?: return QmsRenderProbeResult()
        val ints = intFieldRegex.findAll(jsonString).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val bools = boolFieldRegex.findAll(jsonString).associate { it.groupValues[1] to (it.groupValues[2] == "true") }
        val strings = stringFieldRegex.findAll(jsonString).associate { it.groupValues[1] to it.groupValues[2] }
        return QmsRenderProbeResult(
                pageId = strings["page"].orEmpty(),
                messListPresent = bools["messList"] == true,
                bridgeReady = bools["bridge"] == true,
                domReady = bools["domReady"] == true,
                containerCount = ints["containers"] ?: 0
        )
    }

    fun decodeJsJsonPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        if (!trimmed.startsWith("\"")) return null
        return trimmed
                .removeSurrounding("\"")
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .takeIf { it.startsWith("{") }
    }

    /**
     * True when the DOM shows the loaded batch. Uses a relaxed threshold so a single
     * miscounted date row or deduped message does not leave the error overlay up while
     * bubbles are already visible (see QMS WebView render race).
     */
    fun isMessagesRendered(containerCount: Int, expectedContainers: Int): Boolean {
        if (expectedContainers <= 0) return true
        if (containerCount <= 0) return false
        if (containerCount >= expectedContainers) return true
        if (expectedContainers >= 4 && containerCount >= expectedContainers - 1) return true
        return containerCount >= (expectedContainers * 8 + 9) / 10
    }

    fun hasVisibleMessages(containerCount: Int): Boolean = containerCount > 0

    /**
     * Native batch is loaded (`parse_ok`) but [showNewMess] inject returned zero containers —
     * typical WebView render race (DOM/bridge ready too late or 0×0 layout).
     */
    fun shouldResendOnZeroInjectCount(
            injectCount: Int,
            expectedContainers: Int,
            hasLoadedMessages: Boolean
    ): Boolean = injectCount == 0 && expectedContainers > 0 && hasLoadedMessages

    fun buildShowMessagesScript(
            messagesArg: String,
            forceScroll: Boolean,
            clearExisting: Boolean
    ): String {
        val reset = if (clearExisting) "if(typeof resetQmsMessageList==='function'){resetQmsMessageList();}" else ""
        return """(function(){
            try{
                $reset
                if(typeof showNewMess!=='function'){return -1;}
                showNewMess($messagesArg,$forceScroll);
                return typeof countQmsMessageContainers==='function'?countQmsMessageContainers():0;
            }catch(e){return -1;}
        })();"""
    }

    /**
     * Collapse only interchangeable full-list resets (`resetQmsMessageList` + `showNewMess`).
     * Incremental `OnNewMessages` batches (`clearExisting=false`) are preserved.
     */
    fun collapsePendingFullResetShowNewMessScripts(scripts: List<String>): List<String> {
        val resetIndices = scripts.indices.filter { scripts[it].containsFullResetShowNewMess() }
        if (resetIndices.size <= 1) return scripts
        val lastReset = resetIndices.last()
        return scripts.filterIndexed { index, js ->
            !js.containsFullResetShowNewMess() || index == lastReset
        }
    }

    private fun String.containsFullResetShowNewMess(): Boolean =
            contains("resetQmsMessageList()") && contains("showNewMess(")
}

data class QmsRenderProbeResult(
        val pageId: String = "",
        val messListPresent: Boolean = false,
        val bridgeReady: Boolean = false,
        val domReady: Boolean = false,
        val containerCount: Int = 0
)
