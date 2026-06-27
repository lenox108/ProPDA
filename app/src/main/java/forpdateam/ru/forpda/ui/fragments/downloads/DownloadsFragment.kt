package forpdateam.ru.forpda.ui.fragments.downloads

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.app.DownloadManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import timber.log.Timber
import android.view.Menu
import android.view.View
import forpdateam.ru.forpda.common.showSnackbar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.downloads.DownloadWorker
import forpdateam.ru.forpda.downloads.DownloadStore
import forpdateam.ru.forpda.downloads.InternalDownloader
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.common.PermissionHelper
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import java.io.File
import java.util.UUID
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Named
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadsFragment : RecyclerFragment() {
    @Inject @Named("data_storage") lateinit var dataStoragePreferences: SharedPreferences
    @Inject lateinit var permissionHelper: PermissionHelper
    @Inject lateinit var internalDownloader: InternalDownloader


    companion object {
        private const val LOG_TAG = "ForPdaDownloads"
    }

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var adapter: DownloadsAdapter
    private lateinit var store: DownloadStore
    private var lastWorkInfos: List<WorkInfo> = emptyList()

    /**
     * После [MediaStore.createDeleteRequest] при RESULT_OK файл уже удалён системой — повторный
     * [deleteDownloadedFile] даст 0 и ложное «Ошибка» (часто на Android 14–16).
     * После [RecoverableSecurityException] нужно повторить удаление в приложении.
     */
    private data class PendingDelete(
        val workId: UUID,
        val uriStr: String?,
        val absolutePath: String?,
        val systemAlreadyDeletedOnConfirm: Boolean
    )

    private var pendingDelete: PendingDelete? = null
    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>

    private sealed class DeleteOutcome {
        data class Completed(val deleted: Boolean) : DeleteOutcome()
        data class RequiresUserConfirmation(
            val pending: PendingDelete,
            val sender: IntentSender
        ) : DeleteOutcome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.downloads)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()

        deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingDelete
            pendingDelete = null
            if (pending == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = result.resultCode == android.app.Activity.RESULT_OK
                val stillPresent = withContext(Dispatchers.IO) {
                    fileOrUriStillPresent(pending.uriStr, pending.absolutePath)
                }
                logDelete(
                    "intentResult systemDeleteFlow=${pending.systemAlreadyDeletedOnConfirm} " +
                        "resultCode=${result.resultCode} ok=$ok stillPresent=$stillPresent"
                )

                val ctx = context ?: return@launch
                if (pending.systemAlreadyDeletedOnConfirm) {
                    if (ok || !stillPresent) {
                        showSnackbar(R.string.delete)
                        removeDownloadRecord(pending.workId)
                    }
                    return@launch
                }

                if (!ok) {
                    if (!stillPresent) {
                        showSnackbar(R.string.delete)
                        removeDownloadRecord(pending.workId)
                    }
                    return@launch
                }

                when (val outcome = withContext(Dispatchers.IO) {
                    performDelete(ctx, pending.workId, pending.uriStr, pending.absolutePath, allowConfirm = false)
                }) {
                    is DeleteOutcome.Completed -> {
                        if (isAdded) {
                            showSnackbar(if (outcome.deleted) R.string.delete else R.string.error)
                        }
                        if (outcome.deleted) removeDownloadRecord(pending.workId)
                    }
                    is DeleteOutcome.RequiresUserConfirmation -> {
                        pendingDelete = outcome.pending
                        deleteLauncher.launch(IntentSenderRequest.Builder(outcome.sender).build())
                    }
                }
            }
        }

        refreshLayout.isEnabled = false
        recyclerView.layoutManager = LinearLayoutManager(context)
        store = DownloadStore(dataStoragePreferences)
        adapter = DownloadsAdapter(
            store = store,
            onOpen = { uriStr, mime ->
                openUri(uriStr, mime)
            },
            onOpenDownloadsFolder = {
                openDownloadsFolder()
            },
            onRetry = { url, fileName, mime ->
                internalDownloader.enqueue(requireContext(), url, fileName, mime)
            },
            onCancel = { id ->
                WorkManager.getInstance(requireContext()).cancelWorkById(id)
            },
            onRemoveRecord = { id ->
                store.remove(id)
                WorkManager.getInstance(requireContext()).cancelWorkById(id)
                render()
            },
            onDeleteFile = { workId, uriStr, absolutePath ->
                deleteDownloadedFileAsync(workId, uriStr, absolutePath, allowConfirm = true)
            }
        )
        recyclerView.adapter = adapter

        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(DownloadWorker.WORK_TAG)
            .observe(viewLifecycleOwner, Observer { infos ->
                lastWorkInfos = infos ?: emptyList()
                render()
            })
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.storage_access)
            .setOnMenuItemClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    showSnackbar(R.string.storage_permission_not_needed)
                    return@setOnMenuItemClickListener true
                }
                permissionHelper.checkStoragePermission(Runnable { }, requireActivity())
                true
            }
        menu.add(R.string.downloads_clear_list)
            .setOnMenuItemClickListener {
                val wm = WorkManager.getInstance(requireContext())
                wm.cancelAllWorkByTag(DownloadWorker.WORK_TAG)
                store.clearAll()
                wm.pruneWork()
                render()
                true
            }
    }

    private fun showEmptyIfNeeded(empty: Boolean) {
        if (empty) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funny = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_download)
                    .setTitle(R.string.downloads)
                contentController.addContent(funny, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
    }

    private fun logDelete(message: String) {
        if (BuildConfig.DEBUG) Timber.d(message)
    }

    private fun logDeleteError(message: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (t != null) Timber.e(t, message) else Timber.e(message)
        }
    }

    /** true, если по uri или пути объект ещё есть (для обхода неверного resultCode на OEM). */
    private fun fileOrUriStillPresent(uriStr: String?, absolutePath: String?): Boolean {
        val uri = uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null && contentUriStillExists(uri)) return true
        if (!absolutePath.isNullOrBlank() && File(absolutePath).exists()) return true
        return false
    }

    private fun contentUriStillExists(uri: Uri): Boolean {
        val cr = requireContext().contentResolver
        return try {
            cr.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun openUri(uriStr: String?, mime: String?) {
        if (uriStr.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return
        val ctx = requireContext()
        val pm = ctx.packageManager

        // Media / Downloads providers: прямой grant другим приложениям часто ломается (Oplus/Android 14+).
        // Копируем в кэш и отдаём FileProvider — стабильный chooser.
        val auth = uri.authority.orEmpty()
        val useCacheCopy = auth == "media" ||
            auth.contains("media", ignoreCase = true) ||
            auth.contains("downloads.documents", ignoreCase = true)
        if (useCacheCopy) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val shareUri = withContext(Dispatchers.IO) {
                        openUriPrepareCacheCopy(ctx, uri)
                    }
                    if (shareUri == null) {
                        showSnackbar(R.string.error)
                        return@launch
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(shareUri, mime ?: "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
                    } else {
                        showSnackbar(R.string.error)
                    }
                } catch (e: Exception) {
                    logDeleteError("openUri media copy failed", e)
                    showSnackbar(R.string.error)
                }
            }
            return
        }

        fun launchChooser(viewUri: Uri) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, mime ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(pm) != null) {
                startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
            } else {
                showSnackbar(R.string.error)
            }
        }

        try {
            launchChooser(uri)
        } catch (e: SecurityException) {
            logDelete("openUri direct ACTION_VIEW failed, copy via FileProvider: ${e.message}")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val shareUri = withContext(Dispatchers.IO) {
                        openUriPrepareCacheCopy(ctx, uri)
                    }
                    if (shareUri == null) {
                        showSnackbar(R.string.error)
                        return@launch
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(shareUri, mime ?: "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    if (intent.resolveActivity(pm) != null) {
                        startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
                    } else {
                        showSnackbar(R.string.error)
                    }
                } catch (e2: Exception) {
                    logDeleteError("openUri fallback copy failed", e2)
                    showSnackbar(R.string.error)
                }
            }
        }
    }

    /** @return content Uri через FileProvider или null */
    private fun openUriPrepareCacheCopy(ctx: Context, uri: Uri): Uri? {
        val cr = ctx.contentResolver
        val dir = File(ctx.cacheDir, "open_share").apply { mkdirs() }
        val baseName = queryDisplayName(ctx, uri) ?: "file"
        val safe = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "file" }
        val outFile = File(dir, "${UUID.randomUUID()}_$safe")
        cr.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String? {
        val cr = ctx.contentResolver
        return try {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun removeDownloadRecord(workId: UUID) {
        store.remove(workId)
        WorkManager.getInstance(requireContext()).cancelWorkById(workId)
        render()
    }

    private fun deleteDownloadedFileAsync(workId: UUID, uriStr: String?, absolutePath: String?, allowConfirm: Boolean) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val outcome = withContext(Dispatchers.IO) {
                performDelete(ctx, workId, uriStr, absolutePath, allowConfirm)
            }) {
                is DeleteOutcome.Completed -> {
                    showSnackbar(if (outcome.deleted) R.string.delete else R.string.error)
                    if (outcome.deleted) removeDownloadRecord(workId)
                }
                is DeleteOutcome.RequiresUserConfirmation -> {
                    pendingDelete = outcome.pending
                    deleteLauncher.launch(IntentSenderRequest.Builder(outcome.sender).build())
                }
            }
        }
    }

    /**
     * Дисковые вызовы MediaProvider — только с фонового потока (иначе StrictMode / jank на UI).
     */
    @android.annotation.SuppressLint("NewApi")
    private fun performDelete(
        ctx: Context,
        workId: UUID,
        uriStr: String?,
        absolutePath: String?,
        allowConfirm: Boolean
    ): DeleteOutcome {
        var deleted = false
        if (!uriStr.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (uri != null) {
                val cr = ctx.contentResolver
                try {
                    deleted = when {
                        DocumentsContract.isDocumentUri(ctx, uri) ->
                            DocumentsContract.deleteDocument(cr, uri)
                        else -> cr.delete(uri, null, null) > 0
                    }
                } catch (e: RecoverableSecurityException) {
                    if (allowConfirm) {
                        logDelete("RecoverableSecurityException, launching userAction intent")
                        return DeleteOutcome.RequiresUserConfirmation(
                            PendingDelete(workId, uriStr, absolutePath, systemAlreadyDeletedOnConfirm = false),
                            e.userAction.actionIntent.intentSender
                        )
                    }
                } catch (se: SecurityException) {
                    if (allowConfirm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val r = runCatching {
                            logDelete("SecurityException, MediaStore.createDeleteRequest")
                            val sender = MediaStore.createDeleteRequest(cr, listOf(uri)).intentSender
                            return DeleteOutcome.RequiresUserConfirmation(
                                PendingDelete(workId, uriStr, absolutePath, systemAlreadyDeletedOnConfirm = true),
                                sender
                            )
                        }
                        if (r.isFailure) {
                            logDeleteError("createDeleteRequest after SecurityException failed", r.exceptionOrNull())
                        }
                    }
                } catch (e: Exception) {
                    logDeleteError("Unexpected error resolving delete outcome via MediaStore", e)
                }

                // Файл по пути: из WorkInfo или угадываем public/Download/имя по MediaStore (старые записи без outputPath).
                if (!deleted) {
                    val path = absolutePath ?: guessPublicDownloadPath(ctx, uri)
                    if (!path.isNullOrBlank()) {
                        deleted = runCatching { File(path).delete() }.getOrDefault(false)
                    }
                }

                if (!deleted && allowConfirm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val r = runCatching {
                        logDelete("delete returned 0, MediaStore.createDeleteRequest")
                        val sender = MediaStore.createDeleteRequest(cr, listOf(uri)).intentSender
                        return DeleteOutcome.RequiresUserConfirmation(
                            PendingDelete(workId, uriStr, absolutePath, systemAlreadyDeletedOnConfirm = true),
                            sender
                        )
                    }
                    if (r.isFailure) {
                        logDeleteError("createDeleteRequest (after delete==0) failed", r.exceptionOrNull())
                    }
                }
            }
        }
        return DeleteOutcome.Completed(deleted)
    }

    /** Путь к файлу в общем каталоге Download по DISPLAY_NAME (если файл ещё там). */
    private fun guessPublicDownloadPath(ctx: Context, uri: Uri): String? {
        if (uri.authority?.contains("media") != true) return null
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: return null
        val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
        return if (f.exists()) f.absolutePath else null
    }

    private fun openDownloadsFolder() {
        val pm = requireContext().packageManager
        val downloadsDoc = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")

        // Важно: DownloadManager.ACTION_VIEW_DOWNLOADS как «primary» на части прошивок (Oplus и др.)
        // открывает системное приложение без листа выбора. Первым должен быть «обычный» VIEW каталога.
        val candidates = ArrayList<Intent>(10)

        candidates.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloadsDoc, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })

        candidates.add(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDoc)
        })

        candidates.add(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDoc)
        })

        candidates.add(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsDoc)
        })

        candidates.add(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            candidates.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("file:///storage/emulated/0/Download"), "resource/folder")
            })
        }

        val resolved = candidates.mapNotNull { intent ->
            if (intent.resolveActivity(pm) != null) intent else null
        }
        if (resolved.isEmpty()) {
            showSnackbar(R.string.error)
            return
        }

        val primary = resolved.first()
        val rest = resolved.drop(1).toTypedArray()
        val chooser = Intent.createChooser(primary, getString(R.string.open_with))
        if (rest.isNotEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, rest)
        }
        try {
            startActivity(chooser)
        } catch (e: Exception) {
            logDeleteError("openDownloadsFolder chooser failed", e)
            showSnackbar(R.string.error)
        }
    }

    private fun render() {
        val items = lastWorkInfos
            .filter { wi ->
                wi.state == WorkInfo.State.RUNNING ||
                        wi.state == WorkInfo.State.ENQUEUED ||
                        store.get(wi.id) != null
            }
            .sortedWith(
                compareBy<WorkInfo> { sortBucket(it.state) }
                    .thenByDescending { completionMillis(it) }
                    .thenByDescending { it.runAttemptCount }
                    .thenBy { it.state.name }
            )
        adapter.submit(items)
        showEmptyIfNeeded(items.isEmpty())
    }

    private fun sortBucket(state: WorkInfo.State): Int {
        return when (state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 0
            WorkInfo.State.SUCCEEDED -> 1
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> 2
        }
    }

    private fun completionMillis(info: WorkInfo): Long {
        val outputValue = info.outputData.getLong(DownloadWorker.KEY_COMPLETED_AT, 0L)
        return outputValue.takeIf { it > 0L } ?: store.get(info.id)?.completedAt ?: 0L
    }
}
