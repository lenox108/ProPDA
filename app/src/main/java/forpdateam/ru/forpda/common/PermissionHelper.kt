package forpdateam.ru.forpda.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper для работы с разрешениями приложения.
 * Заменяет статические методы App.checkStoragePermission() и App.onRequestPermissionsResult().
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val REQUEST_STORAGE = 1
    }

    private var pendingCallback: Runnable? = null

    /**
     * Проверяет разрешение на запись во внешнее хранилище.
     */
    fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Запрашивает разрешение на запись во внешнее хранилище.
     * Если разрешение уже есть, выполняет callback немедленно.
     */
    fun checkStoragePermission(callback: Runnable, activity: Activity) {
        if (hasStoragePermission()) {
            callback.run()
        } else {
            pendingCallback = callback
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE
            )
        }
    }

    /**
     * Обрабатывает результат запроса разрешений.
     * Вызывать из Activity.onRequestPermissionsResult().
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCallback?.run()
            }
            pendingCallback = null
            return true
        }
        return false
    }

    /**
     * Сбрасывает pending callback (например, при уничтожении Activity).
     */
    fun clearPendingCallback() {
        pendingCallback = null
    }
}
