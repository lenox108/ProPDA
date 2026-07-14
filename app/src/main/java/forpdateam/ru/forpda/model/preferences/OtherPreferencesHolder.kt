package forpdateam.ru.forpda.model.preferences

import android.content.Context
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.entity.app.other.OtherMenuBlock
import forpdateam.ru.forpda.entity.app.other.QuickSetting
import forpdateam.ru.forpda.model.datastore.OtherDataStore
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OtherPreferencesHolder(
        private val context: Context
) {
    private val dataStore = OtherDataStore(context)

    suspend fun setAppFirstStart(value: Boolean) = dataStore.setAppFirstStart(value)

    suspend fun setAppVersionsHistory(value: String) = dataStore.setAppVersionsHistory(value)

    suspend fun setSearchSettings(value: String) = dataStore.setSearchSettings(value)

    suspend fun setMessagePanelBbCodes(value: String) = dataStore.setMessagePanelBbCodes(value)

    suspend fun setShowReportWarning(value: Boolean) = dataStore.setShowReportWarning(value)

    suspend fun setTooltipSearchSettings(value: Boolean) = dataStore.setTooltipSearchSettings(value)

    suspend fun setTooltipMessagePanelSorting(value: Boolean) = dataStore.setTooltipMessagePanelSorting(value)

    suspend fun setSmartNavLongPressHintDisabled(value: Boolean) = dataStore.setSmartNavLongPressHintDisabled(value)

    suspend fun setOtherMenuTileOrder(value: String) = dataStore.setOtherMenuTileOrder(value)

    suspend fun deleteMessagePanelBbCodes() = dataStore.deleteMessagePanelBbCodes()

    suspend fun getAppFirstStart(): Boolean = dataStore.appFirstStart.first()

    suspend fun getAppVersionsHistory(): String = dataStore.appVersionsHistory.first()

    suspend fun getSearchSettings(): String = dataStore.searchSettings.first()

    suspend fun getMessagePanelBbCodes(): String = dataStore.messagePanelBbCodes.first()

    fun getMessagePanelBbCodesSync(): String = dataStore.getMessagePanelBbCodesSync()

    suspend fun getShowReportWarning(): Boolean = dataStore.showReportWarning.first()

    fun getShowReportWarningSync(): Boolean = dataStore.getShowReportWarningSync()

    suspend fun getTooltipSearchSettings(): Boolean = dataStore.tooltipSearchSettings.first()

    suspend fun getTooltipMessagePanelSorting(): Boolean = dataStore.tooltipMessagePanelSorting.first()

    fun getTooltipMessagePanelSortingSync(): Boolean = dataStore.getTooltipMessagePanelSortingSync()

    suspend fun getSmartNavLongPressHintDisabled(): Boolean = dataStore.smartNavLongPressHintDisabled.first()

    suspend fun getOtherMenuTileLayout(): Map<OtherMenuSection, List<Int>> =
            parseOtherMenuTileLayout(dataStore.otherMenuTileOrder.first())

    fun observeOtherMenuQuickSettingsFlow(): Flow<List<QuickSetting>> =
            dataStore.otherMenuQuickSettings.map { QuickSetting.parse(it) }

    suspend fun setOtherMenuQuickSettings(items: List<QuickSetting>) =
            dataStore.setOtherMenuQuickSettings(QuickSetting.encode(items))

    fun observeOtherMenuHiddenBlocksFlow(): Flow<Set<OtherMenuBlock>> =
            dataStore.otherMenuHiddenBlocks.map { OtherMenuBlock.parse(it) }

    suspend fun setOtherMenuHiddenBlocks(items: Set<OtherMenuBlock>) =
            dataStore.setOtherMenuHiddenBlocks(OtherMenuBlock.encode(items))

    fun encodeOtherMenuTileLayout(layout: Map<OtherMenuSection, List<Int>>): String =
            listOf(OtherMenuSection.QUICK, OtherMenuSection.PERSONAL, OtherMenuSection.TOOLS)
                    .joinToString("|") { section ->
                        "${section.name}:${layout[section].orEmpty().joinToString(",")}"
                    }

    private fun parseOtherMenuTileLayout(value: String): Map<OtherMenuSection, List<Int>> {
        if (value.isBlank()) return emptyMap()
        val sectioned = value
                .split('|')
                .mapNotNull { part ->
                    val separator = part.indexOf(':')
                    if (separator < 0) return@mapNotNull null
                    val section = runCatching {
                        OtherMenuSection.valueOf(part.substring(0, separator))
                    }.getOrNull() ?: return@mapNotNull null
                    val ids = part.substring(separator + 1)
                            .split(',')
                            .mapNotNull { it.toIntOrNull() }
                    section to ids
                }
                .toMap()
        if (sectioned.isNotEmpty()) return sectioned

        val legacyOrder = value.split(',').mapNotNull { it.toIntOrNull() }
        if (legacyOrder.isEmpty()) return emptyMap()
        val legacyRank = legacyOrder.withIndex().associate { it.value to it.index }
        return mapOf(
                OtherMenuSection.QUICK to listOf(
                        MenuRepository.item_article_list,
                        MenuRepository.item_forum,
                        MenuRepository.item_qms_contacts,
                        MenuRepository.item_search,
                        MenuRepository.item_favorites,
                        MenuRepository.item_mentions,
                ),
                OtherMenuSection.PERSONAL to listOf(
                        MenuRepository.item_notes,
                        MenuRepository.item_history,
                        MenuRepository.item_my_messages,
                ),
                OtherMenuSection.TOOLS to listOf(
                        MenuRepository.item_downloads,
                        MenuRepository.item_dev_db,
                        MenuRepository.item_settings,
                )
        ).mapValues { entry ->
            entry.value.sortedWith(compareBy {
                legacyRank[it] ?: Int.MAX_VALUE
            })
        }
    }

}
