package es.renfemonitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(11, 87, 208);
    private static final int TEXT = Color.rgb(25, 31, 40);
    private static final int MUTED = Color.rgb(91, 99, 112);
    private static final int BG = Color.rgb(246, 248, 252);
    private static final int CARD = Color.WHITE;
    private static final int BORDER = Color.rgb(224, 228, 235);

    AutoCompleteTextView origin, destination;
    EditText date, time, interval;
    TextView status;
    RenfeClient.StationList stationList;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        buildUi();

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        loadStations();
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(root);

        TextView eyebrow = text("RENFE MONITOR", 13, BLUE, Typeface.BOLD);
        root.addView(eyebrow);

        TextView title = text("Busca tu plaza", 31, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(3);
        root.addView(title, titleLp);

        TextView subtitle = text("Monitorización automática de Renfe en segundo plano", 15, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.bottomMargin = dp(18);
        root.addView(subtitle, subLp);

        LinearLayout routeCard = card();
        routeCard.addView(sectionTitle("Trayecto"));

        routeCard.addView(fieldLabel("ORIGEN"));
        origin = stationField("Selecciona la estación de origen");
        routeCard.addView(origin, fieldParams());

        routeCard.addView(fieldLabel("DESTINO"));
        destination = stationField("Selecciona la estación de destino");
        LinearLayout.LayoutParams destLp = fieldParams();
        destLp.topMargin = dp(10);
        routeCard.addView(destination, destLp);
        root.addView(routeCard, cardParams());

        LinearLayout searchCard = card();
        searchCard.addView(sectionTitle("Búsqueda"));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.addView(fieldLabel("FECHA"));
        date = edit("dd/MM/yyyy",
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()),
                InputType.TYPE_CLASS_DATETIME);
        dateBox.addView(date, fieldParams());
        row.addView(dateBox, weightParams());

        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setPadding(dp(8), 0, dp(8), 0);
        timeBox.addView(fieldLabel("HORA"));
        time = edit("HH:mm", "17:40",
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        timeBox.addView(time, fieldParams());
        row.addView(timeBox, weightParams());

        LinearLayout intervalBox = new LinearLayout(this);
        intervalBox.setOrientation(LinearLayout.VERTICAL);
        intervalBox.addView(fieldLabel("CADA"));
        interval = edit("seg.", "20", InputType.TYPE_CLASS_NUMBER);
        intervalBox.addView(interval, fieldParams());
        row.addView(intervalBox, weightParams());

        searchCard.addView(row);

        TextView helper = text(
                "El monitor buscará únicamente trenes directos con plaza realmente disponible.",
                13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(-1, -2);
        helperLp.topMargin = dp(10);
        searchCard.addView(helper, helperLp);
        root.addView(searchCard, cardParams());

        LinearLayout statusCard = card();
        LinearLayout statusLine = new LinearLayout(this);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = text("●", 16, Color.rgb(58, 167, 91), Typeface.BOLD);
        statusLine.addView(dot, new LinearLayout.LayoutParams(dp(22), -2));

        status = text("Cargando estaciones…", 14, TEXT, Typeface.BOLD);
        statusLine.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        statusCard.addView(statusLine);

        TextView bgInfo = text(
                "Al iniciar, la búsqueda continúa aunque cierres esta pantalla. Solo recibirás una alerta cuando haya una plaza confirmada.",
                12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(8);
        statusCard.addView(bgInfo, infoLp);
        root.addView(statusCard, cardParams());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button start = actionButton("Iniciar búsqueda", BLUE, Color.WHITE);
        Button stop = actionButton("Detener", Color.WHITE, TEXT);
        stop.setBackground(round(BORDER, 1, 17));

        buttons.addView(start, buttonParams());
        LinearLayout.LayoutParams stopLp = buttonParams();
        stopLp.leftMargin = dp(10);
        buttons.addView(stop, stopLp);

        root.addView(buttons);

        start.setOnClickListener(v -> startMonitor());
        stop.setOnClickListener(v -> stopMonitor());
        setContentView(scroll);
    }

    TextView sectionTitle(String s) {
        TextView v = text(s, 18, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(13);
        v.setLayoutParams(lp);
        return v;
    }

    TextView fieldLabel(String s) {
        return text(s, 11, MUTED, Typeface.BOLD);
    }

    AutoCompleteTextView stationField(String hint) {
        AutoCompleteTextView v = new AutoCompleteTextView(this);
        v.setHint(hint);
        v.setTextSize(16);
        v.setSingleLine(true);
        v.setThreshold(1);
        v.setTextColor(TEXT);
        v.setHintTextColor(Color.rgb(150, 157, 168));
        v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        v.setPadding(dp(14), dp(11), dp(14), dp(11));
        v.setBackground(round(Color.WHITE, 1, 14));
        v.setDropDownBackgroundDrawable(round(Color.WHITE, 1, 12));
        return v;
    }

    EditText edit(String hint, String value, int type) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setText(value);
        v.setSingleLine(true);
        v.setTextSize(15);
        v.setTextColor(TEXT);
        v.setHintTextColor(Color.rgb(150, 157, 168));
        v.setInputType(type);
        v.setPadding(dp(14), dp(10), dp(14), dp(10));
        v.setBackground(round(Color.WHITE, 1, 14));
        return v;
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
        v.setBackground(round(CARD, 1, 20));
        return v;
    }

    LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        return lp;
    }

    LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(5);
        return lp;
    }

    LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(0, dp(54), 1f);
    }

    Button actionButton(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setBackground(round(bg, 0, 17));
        return b;
    }

    GradientDrawable round(int color, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke > 0) g.setStroke(dp(stroke), BORDER);
        return g;
    }

    int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
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
                            new ArrayAdapter<>(this,
                                    android.R.layout.simple_dropdown_item_1line, labels);
                    origin.setAdapter(adapter);
                    destination.setAdapter(adapter);

                    if (stationList.items.size() > 0) {
                        origin.setText(findDefault("ALCÁZAR"), false);
                        destination.setText(findDefault("ALICANTE"), false);
                    }

                    status.setText("Listo para buscar · " + stationList.items.size() + " estaciones");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("No se pudieron cargar las estaciones"));
            }
        }).start();
    }

    String findDefault(String q) {
        String f = q.toLowerCase(Locale.ROOT);
        for (RenfeClient.Station s : stationList.items) {
            if (s.name.toLowerCase(Locale.ROOT).contains(f)) {
                return s.name + " [" + s.code + "]";
            }
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

            status.setText("Buscando en segundo plano · " +
                    labelName(o) + " → " + labelName(d));
        } catch (Exception e) {
            status.setText("Revisa los datos introducidos.");
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
