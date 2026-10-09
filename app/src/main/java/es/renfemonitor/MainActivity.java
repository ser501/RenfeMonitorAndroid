package es.renfemonitor;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG_TOP = Color.rgb(10, 8, 24);
    private static final int BG_BOTTOM = Color.rgb(31, 11, 42);
    private static final int CARD = Color.rgb(29, 23, 43);
    private static final int FIELD = Color.rgb(37, 30, 53);
    private static final int BORDER = Color.rgb(70, 58, 88);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(185, 177, 199);
    private static final int PINK = Color.rgb(232, 63, 173);
    private static final int GREEN = Color.rgb(81, 205, 113);
    private static final int RED = Color.rgb(239, 89, 89);
    private static final int YELLOW = Color.rgb(242, 193, 78);

    private static final String PREFS = "monitor_state";
    private static final String SEARCHES = "saved_searches";
    private static final String HISTORY = "search_history";

    AutoCompleteTextView origin, destination, time;
    EditText date, interval;
    Spinner intervalUnit;
    TextView status, metrics, statusDot;
    RenfeClient.StationList stationList;
    int scheduleRequest = 0;
    ArrayList<String> departureLabels = new ArrayList<>();

    String pendingOriginCode, pendingDestinationCode;
    String pendingDate, pendingTime, pendingIntervalUnit;
    Integer pendingInterval;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_TOP);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);

        buildMainUi();

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        loadStations();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshMonitorState();
    }

    void buildMainUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(30));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(es.renfemonitor.R.drawable.ic_launcher_app);
        brand.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(14), 0, 0, 0);
        brandText.addView(text("RENFE MONITOR", 12, PINK, Typeface.BOLD));
        brandText.addView(text("Busca tu plaza", 29, WHITE, Typeface.BOLD));
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

        Button saved = smallActionButton("☰  MIS BÚSQUEDAS");
        saved.setOnClickListener(v -> showSavedScreen());
        brand.addView(saved, new LinearLayout.LayoutParams(dp(160), dp(48)));

        root.addView(brand);

        TextView subtitle = text("Monitorización automática · confirmación doble · segundo plano",
                13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(10);
        subLp.bottomMargin = dp(18);
        root.addView(subtitle, subLp);

        LinearLayout routeCard = card();
        routeCard.addView(sectionTitle("Trayecto"));
        routeCard.addView(fieldLabel("ORIGEN"));
        origin = stationField("Selecciona estación");
        routeCard.addView(origin, fieldParams());

        routeCard.addView(fieldLabel("DESTINO"));
        destination = stationField("Selecciona estación");
        LinearLayout.LayoutParams dl = fieldParams();
        dl.topMargin = dp(10);
        routeCard.addView(destination, dl);
        root.addView(routeCard, cardParams());

        LinearLayout searchCard = card();
        searchCard.addView(sectionTitle("Búsqueda"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout dateBox = smallBox("FECHA DEL VIAJE");
        date = edit(defaultDate(), InputType.TYPE_NULL);
        date.setFocusable(false);
        date.setClickable(true);
        date.setOnClickListener(v -> openDatePicker());
        dateBox.addView(date, fieldParams());
        row.addView(dateBox, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout timeBox = smallBox("HORA DE SALIDA");
        timeBox.setPadding(dp(8), 0, 0, 0);
        time = stationField("Selecciona horario");
        time.setText("", false);
        time.setFocusable(false);
        time.setClickable(true);
        time.setOnClickListener(v -> {
            if (departureLabels.isEmpty()) loadDepartureTimes();
            else showDepartureChooser();
        });
        timeBox.addView(time, fieldParams());
        row.addView(timeBox, new LinearLayout.LayoutParams(0, -2, 1f));

        searchCard.addView(row);

        LinearLayout intervalBox = smallBox("REVISAR CADA");
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        interval = edit("20", InputType.TYPE_CLASS_NUMBER);
        intervalRow.addView(interval, new LinearLayout.LayoutParams(0, dp(50), 1f));

        intervalUnit = new Spinner(this);
        intervalUnit.setAdapter(intervalUnitAdapter());
        intervalUnit.setBackground(round(FIELD, BORDER, 14));
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(dp(132), dp(50));
        unitLp.leftMargin = dp(10);
        intervalRow.addView(intervalUnit, unitLp);
        intervalBox.addView(intervalRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams intervalBoxLp = new LinearLayout.LayoutParams(-1, -2);
        intervalBoxLp.topMargin = dp(12);
        searchCard.addView(intervalBox, intervalBoxLp);
        TextView info = text("Solo trenes directos con tarifa normal disponible.", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(10);
        searchCard.addView(info, infoLp);
        root.addView(searchCard, cardParams());

        LinearLayout alertCard = card();
        alertCard.addView(sectionTitle("Alertas"));

        LinearLayout soundRow = new LinearLayout(this);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView soundLabel = text("🔔 Sonido al encontrar plaza", 14, WHITE, Typeface.NORMAL);
        soundRow.addView(soundLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sound = new Switch(this);
        sound.setChecked(alertPrefs().getBoolean("alert_sound", true));
        sound.setOnCheckedChangeListener((buttonView, isChecked) ->
                alertPrefs().edit().putBoolean("alert_sound", isChecked).apply());
        soundRow.addView(sound);
        alertCard.addView(soundRow);

        LinearLayout vibRow = new LinearLayout(this);
        vibRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView vibLabel = text("📳 Vibración al encontrar plaza", 14, WHITE, Typeface.NORMAL);
        vibRow.addView(vibLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch vibration = new Switch(this);
        vibration.setChecked(alertPrefs().getBoolean("alert_vibration", true));
        vibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                alertPrefs().edit().putBoolean("alert_vibration", isChecked).apply());
        vibRow.addView(vibration);
        alertCard.addView(vibRow);
        root.addView(alertCard, cardParams());

        LinearLayout statusCard = card();
        LinearLayout sl = new LinearLayout(this);
        sl.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = text("●", 17, MUTED, Typeface.BOLD);
        sl.addView(statusDot, new LinearLayout.LayoutParams(dp(24), -2));
        status = text("Preparando monitor…", 14, WHITE, Typeface.BOLD);
        sl.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        statusCard.addView(sl);

        metrics = text("Conexión: esperando · 0 consultas", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams metricLp = new LinearLayout.LayoutParams(-1, -2);
        metricLp.topMargin = dp(8);
        statusCard.addView(metrics, metricLp);

        TextView bgInfo = text("La búsqueda continúa en segundo plano mientras el monitor esté activo. No se avisa hasta confirmar una plaza.",
                12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams bgLp = new LinearLayout.LayoutParams(-1, -2);
        bgLp.topMargin = dp(8);
        statusCard.addView(bgInfo, bgLp);
        root.addView(statusCard, cardParams());

        Button start = button("▶  INICIAR BÚSQUEDA", PINK, WHITE);
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(55));
        startLp.bottomMargin = dp(10);
        root.addView(start, startLp);

        Button stop = button("■  DETENER", FIELD, WHITE);
        root.addView(stop, new LinearLayout.LayoutParams(-1, dp(50)));

        start.setOnClickListener(v -> startMonitor());
        stop.setOnClickListener(v -> stopMonitor());

        TextView foot = text("Renfe Monitor · v3.1", 11, Color.rgb(135, 126, 149), Typeface.NORMAL);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, -2);
        footLp.topMargin = dp(16);
        root.addView(foot, footLp);

        scroll.addView(root);
        setContentView(scroll);

        if (pendingDate != null && date != null) date.setText(pendingDate);
        if (pendingTime != null && time != null) time.setText(pendingTime, false);
        if (pendingInterval != null && interval != null) interval.setText(String.valueOf(pendingInterval));
        if (pendingIntervalUnit != null && intervalUnit != null) {
            intervalUnit.setSelection("minutos".equalsIgnoreCase(pendingIntervalUnit) ? 1 : 0);
        }
    }

    void showSavedScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(30));

        Button back = smallActionButton("←  VOLVER");
        back.setOnClickListener(v -> showMainScreen());
        root.addView(back, new LinearLayout.LayoutParams(dp(120), dp(46)));

        TextView title = text("MIS BÚSQUEDAS", 28, WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(14);
        root.addView(title, titleLp);
        root.addView(text("Tus búsquedas guardadas y las plazas confirmadas recientemente.", 13, MUTED, Typeface.NORMAL));

        LinearLayout savedCard = card();
        savedCard.addView(sectionTitle("Guardadas"));

        JSONArray saved = getJsonArray(SEARCHES);
        if (saved.length() == 0) {
            savedCard.addView(text("Todavía no tienes búsquedas guardadas. Al iniciar una búsqueda se guardará aquí automáticamente.",
                    13, MUTED, Typeface.NORMAL));
        } else {
            for (int i = 0; i < saved.length(); i++) {
                JSONObject o = saved.optJSONObject(i);
                if (o == null) continue;

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(12), dp(12), dp(12), dp(12));
                item.setBackground(round(FIELD, BORDER, 14));

                String route = o.optString("originName", "Origen") + " → " + o.optString("destName", "Destino");
                item.addView(text(route, 15, WHITE, Typeface.BOLD));
                item.addView(text(o.optString("date", "--") + " · " + o.optString("time", "--") +
                        " · cada " + formatInterval(o), 12, MUTED, Typeface.NORMAL));

                String key = o.optString("key", "");
                boolean active = isCurrentSearchActive(key);
                item.addView(text(active ? "● ACTIVA" : "○ GUARDADA", 11,
                        active ? GREEN : MUTED, Typeface.BOLD));

                LinearLayout actions = new LinearLayout(this);
                actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                Button load = smallActionButton("CARGAR");
                Button del = smallActionButton("ELIMINAR");
                load.setOnClickListener(v -> loadSavedSearch(o));
                del.setOnClickListener(v -> {
                    deleteSavedSearch(key);
                    showSavedScreen();
                });
                actions.addView(load, new LinearLayout.LayoutParams(dp(105), dp(44)));
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(110), dp(44));
                delLp.leftMargin = dp(8);
                actions.addView(del, delLp);
                item.addView(actions);

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
                itemLp.bottomMargin = dp(10);
                savedCard.addView(item, itemLp);
            }
        }
        root.addView(savedCard, cardParams());

        LinearLayout historyCard = card();
        historyCard.addView(sectionTitle("Historial de plazas"));

        JSONArray history = getJsonArray(HISTORY);
        if (history.length() == 0) {
            historyCard.addView(text("Aquí aparecerán las plazas que el monitor confirme.", 13, MUTED, Typeface.NORMAL));
        } else {
            for (int i = 0; i < history.length(); i++) {
                JSONObject h = history.optJSONObject(i);
                if (h == null) continue;

                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(12), dp(12), dp(12), dp(12));
                item.setBackground(round(FIELD, BORDER, 14));

                item.addView(text("✓ " + h.optString("originName", "") + " → " +
                        h.optString("destName", ""), 14, GREEN, Typeface.BOLD));
                item.addView(text(h.optString("date", "") + " · " + h.optString("time", "") +
                        " · " + formatTimestamp(h.optLong("timestamp", 0)), 12, MUTED, Typeface.NORMAL));
                item.addView(text(h.optString("detail", ""), 13, WHITE, Typeface.NORMAL));

                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
                itemLp.bottomMargin = dp(10);
                historyCard.addView(item, itemLp);
            }
        }

        if (history.length() > 0) {
            Button clear = button("🗑  LIMPIAR HISTORIAL", FIELD, WHITE);
            clear.setOnClickListener(v -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(HISTORY).apply();
                showSavedScreen();
            });
            historyCard.addView(clear, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        root.addView(historyCard, cardParams());

        TextView foot = text("Renfe Monitor · v3.1", 11, Color.rgb(135, 126, 149), Typeface.NORMAL);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);

        scroll.addView(root);
        setContentView(scroll);
    }

    void loadSavedSearch(JSONObject o) {
        pendingOriginCode = o.optString("originCode", "");
        pendingDestinationCode = o.optString("destCode", "");
        pendingDate = o.optString("date", defaultDate());
        pendingTime = o.optString("time", "17:40");
        pendingInterval = o.optInt("interval", 20);
        pendingIntervalUnit = o.optString("intervalUnit", "segundos");
        showMainScreen();
    }

    void showMainScreen() {
        buildMainUi();
        loadStations();
    }

    boolean isCurrentSearchActive(String key) {
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getBoolean("active", false) && key.equals(p.getString("searchKey", ""));
    }

    void deleteSavedSearch(String key) {
        JSONArray src = getJsonArray(SEARCHES);
        JSONArray out = new JSONArray();
        for (int i = 0; i < src.length(); i++) {
            JSONObject o = src.optJSONObject(i);
            if (o == null) continue;
            if (!key.equals(o.optString("key", ""))) out.put(o);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(SEARCHES, out.toString()).apply();
    }

    void upsertSavedSearch(String on, String oc, String dn, String dc, String ds, String ts,
                           int intervalValue, String unit, int sec) {
        JSONArray src = getJsonArray(SEARCHES);
        JSONArray out = new JSONArray();
        String key = oc + "|" + dc + "|" + ds + "|" + ts;
        boolean replaced = false;
        try {
            JSONObject item = new JSONObject();
            item.put("key", key);
            item.put("originName", on);
            item.put("originCode", oc);
            item.put("destName", dn);
            item.put("destCode", dc);
            item.put("date", ds);
            item.put("time", ts);
            item.put("interval", intervalValue);
            item.put("intervalUnit", unit);
            item.put("intervalSeconds", sec);
            item.put("updated", System.currentTimeMillis());

            out.put(item);
            replaced = true;

            for (int i = 0; i < src.length() && out.length() < 20; i++) {
                JSONObject old = src.optJSONObject(i);
                if (old == null) continue;
                if (key.equals(old.optString("key", ""))) continue;
                out.put(old);
            }
        } catch (Exception ignored) {}

        if (replaced) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(SEARCHES, out.toString()).apply();
        }
    }

    void addHistory(JSONObject item) {
        JSONArray src = getJsonArray(HISTORY);
        JSONArray out = new JSONArray();
        out.put(item);
        for (int i = 0; i < src.length() && out.length() < 50; i++) {
            JSONObject h = src.optJSONObject(i);
            if (h != null) out.put(h);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(HISTORY, out.toString()).apply();
    }

    JSONArray getJsonArray(String key) {
        try {
            return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    android.content.SharedPreferences alertPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    String defaultDate() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }

    String formatTimestamp(long t) {
        if (t <= 0) return "sin fecha";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(t));
    }

    void refreshMonitorState() {
        if (status == null || metrics == null) return;
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        boolean active = p.getBoolean("active", false);
        boolean found = p.getBoolean("found", false);
        String detail = p.getString("detail", "");
        String conn = p.getString("connection", "WAITING");
        long last = p.getLong("lastCheck", 0);
        int count = p.getInt("queryCount", 0);
        String error = p.getString("lastError", "");

        if (found) {
            status.setText("✓ ¡PLAZA ENCONTRADA!  " + detail);
        } else if (active) {
            status.setText("Monitor activo · buscando en segundo plano…");
        } else {
            status.setText("Monitor detenido.");
        }

        String connLabel;
        int connColor;
        if ("ONLINE".equals(conn)) {
            connLabel = "RENFE ONLINE";
            connColor = GREEN;
        } else if ("ERROR".equals(conn)) {
            connLabel = "RENFE ERROR";
            connColor = RED;
        } else {
            connLabel = "RENFE ESPERANDO";
            connColor = YELLOW;
        }

        if (statusDot != null) statusDot.setTextColor(connColor);
        String lastText = last > 0 ? " · última " + formatTimestamp(last) : "";
        metrics.setText(connLabel + " · " + count + " consultas" + lastText +
                (("ERROR".equals(conn) && !error.isEmpty()) ? " · reintentando" : ""));
        metrics.setTextColor(MUTED);
    }

    void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
            f.setLenient(false);
            Date current = f.parse(date.getText().toString().trim());
            if (current != null) cal.setTime(current);
        } catch (Exception ignored) {}

        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            date.setText(String.format(Locale.ROOT, "%02d/%02d/%04d", day, month + 1, year));
            loadDepartureTimes(true);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle("Fecha del viaje");
        dialog.show();
    }

    ArrayAdapter<String> intervalUnitAdapter() {
        String[] units = {"segundos", "minutos"};
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, units) {
            TextView style(TextView v) {
                v.setTextColor(WHITE);
                v.setTextSize(13);
                v.setPadding(dp(10), dp(8), dp(8), dp(8));
                v.setTypeface(Typeface.create("sans", Typeface.NORMAL));
                return v;
            }

            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                return style(v);
            }

            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                style(v);
                v.setBackgroundColor(CARD);
                return v;
            }
        };
    }

    String formatInterval(JSONObject o) {
        String unit = o.optString("intervalUnit", "segundos");
        int value = o.optInt("interval", 20);
        if (!o.has("intervalUnit")) unit = "segundos"; // búsqueda guardada en una versión anterior
        return value + " " + unit;
    }

    String extractTime(String text) {
        if (text == null) return "";
        String value = text.trim();
        if (value.length() >= 5 && value.substring(0, 5).matches("\\d{2}:\\d{2}")) {
            return value.substring(0, 5);
        }
        return value;
    }

    void showDepartureChooser() {
        if (departureLabels == null || departureLabels.isEmpty()) {
            Toast.makeText(this, "No hay horarios cargados para ese trayecto y fecha.", Toast.LENGTH_LONG).show();
            loadDepartureTimes();
            return;
        }
        String[] options = departureLabels.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Horarios reales de Renfe")
                .setItems(options, (dialog, which) -> {
                    String departure = extractTime(options[which]);
                    if (!departure.isEmpty()) time.setText(departure, false);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void loadDepartureTimes() {
        loadDepartureTimes(false);
    }

    void loadDepartureTimes(boolean clearSelection) {
        if (origin == null || destination == null || date == null || time == null || metrics == null) return;
        String originLabel = origin.getText().toString().trim();
        String destinationLabel = destination.getText().toString().trim();
        String oc = codeFromLabel(originLabel);
        String dc = codeFromLabel(destinationLabel);
        String ds = date.getText().toString().trim();
        if (oc == null || dc == null || oc.equals(dc)) return;
        try {
            validateDate(ds);
        } catch (Exception e) {
            return;
        }

        final int request = ++scheduleRequest;
        departureLabels = new ArrayList<>();
        if (clearSelection) time.setText("", false);
        metrics.setText("Consultando horarios reales de Renfe…");
        new Thread(() -> {
            try {
                ArrayList<RenfeClient.Journey> journeys = RenfeClient.getDepartures(
                        labelName(originLabel), oc, labelName(destinationLabel), dc, ds);
                Collections.sort(journeys, Comparator.comparing(j -> j.departure == null ? "" : j.departure));

                ArrayList<String> labels = new ArrayList<>();
                int directCount = 0;
                int connectionCount = 0;
                HashSet<String> seen = new HashSet<>();
                for (RenfeClient.Journey j : journeys) {
                    if (j.departure == null || j.departure.trim().isEmpty()) continue;
                    String routeKind;
                    if (j.direct) {
                        routeKind = "DIRECTO";
                    } else if (j.transferCount > 0) {
                        routeKind = j.transferCount == 1 ? "1 ENLACE" : j.transferCount + " ENLACES";
                    } else {
                        routeKind = "CON ENLACE";
                    }
                    String label = j.departure + " · llega " + safeText(j.arrival)
                            + " · " + routeKind
                            + (j.type == null || j.type.isEmpty() ? "" : " · " + j.type)
                            + (j.train == null || j.train.isEmpty() ? "" : " · trenes " + j.train);
                    // Conserva servicios distintos aunque compartan hora de salida.
                    if (seen.add(label)) {
                        labels.add(label);
                        if (j.direct) directCount++;
                        else connectionCount++;
                    }
                }

                final int directTotal = directCount;
                final int connectionTotal = connectionCount;
                runOnUiThread(() -> {
                    if (request != scheduleRequest || isFinishing()) return;
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this, android.R.layout.simple_list_item_1, labels) {
                        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getView(position, convertView, parent);
                            styleStation(v);
                            return v;
                        }
                        @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                            styleStation(v);
                            return v;
                        }
                    };
                    time.setAdapter(adapter);
                    departureLabels = new ArrayList<>(labels);
                    if (labels.isEmpty()) {
                        metrics.setText("Renfe no devolvió horarios para esa fecha y trayecto, ni directos ni con enlace.");
                    } else {
                        metrics.setText("Horarios reales: " + directTotal + " directos · " +
                                connectionTotal + " con enlace. Pulsa HORA DE SALIDA para elegir.");
                    }
                    refreshMonitorStateIfMonitoring();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != scheduleRequest || isFinishing()) return;
                    metrics.setText("No se pudieron consultar los horarios de Renfe.");
                });
            }
        }, "RenfeDepartures").start();
    }

    String safeText(String value) {
        return value == null ? "" : value;
    }

    void refreshMonitorStateIfMonitoring() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.getBoolean("active", false) || p.getBoolean("found", false)) refreshMonitorState();
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
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            this, android.R.layout.simple_list_item_1, labels) {
                        @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getView(position, convertView, parent);
                            styleStation(v);
                            return v;
                        }

                        @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                            styleStation(v);
                            return v;
                        }
                    };
                    origin.setAdapter(adapter);
                    destination.setAdapter(adapter);

                    if (pendingOriginCode != null && !pendingOriginCode.isEmpty()) {
                        origin.setText(findLabelByCode(pendingOriginCode), false);
                        destination.setText(findLabelByCode(pendingDestinationCode), false);
                        pendingOriginCode = null;
                        pendingDestinationCode = null;
                        pendingDate = null;
                        pendingTime = null;
                        pendingInterval = null;
                        pendingIntervalUnit = null;
                    } else if (!stationList.items.isEmpty()) {
                        origin.setText(findDefault("ALCAZAR"), false);
                        destination.setText(findDefault("ALICANTE"), false);
                    }

                    origin.setOnItemClickListener((parent, view, position, id) -> loadDepartureTimes(true));
                    destination.setOnItemClickListener((parent, view, position, id) -> loadDepartureTimes(true));

                    status.setText("Listo · " + stationList.items.size() + " estaciones");
                    refreshMonitorState();
                    loadDepartureTimes();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("No se pudieron cargar las estaciones");
                    if (statusDot != null) statusDot.setTextColor(RED);
                });
            }
        }).start();
    }

    void styleStation(TextView v) {
        v.setTextColor(WHITE);
        v.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        v.setTextSize(16);
        v.setPadding(dp(16), dp(12), dp(16), dp(12));
        v.setBackgroundColor(CARD);
    }

    String findLabelByCode(String code) {
        for (RenfeClient.Station s : stationList.items) {
            if (s.code.equals(code)) return s.name + " [" + s.code + "]";
        }
        return findDefault("");
    }

    String findDefault(String q) {
        String f = q.toLowerCase(Locale.ROOT);
        for (RenfeClient.Station s : stationList.items) {
            if (s.name.toLowerCase(Locale.ROOT).contains(f)) {
                return s.name + " [" + s.code + "]";
            }
        }
        return stationList.items.isEmpty() ? "" :
                stationList.items.get(0).name + " [" + stationList.items.get(0).code + "]";
    }

    void startMonitor() {
        try {
            String o = origin.getText().toString().trim();
            String d = destination.getText().toString().trim();
            String oc = codeFromLabel(o), dc = codeFromLabel(d);

            if (oc == null || dc == null) {
                status.setText("Selecciona las estaciones de la lista.");
                return;
            }
            if (oc.equals(dc)) {
                status.setText("Origen y destino no pueden coincidir.");
                return;
            }

            String ds = date.getText().toString().trim();
            String ts = extractTime(time.getText().toString().trim());
            validateDate(ds);
            if (ts.isEmpty()) {
                status.setText("Selecciona una hora en los horarios reales de Renfe.");
                return;
            }
            validateTime(ts);

            int intervalValue = Integer.parseInt(interval.getText().toString().trim());
            if (intervalValue <= 0) {
                status.setText("El intervalo debe ser mayor que cero.");
                return;
            }
            String unit = intervalUnit.getSelectedItemPosition() == 1 ? "minutos" : "segundos";
            long calculated = (long) intervalValue * ("minutos".equals(unit) ? 60L : 1L);
            if (calculated > 86400L) {
                status.setText("El intervalo máximo permitido es de 24 horas.");
                return;
            }
            if (calculated < 10L) {
                intervalValue = 10;
                calculated = 10L;
                interval.setText("10");
                Toast.makeText(this, "El intervalo mínimo es de 10 segundos.", Toast.LENGTH_LONG).show();
            }
            int sec = (int) calculated;

            upsertSavedSearch(labelName(o), oc, labelName(d), dc, ds, ts, intervalValue, unit, sec);

            Intent i = new Intent(this, MonitorService.class);
            i.putExtra("originName", labelName(o));
            i.putExtra("originCode", oc);
            i.putExtra("destName", labelName(d));
            i.putExtra("destCode", dc);
            i.putExtra("date", ds);
            i.putExtra("time", ts);
            i.putExtra("interval", sec);

            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("active", true)
                    .putBoolean("found", false)
                    .putString("detail", "")
                    .putString("searchKey", oc + "|" + dc + "|" + ds + "|" + ts)
                    .putString("connection", "WAITING")
                    .putInt("queryCount", 0)
                    .putLong("lastCheck", 0)
                    .putString("lastError", "")
                    .apply();

            status.setText("Monitor activo · buscando en segundo plano…");
            refreshMonitorState();
        } catch (Exception e) {
            status.setText("Revisa fecha, hora, intervalo y estaciones.");
        }
    }

    void stopMonitor() {
        stopService(new Intent(this, MonitorService.class));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("active", false)
                .putBoolean("found", false)
                .putString("detail", "")
                .putString("connection", "WAITING")
                .apply();
        status.setText("Monitor detenido.");
        refreshMonitorState();
    }

    Button smallActionButton(String label) {
        Button b = button(label, FIELD, WHITE);
        b.setTextSize(11);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    LinearLayout smallBox(String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(fieldLabel(label));
        return box;
    }

    TextView sectionTitle(String s) {
        TextView v = text(s, 18, WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    TextView fieldLabel(String s) {
        return text(s, 10, MUTED, Typeface.BOLD);
    }

    AutoCompleteTextView stationField(String hint) {
        AutoCompleteTextView v = new AutoCompleteTextView(this);
        v.setHint(hint);
        v.setTextSize(16);
        v.setSingleLine(true);
        v.setThreshold(1);
        v.setTextColor(WHITE);
        v.setHintTextColor(Color.rgb(135, 126, 149));
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        v.setBackground(round(FIELD, BORDER, 14));
        v.setDropDownBackgroundDrawable(round(CARD, BORDER, 12));
        return v;
    }

    EditText edit(String value, int type) {
        EditText v = new EditText(this);
        v.setText(value);
        v.setSingleLine(true);
        v.setTextSize(14);
        v.setTextColor(WHITE);
        v.setHintTextColor(Color.rgb(135, 126, 149));
        v.setInputType(type);
        v.setPadding(dp(12), dp(8), dp(12), dp(8));
        v.setBackground(round(FIELD, BORDER, 14));
        return v;
    }

    Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, bg == FIELD ? BORDER : bg, 17));
        return b;
    }

    TextView text(String s, float size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", style));
        return v;
    }

    LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        v.setBackground(round(CARD, BORDER, 20));
        return v;
    }

    LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        return lp;
    }

    LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(5);
        return lp;
    }

    LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    GradientDrawable round(int fill, int stroke, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(r));
        d.setStroke(dp(1), stroke);
        return d;
    }

    GradientDrawable gradient(int a, int b) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{a, b});
    }

    int dp(float x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    String labelName(String s) {
        int p = s.lastIndexOf(" [");
        return p > 0 ? s.substring(0, p).trim() : s;
    }

    String codeFromLabel(String s) {
        int a = s.lastIndexOf('['), b = s.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        return s.substring(a + 1, b).trim();
    }

    void validateDate(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        f.setLenient(false);
        f.parse(s);
    }

    void validateTime(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);
        f.setLenient(false);
        f.parse(s);
    }
}
