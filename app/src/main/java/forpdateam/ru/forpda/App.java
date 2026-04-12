package forpdateam.ru.forpda;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.StrictMode;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import androidx.multidex.MultiDex;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;

import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.webkit.WebSettings;

import forpdateam.ru.forpda.BuildConfig;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;

import java.util.concurrent.TimeUnit;

import io.appmetrica.analytics.AppMetrica;
import io.appmetrica.analytics.AppMetricaConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;
import java.util.regex.Pattern;

import forpdateam.ru.forpda.common.ForPdaCoil;
import forpdateam.ru.forpda.common.DayNightHelper;
import forpdateam.ru.forpda.common.LocaleHelper;
import forpdateam.ru.forpda.common.Preferences;
import forpdateam.ru.forpda.common.realm.DbMigration;
import forpdateam.ru.forpda.common.NetworkConnectivityTracker;
import forpdateam.ru.forpda.common.simple.SimpleObservable;
import forpdateam.ru.forpda.notifications.NotificationsPeriodicWorker;
import forpdateam.ru.forpda.notifications.NotificationsService;
import forpdateam.ru.forpda.ui.fragments.TabFragment;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by radiationx on 28.07.16.
 */

public class App extends android.app.Application {
    public static int px2, px4, px6, px8, px12, px14, px16, px20, px24, px32, px36, px40, px48, px56, px64;
    private static App instance;
    private float density = 1.0f;
    private SharedPreferences preferences;

    private SimpleObservable networkForbidden = new SimpleObservable();
    private NetworkConnectivityTracker networkConnectivityTracker;
    private Boolean webViewFound = null;
    private Messenger mBoundService = null;
    private boolean mServiceBound = false;


    public App() {
        instance = this;
    }

    public static App get() {
        if (instance == null) {
            throw new IllegalStateException("App is not initialized — call only after Application.onCreate()");
        }
        return instance;
    }

    public static Context getContext() {
        return get();
    }

    @ColorInt
    public static int getColorFromAttr(Context context, @AttrRes int attr) {
        TypedValue typedValue = new TypedValue();
        if (context != null && context.getTheme().resolveAttribute(attr, typedValue, true))
            return typedValue.data;
        else
            return Color.RED;
    }

    @DrawableRes
    public static int getDrawableResAttr(Context context, @AttrRes int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int attributeResourceId = a.getResourceId(0, 0);
        a.recycle();
        return attributeResourceId;
    }

    public static Drawable getDrawableAttr(Context context, @AttrRes int attr) {
        return AppCompatResources.getDrawable(context, getDrawableResAttr(context, attr));
    }

    public boolean isWebViewFound(Context context) {
        if (webViewFound == null) {
            try {
                WebSettings.getDefaultUserAgent(context);
                webViewFound = true;
            } catch (Exception e) {
                webViewFound = false;
            }
        }
        return webViewFound;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        long time = System.currentTimeMillis();

        // Debug-only: помогает ловить причины лагов/ANR (диск/сеть на main, утечки Closeable).
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }
        AppMetricaConfig config = AppMetricaConfig.newConfigBuilder("a94d9236-cdf3-4a5e-af30-d6dbffaea362").build();
        AppMetrica.activate(getApplicationContext(), config);
        AppMetrica.enableActivityAutoTracking(this);

