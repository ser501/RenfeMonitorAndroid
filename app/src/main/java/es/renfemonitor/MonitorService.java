package es.renfemonitor;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Icon;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;

public class MonitorService extends Service {
    static final int SEARCH_ID = 77;
    static final int FOUND_ID = 78;
    static final String SEARCH_CHANNEL = "renfe_search_v2";
    static final String FOUND_CHANNEL = "renfe_found_v6";
    static final String PREFS = "monitor_state";

    volatile boolean running = false;
    volatile Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(SEARCH_ID, buildSearchNotification(
                "Buscando plazas…", "El monitor seguirá buscando en segundo plano."));
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        final String on = i.getStringExtra("originName");
        final String oc = i.getStringExtra("originCode");
        final String dn = i.getStringExtra("destName");
        final String dc = i.getStringExtra("destCode");
        final String date = i.getStringExtra("date");
        final String target = i.getStringExtra("time");
        final int sec = i.getIntExtra("interval", 20);
        final String searchKey = oc + "|" + dc + "|" + date + "|" + target;

        if (worker != null) {
            running = false;
            worker.interrupt();
        }

        running = true;
        saveState(true, false, "", on + " → " + dn + " | " + date + " | " + target,
                0, "WAITING", 0L, "", searchKey);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(SEARCH_ID, buildSearchNotification(
                "Buscando plazas…",
                on + " → " + dn + " · " + date + " · " + target + " · cada " + sec + " s"));

