package forpdateam.ru.forpda.notifications.push;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Dependency-free FCM/IID registration, ported from the official 4PDA client
 * (ru.fourpda.client 1.9.43, class PicoFCM / b1.java).
 * <p>
 * The point of the port is a single question: can a package other than
 * ru.fourpda.client obtain a valid registration token for 4PDA's FCM sender?
 */
public class PicoFcm {

    public interface Callback {
        void onResult(int requestId, Bundle result);
    }

    private static final String TAG = "PicoFcm";

    private final Context ctx;
    private final String gmpAppId;
    private final String sender;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseArray<Req> pending = new SparseArray<>(10);

    private String appVer = "1";
    private String appVerName = "1.0";
    private int gmsv;
    private int v1mode;      // 0 = none, 1 = startService REGISTER, 2 = broadcast TOKEN_REQUEST
    private int nextId;
    private boolean v2Failed;

    private final Messenger v1Reply = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message m) { onV1Message(m); }
    });
    private final Messenger v2Reply = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message m) { onV2Message(m); }
    });

    private Messenger v2Service;
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            handler.removeCallbacks(v2Timeout);
            try {
                String descr = binder.getInterfaceDescriptor();
                Log.i(TAG, "V2 bound, descriptor=" + descr);
                if ("android.os.IMessenger".equals(descr)) {
                    v2Service = new Messenger(binder);
                } else if ("com.google.android.gms.iid.IMessengerCompat".equals(descr)) {
                    v2Compat = binder;
                } else {
                    Log.w(TAG, "unknown descriptor, falling back to V1");
                    fallbackV1();
                    return;
                }
                synchronized (pending) {
                    for (int i = 0; i < pending.size(); i++) {
                        sendV2(pending.keyAt(i), pending.valueAt(i).data);
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "bind error " + e);
                fallbackV1();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            v2Service = null;
            v2Compat = null;
        }
    };

    private final Runnable v2Timeout = new Runnable() {
        @Override public void run() {
            Log.w(TAG, "V2 bind timeout, falling back to V1");
            fallbackV1();
        }
    };

    /** The legacy IMessengerCompat binder used by older GmsCore builds. */
    private IBinder v2Compat;

    private void sendCompat(Message message) throws RemoteException {
        Parcel p = Parcel.obtain();
        try {
            p.writeInterfaceToken("com.google.android.gms.iid.IMessengerCompat");
            p.writeInt(1);
            message.writeToParcel(p, 0);
            v2Compat.transact(1, p, null, 1);
        } finally {
            p.recycle();
        }
    }

    private class Req {
        final Bundle data;
        final Callback cb;
        final Runnable timeout;
        Req(final int id, Bundle data, Callback cb) {
            this.data = data;
            this.cb = cb;
            this.timeout = new Runnable() {
                @Override public void run() { finish(id, error("Operation timed out")); }
            };
        }
    }

    public PicoFcm(Context context, String gmpAppId) {
        this.ctx = context.getApplicationContext();
        this.gmpAppId = gmpAppId;
        this.sender = gmpAppId.startsWith("1:") ? gmpAppId.split(":")[1] : gmpAppId;
        PackageManager pm = ctx.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), 0);
            appVer = Integer.toString(pi.versionCode);
            appVerName = pi.versionName;
            gmsv = pm.getPackageInfo("com.google.android.gms", 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "package info", e);
        }
        if (pm.checkPermission("com.google.android.c2dm.permission.SEND", "com.google.android.gms") == 0) {
            if (Build.VERSION.SDK_INT < 26) {
                Intent i = new Intent("com.google.android.c2dm.intent.REGISTER");
                i.setPackage("com.google.android.gms");
                List<ResolveInfo> l = pm.queryIntentServices(i, 0);
                if (l != null && !l.isEmpty()) v1mode = 1;
            }
            if (v1mode == 0) {
                Intent i = new Intent("com.google.iid.TOKEN_REQUEST");
                i.setPackage("com.google.android.gms");
                List<ResolveInfo> l = pm.queryBroadcastReceivers(i, 0);
                if (l != null && !l.isEmpty()) v1mode = 2;
            }
            if (v1mode == 0) v1mode = Build.VERSION.SDK_INT >= 26 ? 2 : 1;
        }
        Log.i(TAG, "gmsv=" + gmsv + " v1mode=" + v1mode + " sender=" + sender + " pkg=" + ctx.getPackageName());
    }

    /** Instance-ID pseudo identity, stored the same way GmsCore expects it. */
    private String instanceId() {
        SharedPreferences sp = ctx.getSharedPreferences("com.google.android.gms.appid", 0);
        String id = sp.getString("|S|id", null);
        if (id != null) return id;
        byte[] b = new byte[8];
        new Random().nextBytes(b);
        b[0] = (byte) ((b[0] & 15) + 112);
        id = Base64.encodeToString(b, 0, 8, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        sp.edit().putString("|S|id", id)
                .putString("|S|cre", String.valueOf(System.currentTimeMillis())).apply();
        return id;
    }

    public void getToken(Callback cb) {
        Bundle b = new Bundle();
        b.putString("scope", "*");
        b.putString("sender", sender);
        b.putString("subtype", sender);
        b.putString("appid", instanceId());
        b.putString("gmp_app_id", gmpAppId);
        b.putString("gmsv", Integer.toString(gmsv));
        b.putString("osv", Integer.toString(Build.VERSION.SDK_INT));
        b.putString("app_ver", appVer);
        b.putString("app_ver_name", appVerName);
        b.putString("cliv", "fiid-20.0.2");

        int id = ++nextId;
        Req r = new Req(id, b, cb);
        synchronized (pending) { pending.put(id, r); }
        handler.postDelayed(r.timeout, TimeUnit.SECONDS.toMillis(30));

        if (gmsv >= 12000000 && !v2Failed) {
            if (v2Service == null && v2Compat == null) {
                Intent i = new Intent("com.google.android.c2dm.intent.REGISTER");
                i.setPackage("com.google.android.gms");
                ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
                handler.postDelayed(v2Timeout, TimeUnit.SECONDS.toMillis(10));
            } else {
                sendV2(id, b);
            }
            return;
        }
        sendV1(id, b);
    }

    private void sendV2(int id, Bundle data) {
        Message m = Message.obtain();
        m.what = 1;
        m.arg1 = id;
        m.replyTo = v2Reply;
        Bundle wrap = new Bundle();
        wrap.putBoolean("oneWay", false);
        wrap.putString("pkg", ctx.getPackageName());
        wrap.putBundle("data", data);
        m.setData(wrap);
        try {
            if (v2Service != null) v2Service.send(m); else sendCompat(m);
        } catch (RemoteException e) {
            Log.w(TAG, "sendV2 failed", e);
            fallbackV1();
        }
    }

    private void sendV1(int id, Bundle data) {
        Intent i = new Intent();
        i.setPackage("com.google.android.gms");
        i.setAction(v1mode == 2 ? "com.google.iid.TOKEN_REQUEST" : "com.google.android.c2dm.intent.REGISTER");
        i.putExtras(data);
        i.putExtra("kid", "|ID|" + id + "|");
        i.putExtra("google.messenger", v1Reply);
        if (v1mode == 2) ctx.sendBroadcast(i); else ctx.startService(i);
    }

    private void fallbackV1() {
        if (v2Failed) return;
        v2Failed = true;
        handler.removeCallbacks(v2Timeout);
        try { ctx.unbindService(conn); } catch (Exception ignored) { }
        synchronized (pending) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                sendV1(pending.keyAt(i), pending.valueAt(i).data);
            }
        }
    }

    private void onV2Message(Message m) {
        Bundle d = m.getData();
        Bundle res = d.getBoolean("unsupported", false)
                ? error("Request not supported") : d.getBundle("data");
        finish(m.arg1, res);
    }

    private void onV1Message(Message m) {
        if (!(m.obj instanceof Intent)) return;
        Intent intent = (Intent) m.obj;
        String reg = intent.getStringExtra("registration_id");
        if (reg == null) reg = intent.getStringExtra("unregistered");
        if (reg != null) {
            java.util.regex.Matcher mt =
                    java.util.regex.Pattern.compile("\\|ID\\|([^|]+)\\|:?+(.*)").matcher(reg);
            if (mt.matches()) {
                Bundle ex = intent.getExtras();
                ex.putString("registration_id", mt.group(2));
                finish(Integer.parseInt(mt.group(1)), ex);
            }
            return;
        }
        String err = intent.getStringExtra("error");
        if (err != null && err.startsWith("|")) {
            String[] parts = err.split("\\|");
            if (parts.length > 2 && "ID".equals(parts[1])) {
                String e = parts[3];
                if (e.startsWith(":")) e = e.substring(1);
                finish(Integer.parseInt(parts[2]), error(e));
                return;
            }
        }
        synchronized (pending) {
            for (int i = pending.size() - 1; i >= 0; i--) finish(pending.keyAt(i), intent.getExtras());
        }
    }

    private void finish(int id, Bundle res) {
        Req r;
        synchronized (pending) {
            r = pending.get(id);
            pending.remove(id);
        }
        if (r == null) return;
        handler.removeCallbacks(r.timeout);
        r.cb.onResult(id, res);
    }

    private static Bundle error(String msg) {
        Bundle b = new Bundle();
        b.putString("error", msg);
        return b;
    }
}