        final Thread.UncaughtExceptionHandler defaultUncaught = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                if (ex != null) {
                    AppMetrica.reportError("uncaught:" + thread.getName() + ":" + ex.getMessage(), ex);
                }
            } catch (Throwable ignored) {
            }
            if (defaultUncaught != null) {
                defaultUncaught.uncaughtException(thread, ex);
            }
        });

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        }

        //ACRA.init(this);
        dependencies = new Dependencies(this);


        RxJavaPlugins.setErrorHandler(throwable -> {
            throwable.printStackTrace();
            if (BuildConfig.DEBUG) {
                Log.e("RxJava", "Undelivered exception (global handler)", throwable);
            }
            AppMetrica.reportError("Крит " + throwable.getMessage(), throwable);
        });

        Disposable disposable = dependencies
                .getMainPreferencesHolder()
                .observeThemeMode()
                .distinctUntilChanged()
                .subscribe(
                        DayNightHelper.Companion::applyTheme,
                        Throwable::printStackTrace
                );

        try {
            String inputHistory = dependencies.getOtherPreferencesHolder().getAppVersionsHistory();
            String[] history = TextUtils.split(inputHistory, ";");

            int lastVNum = 0;
            boolean disorder = false;
            for (String version : history) {
                int vNum = Integer.parseInt(version);
                if (vNum < lastVNum) {
                    disorder = true;
                }
                lastVNum = vNum;
            }
            Object vCode = BuildConfig.VERSION_CODE;
            String sVCode = "" + vCode;
            int nVCode = Integer.parseInt(sVCode);

            if (lastVNum < nVCode) {
                List<String> list = new ArrayList<>(Arrays.asList(history));
                list.add(Integer.toString(nVCode));
                dependencies.getOtherPreferencesHolder().setAppVersionsHistory(TextUtils.join(";", list));
            }
            if (disorder) {
                throw new Exception("Нарушение порядка версий!");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            AppMetrica.reportError("VERSIONS_HISTORY", ex);
        }

        ForPdaCoil.INSTANCE.init(this);

        updateStaticRes();

        Realm.init(this);
        RealmConfiguration configuration = new RealmConfiguration.Builder()
                .name("forpda.realm")
                .schemaVersion(5)
                .migration(new DbMigration())
                .build();
        Realm.setDefaultConfiguration(configuration);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @RequiresApi(api = Build.VERSION_CODES.M)
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(App.class.getSimpleName(), "DOZE ON RECEIVE " + intent);
                    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    if (pm == null)
                        return;
                    if (pm.isDeviceIdleMode()) {
                        // the device is now in doze mode
                        Log.d(App.class.getSimpleName(), "DOZE MODE ENABLYA");
                    } else {
                        // the device just woke up from doze mode
                        Log.d(App.class.getSimpleName(), "DOZE MODE DISABLYA");
                        // Не делаем bind из ресивера (может приводить к ANR на wake-up/edge устройствах).
                        NotificationsService.startAndCheckNoBind();
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(receiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
            }
        }


        // WakeUpReceiver оставляем ТОЛЬКО в манифесте для BOOT_COMPLETED.
        // Динамически регистрировать BOOT_COMPLETED бессмысленно (приложение и так уже запущено),
        // а SCREEN_ON даёт лишние события и может провоцировать ANR.

        Observable
                .fromCallable(() -> {
                    Constraints constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();
                    PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                            NotificationsPeriodicWorker.class,
                            15,
                            TimeUnit.MINUTES
                    )
                            .setConstraints(constraints)
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                            .build();
                    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                            NotificationsPeriodicWorker.UNIQUE_WORK_NAME,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            workRequest
                    );
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .subscribe();

        if (BuildConfig.DEBUG) {
            Log.d("APP", "TIME APP FINAL " + (System.currentTimeMillis() - time));
        }

        registerConnectivityReceiver();
    }

    private void registerConnectivityReceiver() {
        if (networkConnectivityTracker != null) {
            networkConnectivityTracker.stop();
        }
        networkConnectivityTracker = new NetworkConnectivityTracker(this, Di().getNetworkState());
        networkConnectivityTracker.start();
    }

    private void updateStaticRes() {
        if (BuildConfig.DEBUG) {
            Log.d("App", "updateStaticRes");
        }
        px2 = getContext().getResources().getDimensionPixelSize(R.dimen.dp2);
        px4 = getContext().getResources().getDimensionPixelSize(R.dimen.dp4);
        px6 = getContext().getResources().getDimensionPixelSize(R.dimen.dp6);
        px8 = getContext().getResources().getDimensionPixelSize(R.dimen.dp8);
        px12 = getContext().getResources().getDimensionPixelSize(R.dimen.dp12);
        px14 = getContext().getResources().getDimensionPixelSize(R.dimen.dp14);
        px16 = getContext().getResources().getDimensionPixelSize(R.dimen.dp16);
        px20 = getContext().getResources().getDimensionPixelSize(R.dimen.dp20);
        px24 = getContext().getResources().getDimensionPixelSize(R.dimen.dp24);
        px32 = getContext().getResources().getDimensionPixelSize(R.dimen.dp32);
        px36 = getContext().getResources().getDimensionPixelSize(R.dimen.dp36);
        px40 = getContext().getResources().getDimensionPixelSize(R.dimen.dp40);
        px48 = getContext().getResources().getDimensionPixelSize(R.dimen.dp48);
        px56 = getContext().getResources().getDimensionPixelSize(R.dimen.dp56);
        px64 = getContext().getResources().getDimensionPixelSize(R.dimen.dp64);

        HashMap<String, String> templateStringCache = new HashMap<>();
        for (Field f : R.string.class.getFields()) {
            try {
                if (f.getName().contains("res_s_")) {
                    templateStringCache.put(f.getName(), getString(f.getInt(f)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        dependencies.getTemplateManager().setStaticStrings(templateStringCache);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateStaticRes();
    }

    private Dependencies dependencies;

    public Dependencies Di() {
        return dependencies;
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundService = null;
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            String n1 = name.getClassName();
            String n2 = NotificationsService.class.getName();
            if (n1.equals(n2)) {
                mBoundService = new Messenger(service);
                mServiceBound = true;
            }
        }
    };

    public ServiceConnection getServiceConnection() {
        return mServiceConnection;
    }

    public static int getToolBarHeight(Context context) {
        int[] attrs = new int[]{R.attr.actionBarSize};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        int toolBarHeight = ta.getDimensionPixelSize(0, -1);
        ta.recycle();
        return toolBarHeight;
    }

    @SuppressWarnings("deprecation")
    public void subscribeForbidden(Observer observer) {
        networkForbidden.addObserver(observer);
    }

    @SuppressWarnings("deprecation")
    public void unSubscribeForbidden(Observer observer) {
        networkForbidden.deleteObserver(observer);
    }

    public void notifyForbidden(boolean isForbidden) {
        networkForbidden.notifyObservers(isForbidden);
    }

    public int dpToPx(int dp, Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /*Only vector icon*/
    public static Drawable getVecDrawable(Context context, @DrawableRes int id) {
        Drawable drawable = AppCompatResources.getDrawable(context, id);
        if (!(drawable instanceof VectorDrawableCompat || drawable instanceof VectorDrawable)) {
            throw new RuntimeException();
        }
        return drawable;
    }

    public SharedPreferences getPreferences() {
        if (preferences == null)
            preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return preferences;
    }

    public static SharedPreferences getPreferences(Context context) {
        if (context == null) {
            return App.get().getPreferences();
        }
        return PreferenceManager.getDefaultSharedPreferences(context);
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Activity getActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);

            Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
            if (activities == null)
                return null;

            for (Object activityRecord : activities.values()) {
                Class<?> activityRecordClass = activityRecord.getClass();
                Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(activityRecord)) {
                    Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    Activity activity = (Activity) activityField.get(activityRecord);
                    return activity;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private List<Runnable> permissionCallbacks = new ArrayList<>();

    public void checkStoragePermission(Runnable runnable, Activity activity) {
        if (runnable == null || activity == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, TabFragment.REQUEST_STORAGE);
                permissionCallbacks.add(runnable);
                return;
            }
        }
        runnable.run();
    }

    //PLS CALL THIS IN ALL ACTIVITIES
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                for (Runnable runnable : permissionCallbacks) {
                    try {
                        runnable.run();
                    } catch (Exception ignore) {
                    }
                }
                break;
            }
        }
        permissionCallbacks.clear();
    }
}
