package es.renfemonitor;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;

public class MonitorService extends Service {
    static final int ID = 77;
    volatile boolean running = true;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(ID, buildNotification("Preparando monitor…", true, null));
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        final String on=i.getStringExtra("originName");
        final String oc=i.getStringExtra("originCode");
        final String dn=i.getStringExtra("destName");
        final String dc=i.getStringExtra("destCode");
        final String date=i.getStringExtra("date");
        final String target=i.getStringExtra("time");
        final int sec=i.getIntExtra("interval",20);

        new Thread(() -> {
            while (running) {
                try {
                    update("Buscando " + on + " → " + dn + " | " + date + " | " + target);
                    java.util.ArrayList<RenfeClient.Journey> found = RenfeClient.search(on,oc,dn,dc,date,target);
                    if (!found.isEmpty()) {
                        // Confirm with a second completely fresh Renfe session.
                        // A single transient/stale result must never trigger an alert.
                        update("Candidato encontrado; confirmando disponibilidad real…");
                        Thread.sleep(1200L);
                        java.util.ArrayList<RenfeClient.Journey> confirm =
                                RenfeClient.search(on,oc,dn,dc,date,target);
                        if (!confirm.isEmpty()) {
                            RenfeClient.Journey j = confirm.get(0);
                            String detail = j.toStringLine();
                            update("PLAZA CONFIRMADA: " + detail);
                            notifyFound(on,dn,date,target,detail);
                            running=false;
                            break;
                        }
                    }
                } catch (Exception e) {
                    update("Error: " + safe(e.getMessage()));
                }
                if (!running) break;
                try { Thread.sleep(sec * 1000L); }
                catch (InterruptedException ignored) { break; }
            }
            stopSelf();
        }, "RenfeMonitor").start();

        return START_NOT_STICKY;
    }

    String safe(String s){ return s==null?"error":s; }

    void update(String text){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(ID, buildNotification(text,true,null));
    }

    void notifyFound(String on,String dn,String date,String target,String detail){
        Intent in=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.renfe.com/es/es"));
        PendingIntent pi=PendingIntent.getActivity(this,78,in,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"renfe")
                .setContentTitle("¡Plaza encontrada!")
                .setContentText(detail)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(Notification.PRIORITY_MAX)
                .setAutoCancel(true).setContentIntent(pi).build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(78,n);
    }

    Notification buildNotification(String text, boolean ongoing, PendingIntent pi){
        Notification.Builder b=new Notification.Builder(this,"renfe")
                .setContentTitle("Renfe Monitor")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(ongoing);
        if(pi!=null)b.setContentIntent(pi);
        return b.build();
    }

    void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(new NotificationChannel(
                            "renfe","Renfe Monitor",NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @Override public void onDestroy(){ running=false; super.onDestroy(); }
    @Override public IBinder onBind(Intent i){ return null; }
}