        worker = new Thread(() -> {
            int queryCount = 0;
            try {
                while (running) {
                    try {
                        queryCount++;
                        ArrayList<RenfeClient.Journey> found =
                                RenfeClient.search(on, oc, dn, dc, date, target);
                        long checkedAt = System.currentTimeMillis();

                        saveState(true, false, "",
                                on + " → " + dn + " | " + date + " | " + target,
                                queryCount, "ONLINE", checkedAt, "", searchKey);

                        if (!found.isEmpty()) {
                            Log.i("RenfeMonitor", "Candidato encontrado. Confirmando…");
                            Thread.sleep(1200L);

                            queryCount++;
                            ArrayList<RenfeClient.Journey> confirm =
                                    RenfeClient.search(on, oc, dn, dc, date, target);
                            checkedAt = System.currentTimeMillis();

                            saveState(true, false, "",
                                    on + " → " + dn + " | " + date + " | " + target,
                                    queryCount, "ONLINE", checkedAt, "", searchKey);

                            if (!confirm.isEmpty()) {
                                RenfeClient.Journey j = confirm.get(0);
                                String detail = j.toStringLine();

                                Log.i("RenfeMonitor", "PLAZA CONFIRMADA: " + detail);
                                running = false;

                                saveState(false, true, detail,
                                        on + " → " + dn + " | " + date + " | " + target,
                                        queryCount, "ONLINE", checkedAt, "", searchKey);

                                addHistory(on, dn, date, target, detail, checkedAt);
                                showFoundNotification(on, oc, dn, dc, date, target, detail);

                                nm.cancel(SEARCH_ID);
                                stopForeground(true);
                                stopSelfResult(startId);
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e("RenfeMonitor", "Error durante la comprobación", e);
                        long checkedAt = System.currentTimeMillis();
                        saveState(true, false, "",
                                on + " → " + dn + " | " + date + " | " + target,
                                queryCount, "ERROR", checkedAt, safe(e.getMessage()), searchKey);
                    }

                    if (!running) break;

                    try {
                        Thread.sleep(sec * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (Thread.currentThread() == worker) {
                    if (!running) {
                        nm.cancel(SEARCH_ID);
                        try { stopForeground(true); } catch (Exception ignored) {}
                    }
                    stopSelfResult(startId);
                }
            }
        }, "RenfeMonitor");

        worker.start();
        return START_NOT_STICKY;
    }

    Notification buildSearchNotification(String title, String text) {
        return new Notification.Builder(this, SEARCH_CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(es.renfemonitor.R.drawable.ic_train_notification)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    void showFoundNotification(String on, String oc, String dn, String dc,
                               String date, String target, String detail) {
        Intent in = new Intent(this, RenfeBookingActivity.class);
        in.putExtra("originName", on);
        in.putExtra("originCode", oc);
        in.putExtra("destName", dn);
        in.putExtra("destCode", dc);
        in.putExtra("date", date);
        in.putExtra("time", target);
        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                this, FOUND_ID, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, FOUND_CHANNEL)
                .setContentTitle("¡PLAZA CONFIRMADA!")
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle()
                        .bigText("🚆 " + on + " → " + dn + "\n"
                                + date + " · " + target + "\n"
                                + detail))
                .setSmallIcon(es.renfemonitor.R.drawable.ic_train_notification)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, es.renfemonitor.R.drawable.ic_train_notification),
                        "ABRIR RENFE", pi).build())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setPriority(Notification.PRIORITY_MAX)
                .setOnlyAlertOnce(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (!nm.areNotificationsEnabled()) {
            Log.e("RenfeMonitor", "Las notificaciones están desactivadas para la aplicación.");
        } else {
            nm.notify(FOUND_ID, n);
        }

        playAlert();
    }

    void playAlert() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean sound = p.getBoolean("alert_sound", true);
        boolean vibration = p.getBoolean("alert_vibration", true);

        if (sound) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
                if (ringtone != null) {
                    ringtone.play();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { ringtone.stop(); } catch (Exception ignored) {}
                    }, 1800L);
                }
            } catch (Exception e) {
                Log.e("RenfeMonitor", "No se pudo reproducir el sonido", e);
            }
        }

        if (vibration) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(VibrationEffect.createWaveform(
                                new long[]{0, 250, 120, 250, 120, 400}, -1));
                    } else {
                        v.vibrate(1000L);
                    }
                }
            } catch (Exception e) {
                Log.e("RenfeMonitor", "No se pudo reproducir la vibración", e);
            }
        }
    }

    void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel search = new NotificationChannel(
                SEARCH_CHANNEL, "Búsqueda en segundo plano", NotificationManager.IMPORTANCE_LOW);
        search.setDescription("Indica que Renfe Monitor sigue buscando.");
        search.setSound(null, null);
        search.enableVibration(false);
        nm.createNotificationChannel(search);

        NotificationChannel found = new NotificationChannel(
                FOUND_CHANNEL, "Plazas encontradas", NotificationManager.IMPORTANCE_HIGH);
        found.setDescription("Aviso cuando Renfe Monitor confirma una plaza.");
        // El sonido y la vibración se controlan dentro de la app mediante las preferencias.
        found.setSound(null, null);
        found.enableVibration(false);
        nm.createNotificationChannel(found);
    }

    void saveState(boolean active, boolean found, String detail, String meta,
                   int queryCount, String connection, long lastCheck,
                   String lastError, String searchKey) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("active", active)
                .putBoolean("found", found)
                .putString("detail", detail == null ? "" : detail)
                .putString("meta", meta == null ? "" : meta)
                .putInt("queryCount", queryCount)
                .putString("connection", connection == null ? "WAITING" : connection)
                .putLong("lastCheck", lastCheck)
                .putString("lastError", lastError == null ? "" : lastError)
                .putString("searchKey", searchKey == null ? "" : searchKey)
                .apply();
    }

    void addHistory(String on, String dn, String date, String target, String detail, long timestamp) {
        try {
            android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray src = new JSONArray(p.getString("search_history", "[]"));
            JSONArray out = new JSONArray();

            JSONObject item = new JSONObject();
            item.put("originName", on);
            item.put("destName", dn);
            item.put("date", date);
            item.put("time", target);
            item.put("detail", detail);
            item.put("timestamp", timestamp);
            out.put(item);

            for (int i = 0; i < src.length() && out.length() < 50; i++) {
                JSONObject old = src.optJSONObject(i);
                if (old != null) out.put(old);
            }

            p.edit().putString("search_history", out.toString()).apply();
        } catch (Exception e) {
            Log.e("RenfeMonitor", "No se pudo guardar el historial", e);
        }
    }

    String safe(String s) {
        return s == null ? "error" : s;
    }

    @Override public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
