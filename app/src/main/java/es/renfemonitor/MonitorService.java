package es.renfemonitor;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import java.util.*;

public class MonitorService extends Service {
    static final int SEARCH_ID = 77;
    static final int FOUND_ID = 78;
    static final String SEARCH_CHANNEL = "renfe_search_v2";
    static final String FOUND_CHANNEL = "renfe_found_v5";
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

        if (worker != null) {
            running = false;
            worker.interrupt();
        }

        running = true;
        saveState(true, false, "", "");

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(SEARCH_ID, buildSearchNotification(
                "Buscando plazas…",
                on + " → " + dn + " · " + date + " · " + target + " · cada " + sec + " s"));

        worker = new Thread(() -> {
            try {
                while (running) {
                    try {
                        ArrayList<RenfeClient.Journey> found =
                                RenfeClient.search(on, oc, dn, dc, date, target);

                        if (!found.isEmpty()) {
                            Log.i("RenfeMonitor", "Candidato encontrado. Confirmando…");
                            Thread.sleep(1200L);

                            ArrayList<RenfeClient.Journey> confirm =
                                    RenfeClient.search(on, oc, dn, dc, date, target);

                            if (!confirm.isEmpty()) {
                                RenfeClient.Journey j = confirm.get(0);
                                String detail = j.toStringLine();

                                Log.i("RenfeMonitor", "PLAZA CONFIRMADA: " + detail);
                                running = false;
                                saveState(false, true, detail,
                                        on + " → " + dn + " | " + date + " | " + target);

                                showFoundNotification(on, dn, date, target, detail);

                                // El aviso de búsqueda no puede seguir apareciendo
                                // después de una plaza confirmada.
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
                        saveState(true, false, "", safe(e.getMessage()));
                        // No se alerta por errores transitorios; la búsqueda continúa.
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

    void showFoundNotification(String on, String dn, String date, String target, String detail) {
        Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.renfe.com/es/es/"));
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

        // Refuerzo sonoro inmediato, además del sonido del canal.
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 1000);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 1200L);
        } catch (Exception e) {
            Log.e("RenfeMonitor", "No se pudo reproducir el sonido", e);
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
        found.setDescription("Alerta sonora cuando Renfe Monitor confirma una plaza.");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes audio = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        found.setSound(sound, audio);
        found.enableVibration(true);
        found.setVibrationPattern(new long[]{0, 250, 120, 250, 120, 400});
        nm.createNotificationChannel(found);
    }

    void saveState(boolean active, boolean found, String detail, String meta) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("active", active)
                .putBoolean("found", found)
                .putString("detail", detail == null ? "" : detail)
                .putString("meta", meta == null ? "" : meta)
                .apply();
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
