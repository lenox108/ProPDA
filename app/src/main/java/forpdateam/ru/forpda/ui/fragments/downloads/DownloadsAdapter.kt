package forpdateam.ru.forpda.ui.fragments.downloads

import android.view.LayoutInflater
import android.widget.ImageButton
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemDownloadRowBinding
import forpdateam.ru.forpda.downloads.DownloadWorker
import forpdateam.ru.forpda.downloads.DownloadStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class DownloadsAdapter(
    private val store: DownloadStore,
    private val onOpen: (uri: String?, mime: String?) -> Unit,
    private val onOpenDownloadsFolder: () -> Unit,
    private val onRetry: (url: String, fileName: String, mime: String?) -> Unit,
    private val onCancel: (id: UUID) -> Unit,
    private val onRemoveRecord: (id: UUID) -> Unit,
    private val onDeleteFile: (workId: UUID, uri: String?, absolutePath: String?) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.VH>() {

    private var items: List<WorkInfo> = emptyList()

    fun submit(newItems: List<WorkInfo>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(old: Int, new: Int) = items[old].id == newItems[new].id
            override fun areContentsTheSame(old: Int, new: Int) =
                items[old].state == newItems[new].state &&
                items[old].progress == newItems[new].progress &&
                items[old].outputData == newItems[new].outputData
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDownloadRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemDownloadRowBinding) : RecyclerView.ViewHolder(binding.root) {
        private val title: TextView = binding.downloadTitle
        private val subtitle: TextView = binding.downloadSubtitle
        private val progress: ProgressBar = binding.downloadProgress
        private val btnMore: ImageButton = binding.btnMore
        private val btnRetry: ImageButton = binding.btnRetry

        fun bind(info: WorkInfo) {
            val meta = store.get(info.id)
            val fileName = meta?.fileName.orEmpty()
            val url = meta?.url.orEmpty()
            val mime = meta?.mime
            val p = info.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)

            title.text = if (fileName.isNotBlank()) fileName else url
            subtitle.text = buildSubtitle(info, meta, p)

            val indeterminate = info.state == WorkInfo.State.RUNNING && info.progress.keyValueMap.isEmpty()
            progress.isIndeterminate = indeterminate
            progress.progress = p

            // Click on title opens the file if download completed
            val outUri = info.outputData.getString(DownloadWorker.KEY_OUTPUT_URI)
            val canOpen = info.state == WorkInfo.State.SUCCEEDED && !outUri.isNullOrBlank()
            title.setOnClickListener {
                if (canOpen) {
                    onOpen(outUri, mime)
                }
            }
            title.isClickable = canOpen
            title.isFocusable = canOpen

            // Показываем кнопку Повторить только для FAILED/CANCELLED состояний
            val canRetry = (info.state == WorkInfo.State.FAILED || info.state == WorkInfo.State.CANCELLED) && url.isNotBlank() && fileName.isNotBlank()
            btnRetry.visibility = if (canRetry) View.VISIBLE else View.GONE
            btnRetry.setOnClickListener {
                onRetry(url, fileName, mime)
            }

            btnMore.setOnClickListener { anchor ->
                showMenu(anchor, info, url, fileName, mime)
            }
        }

        private fun showMenu(anchor: View, info: WorkInfo, url: String, fileName: String, mime: String?) {
            val ctx = anchor.context
            val outUri = info.outputData.getString(DownloadWorker.KEY_OUTPUT_URI)
            val outPath = info.outputData.getString(DownloadWorker.KEY_OUTPUT_PATH)

            val canOpen = info.state == WorkInfo.State.SUCCEEDED && !outUri.isNullOrBlank()
            val canRetry = (info.state == WorkInfo.State.FAILED || info.state == WorkInfo.State.CANCELLED) && url.isNotBlank() && fileName.isNotBlank()
            val canCancel = info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            val canDeleteFile = info.state == WorkInfo.State.SUCCEEDED && (!outUri.isNullOrBlank() || !outPath.isNullOrBlank())

            PopupMenu(ctx, anchor).apply {
                if (canOpen) {
                    menu.add(R.string.open).setOnMenuItemClickListener {
                        onOpen(outUri, mime); true
                    }
                }
                menu.add(R.string.open_downloads_folder).setOnMenuItemClickListener {
                    onOpenDownloadsFolder(); true
                }
                if (canRetry) {
                    menu.add(R.string.retry).setOnMenuItemClickListener {
                        onRetry(url, fileName, mime); true
                    }
                }
                if (canCancel) {
                    menu.add(R.string.cancel).setOnMenuItemClickListener {
                        onCancel(info.id); true
                    }
                }
                if (canDeleteFile) {
                    menu.add(R.string.delete_file).setOnMenuItemClickListener {
                        onDeleteFile(info.id, outUri, outPath); true
                    }
                }
                menu.add(R.string.delete_record).setOnMenuItemClickListener {
                    onRemoveRecord(info.id); true
                }
            }.show()
        }

        private fun buildSubtitle(info: WorkInfo, meta: DownloadStore.Meta?, progress: Int): String {
            val state = when (info.state) {
                WorkInfo.State.ENQUEUED -> "В очереди"
                WorkInfo.State.RUNNING -> if (progress > 0) "Загрузка: $progress%" else "Загрузка…"
                WorkInfo.State.SUCCEEDED -> {
                    val completedAt = completionMillis(info, meta)
                    if (completedAt != null) "Завершено: ${formatCompletionTime(completedAt)}" else "Готово"
                }
                WorkInfo.State.FAILED -> "Ошибка"
                WorkInfo.State.CANCELLED -> "Отменено"
                WorkInfo.State.BLOCKED -> "Ожидание"
            }
            val attempts = info.runAttemptCount
            return if (attempts > 0) "$state (попытка ${attempts + 1})" else state
        }

        private fun completionMillis(info: WorkInfo, meta: DownloadStore.Meta?): Long? {
            val outputValue = info.outputData.getLong(DownloadWorker.KEY_COMPLETED_AT, 0L)
            return outputValue.takeIf { it > 0L } ?: meta?.completedAt
        }

        private fun formatCompletionTime(millis: Long): String {
            val zone = ZoneId.systemDefault()
            val dateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
            val today = LocalDate.now(zone)
            val time = TIME_FORMAT.format(dateTime)
            return when (dateTime.toLocalDate()) {
                today -> "Сегодня, $time"
                today.minusDays(1) -> "Вчера, $time"
                else -> DATE_TIME_FORMAT.format(dateTime)
            }
        }
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.ROOT)
    }
}

