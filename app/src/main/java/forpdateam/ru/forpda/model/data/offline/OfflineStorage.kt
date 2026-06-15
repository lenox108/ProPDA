package forpdateam.ru.forpda.model.data.offline

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Filesystem helper for §5.1 (offline reading) Phase 1.
 *
 * Layout (under `context.filesDir`):
 *   offline/
 *     <id>/
 *       index.html
 *       images/...
 *
 * Room rows in [forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom]
 * carry `htmlPath` (the relative path under [ROOT_DIR]) and
 * `sizeBytes` (recursive size of the item's directory).
 */
class OfflineStorage(context: Context) {

    private val root: File = File(context.filesDir, ROOT_DIR).apply {
        if (!exists() && !mkdirs()) {
            Timber.w("OfflineStorage: could not create root directory %s", absolutePath)
        }
    }

    /** Returns the directory that holds this item's files. Creates it if missing. */
    fun itemDir(id: String): File {
        val sanitized = sanitize(id)
        val dir = File(root, sanitized)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun htmlFile(id: String): File = File(itemDir(id), "index.html")

    fun imagesDir(id: String): File {
        val dir = File(itemDir(id), "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun rootDirectory(): File = root

    /** Writes the HTML payload atomically. Returns the bytes written. */
    @Throws(IOException::class)
    fun writeHtml(id: String, html: String): Long {
        val tmp = File(itemDir(id), "index.html.tmp")
        tmp.writeText(html, Charsets.UTF_8)
        val target = htmlFile(id)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // Fall back to copy+delete if renameTo fails (e.g. on some emulators).
            target.writeText(html, Charsets.UTF_8)
            tmp.delete()
        }
        return target.length()
    }

    fun readHtml(id: String): String? {
        val f = htmlFile(id)
        if (!f.exists()) return null
        return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    }

    /** Total size in bytes of this item's directory, recursively. */
    fun sizeOf(id: String): Long {
        val dir = File(root, sanitize(id))
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Recursive delete. No-op if the item is not on disk. */
    fun delete(id: String) {
        val dir = File(root, sanitize(id))
        if (!dir.exists()) return
        runCatching { dir.deleteRecursively() }
                .onFailure { Timber.w(it, "OfflineStorage: failed to delete %s", id) }
    }

    /** Deletes every saved item. Used by storage-limit eviction. */
    fun deleteAll() {
        if (!root.exists()) return
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                runCatching { child.deleteRecursively() }
                        .onFailure { Timber.w(it, "OfflineStorage: failed to delete %s", child.absolutePath) }
            }
        }
    }

    private fun sanitize(id: String): String =
            id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        /** Stored relative to context.filesDir. */
        const val ROOT_DIR: String = "offline"
    }
}
