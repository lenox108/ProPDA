package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.bbcode.BbcodeRegistry
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.adapters.PanelItemAdapter

/** Последние использованные BBCode без отдельной постоянной панели в редакторе. */
@SuppressLint("ViewConstructor")
class RecentCodesPanelItem(
    context: Context,
    panel: MessagePanel,
    private val codesPanel: CodesPanelItem,
) : BasePanelItem(context, panel, context.getString(R.string.recent_title)) {

    private val items = mutableListOf<ButtonData>()
    private val adapter = PanelItemAdapter(
        items,
        emptyList(),
        PanelItemAdapter.TYPE_DRAWABLE,
        showTitles = false,
    )

    init {
        recyclerView.setColumnWidth(context.resources.getDimensionPixelSize(R.dimen.dp64))
        adapter.setOnItemClickListener(object : PanelItemAdapter.OnItemClickListener {
            override fun onItemClick(item: ButtonData) {
                codesPanel.onToolSelected(item)
            }
        })
        recyclerView.adapter = adapter
        update(codesPanel.initialRecentCodes())
        codesPanel.setRecentChangedListener(::update)
    }

    private fun update(tags: List<String>) {
        items.clear()
        items += tags
            .mapNotNull(BbcodeRegistry::findTool)
            .take(MAX_VISIBLE_RECENT)
            .map { CodesPanelItem.createButtonData(getContext(), it) }
        adapter.notifyDataSetChanged()
    }

    override fun dispose() {
        codesPanel.setRecentChangedListener(null)
    }

    private companion object {
        const val MAX_VISIBLE_RECENT = 10
    }
}
