package forpdateam.ru.forpda.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Replaces Evernote Android-Job periodic task (min interval aligned with WorkManager: 15 min).
 */
public class NotificationsPeriodicWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "forpda_notifications_periodic";

    public NotificationsPeriodicWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // WorkManager уже выполняется в фоне — bind не нужен.
        NotificationsService.startAndCheckNoBind();
        return Result.success();
    }
}
