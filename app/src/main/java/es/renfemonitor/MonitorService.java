package es.renfemonitor;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;

public class MonitorService extends Service {
    static final int SEARCH_ID = 77;
    static final int FOUND_ID = 78;
    static final String SEARCH_CHANNEL = "renfe_search_v2";
    static final String FOUND_CHANNEL = "renfe_found_v4";

    volatile boolean running = false;
    volatile Thread worker;

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(SEARCH_ID, buildSearchNotification(
                "Buscando plazas…", "El monitor seguirá en segundo plano y sin alertas."));
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
                            // Segunda consulta completamente nueva antes de alertar.
                            Thread.sleep(1200L);
                            ArrayList<RenfeClient.Journey> confirm =
                                    RenfeClient.search(on, oc, dn, dc, date, target);

                            if (!confirm.isEmpty()) {
                                RenfeClient.Journey j = confirm.get(0);
                                String detail = j.toStringLine();
                                notifyFound(on, dn, date, target, detail);
                                running = false;
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ignored) {
                        // Errores/transitorios no generan alertas.
                        // El monitor continúa buscando en segundo plano.
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
                    stopSelf(startId);
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

    void notifyFound(String on, String dn, String date, String target, String detail) {
        Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.renfe.com/es/es/"));
        PendingIntent pi = PendingIntent.getActivity(
                this, FOUND_ID, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String route = on + " → " + dn;
        Notification n = new Notification.Builder(this, FOUND_CHANNEL)
                .setContentTitle("¡Plaza encontrada!")
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(route + "\n" + date + " · " + target + "\n" + detail))
                .setSmallIcon(es.renfemonitor.R.drawable.ic_train_notification)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setPriority(Notification.PRIORITY_MAX)
                .build();

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(FOUND_ID, n);

        // Refuerzo sonoro: además del sonido del canal de notificación,
        // emite un aviso corto en el flujo de notificaciones del sistema.
        try {
            android.media.ToneGenerator tone =
                    new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100);
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 900);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tone::release, 1100L);
        } catch (Exception ignored) {}
    }

    void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel search = new NotificationChannel(
                SEARCH_CHANNEL, "Búsqueda en segundo plano", NotificationManager.IMPORTANCE_LOW);
        search.setDescription("Indica que Renfe Monitor sigue buscando. No emite sonido ni vibración.");
        search.setSound(null, null);
        search.enableVibration(false);
        nm.createNotificationChannel(search);

        NotificationChannel found = new NotificationChannel(
                FOUND_CHANNEL, "Plazas encontradas", NotificationManager.IMPORTANCE_HIGH);
        found.setDescription("Alerta con sonido cuando Renfe Monitor confirma una plaza disponible.");

        android.net.Uri sound = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION);
        android.media.AudioAttributes audio = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        found.setSound(sound, audio);
        found.enableVibration(true);
        found.setVibrationPattern(new long[]{0, 250, 120, 250});
        nm.createNotificationChannel(found);
    }

    @Override public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
