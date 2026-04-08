package forpdateam.ru.forpda.common.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import forpdateam.ru.forpda.notifications.NotificationsService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by isanechek on 7/11/17.
 */

public class WakeUpReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final String action = intent != null ? intent.getAction() : null;
        EXECUTOR.execute(() -> {
            try {
                Log.d("WakeUpReceiver", "RECEIVER ACTION " + action);
                if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    // В ресивере нельзя делать тяжёлую работу/инициализацию на main thread.
                    NotificationsService.startAndCheckNoBind();
                }
            } catch (Throwable t) {
                // Не даём ресиверу упасть/повиснуть
                Log.e("WakeUpReceiver", "Failed to handle action=" + action, t);
            } finally {
                pr.finish();
            }
        });
    }
}
