package es.renfemonitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends android.app.Activity {
    AutoCompleteTextView origin, destination;
    EditText date, time, interval;
    TextView status;
    RenfeClient.StationList stationList;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        loadStations();
    }

    void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,24);

        TextView title = new TextView(this);
        title.setText("BUSCADOR RENFE");
        title.setTextSize(26);
        title.setPadding(0,0,0,16);
        root.addView(title);

        origin = field("Origen (estación)");
        destination = field("Destino (estación)");
        root.addView(origin);
        root.addView(destination);

        date = edit("Fecha (dd/MM/yyyy)",
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));
        time = edit("Hora objetivo (HH:mm)", "16:55");
        interval = edit("Intervalo (segundos)", "20");
        root.addView(date);
        root.addView(time);
        root.addView(interval);

        status = new TextView(this);
        status.setText("Cargando estaciones…");
        status.setPadding(0,18,0,18);
        root.addView(status);

        Button start = new Button(this);
        start.setText("INICIAR BÚSQUEDA");
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("DETENER");
        root.addView(stop);

        start.setOnClickListener(v -> startMonitor());
        stop.setOnClickListener(v -> stopMonitor());
        setContentView(root);
    }

    AutoCompleteTextView field(String hint) {
        AutoCompleteTextView v = new AutoCompleteTextView(this);
        v.setHint(hint);
        v.setTextSize(17);
        v.setSingleLine();
        v.setThreshold(1);
        v.setPadding(8,16,8,16);
        return v;
    }

    EditText edit(String hint, String value) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setText(value);
        v.setSingleLine();
        v.setTextSize(17);
        v.setPadding(8,12,8,12);
        return v;
    }

    void loadStations() {
        new Thread(() -> {
            try {
                stationList = RenfeClient.getStations();
                ArrayList<String> labels = new ArrayList<>();
                for (RenfeClient.Station s : stationList.items) {
                    labels.add(s.name + " [" + s.code + "]");
                }
                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter =
                            new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
                    origin.setAdapter(adapter);
                    destination.setAdapter(adapter);
                    if (stationList.items.size() > 0) {
                        origin.setText(findDefault("ALICANTE"));
                        destination.setText(findDefault("ALCÁZAR"));
                    }
                    status.setText("Estaciones cargadas: " + stationList.items.size());
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Error cargando estaciones: " + e.getMessage()));
            }
        }).start();
    }

    String findDefault(String q) {
        String f = q.toLowerCase(Locale.ROOT);
        for (RenfeClient.Station s : stationList.items) {
            if (s.name.toLowerCase(Locale.ROOT).contains(f)) return s.name + " [" + s.code + "]";
        }
        return stationList.items.get(0).name + " [" + stationList.items.get(0).code + "]";
    }

    void startMonitor() {
        try {
            String o = origin.getText().toString().trim();
            String d = destination.getText().toString().trim();
            String oc = codeFromLabel(o), dc = codeFromLabel(d);
            if (oc == null || dc == null) {
                status.setText("Selecciona una estación de la lista.");
                return;
            }
            if (oc.equals(dc)) {
                status.setText("Origen y destino no pueden ser la misma estación.");
                return;
            }
            validateDate(date.getText().toString().trim());
            validateTime(time.getText().toString().trim());
            int sec = Math.max(10, Integer.parseInt(interval.getText().toString().trim()));

            Intent i = new Intent(this, MonitorService.class);
            i.putExtra("originName", labelName(o));
            i.putExtra("originCode", oc);
            i.putExtra("destName", labelName(d));
            i.putExtra("destCode", dc);
            i.putExtra("date", date.getText().toString().trim());
            i.putExtra("time", time.getText().toString().trim());
            i.putExtra("interval", sec);

            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);

            status.setText("Monitor activo: " + labelName(o) + " → " + labelName(d)
                    + " | " + date.getText() + " | " + time.getText() + " | cada " + sec + " s");
        } catch (Exception e) {
            status.setText("Datos no válidos: " + e.getMessage());
        }
    }

    void stopMonitor() {
        stopService(new Intent(this, MonitorService.class));
        status.setText("Monitor detenido.");
    }

    String labelName(String s) {
        int p = s.lastIndexOf(" [");
        return p > 0 ? s.substring(0, p).trim() : s;
    }

    String codeFromLabel(String s) {
        int a = s.lastIndexOf('['), b = s.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        return s.substring(a+1,b).trim();
    }

    void validateDate(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        f.setLenient(false); f.parse(s);
    }

    void validateTime(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);
        f.setLenient(false); f.parse(s);
    }
}
