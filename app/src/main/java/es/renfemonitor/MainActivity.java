package es.renfemonitor;

import android.Manifest;
import android.app.Activity;
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
    private static final int BG_TOP = Color.rgb(10, 8, 24);
    private static final int BG_BOTTOM = Color.rgb(31, 11, 42);
    private static final int CARD = Color.rgb(29, 23, 43);
    private static final int FIELD = Color.rgb(37, 30, 53);
    private static final int BORDER = Color.rgb(70, 58, 88);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(185, 177, 199);
    private static final int PINK = Color.rgb(232, 63, 173);
    private static final int GREEN = Color.rgb(81, 205, 113);

    AutoCompleteTextView origin, destination;
    EditText date, time, interval;
    TextView status;
    RenfeClient.StationList stationList;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_TOP);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
        buildUi();

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        loadStations();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        android.content.SharedPreferences p =
                getSharedPreferences("monitor_state", MODE_PRIVATE);
        if (p.getBoolean("found", false)) {
            String detail = p.getString("detail", "Plaza confirmada");
            status.setText("✓ ¡PLAZA ENCONTRADA!  " + detail);
        } else if (p.getBoolean("active", false)) {
            status.setText("Monitor activo · buscando en segundo plano…");
        }
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(gradient(BG_TOP, BG_BOTTOM, 270));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(30));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(es.renfemonitor.R.drawable.ic_launcher_app);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        brand.addView(icon, iconLp);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(14), 0, 0, 0);
        brandText.addView(text("RENFE MONITOR", 12, PINK, Typeface.BOLD));
        TextView title = text("Busca tu plaza", 29, WHITE, Typeface.BOLD);
        brandText.addView(title);
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1f));

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

        LinearLayout dateBox = smallBox("FECHA");
        date = edit(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()),
                InputType.TYPE_CLASS_DATETIME);
        dateBox.addView(date, fieldParams());
        row.addView(dateBox, weightParams());

        LinearLayout timeBox = smallBox("HORA");
        timeBox.setPadding(dp(7), 0, dp(7), 0);
        time = edit("17:40", InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        timeBox.addView(time, fieldParams());
        row.addView(timeBox, weightParams());

        LinearLayout intervalBox = smallBox("CADA");
        interval = edit("20 s", InputType.TYPE_CLASS_NUMBER);
        intervalBox.addView(interval, fieldParams());
        row.addView(intervalBox, weightParams());

        searchCard.addView(row);
        TextView info = text("Solo trenes directos con tarifa normal disponible.", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(10);
        searchCard.addView(info, infoLp);
        root.addView(searchCard, cardParams());

        LinearLayout statusCard = card();
        LinearLayout sl = new LinearLayout(this);
        sl.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = text("●", 17, GREEN, Typeface.BOLD);
        sl.addView(dot, new LinearLayout.LayoutParams(dp(24), -2));
        status = text("Preparando monitor…", 14, WHITE, Typeface.BOLD);
        sl.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        statusCard.addView(sl);

        TextView bgInfo = text("Al iniciar, puedes cerrar esta pantalla: la búsqueda continuará activa en segundo plano. No habrá alertas hasta que se confirme una plaza.", 12, MUTED, Typeface.NORMAL);
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

        TextView foot = text("Renfe Monitor · v2.1", 11, Color.rgb(135, 126, 149), Typeface.NORMAL);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, -2);
        footLp.topMargin = dp(16);
        root.addView(foot, footLp);

        scroll.addView(root);
        setContentView(scroll);
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

    GradientDrawable gradient(int a, int b, int angle) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setCornerRadius(0);
        return d;
    }

    int dp(float x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
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
                            v.setTextColor(WHITE);
                            v.setTypeface(Typeface.create("sans", Typeface.NORMAL));
                            v.setTextSize(16);
                            v.setPadding(dp(16), dp(12), dp(16), dp(12));
                            v.setBackgroundColor(CARD);
                            return v;
                        }

                        @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                            v.setTextColor(WHITE);
                            v.setTypeface(Typeface.create("sans", Typeface.NORMAL));
                            v.setTextSize(16);
                            v.setPadding(dp(16), dp(12), dp(16), dp(12));
                            v.setBackgroundColor(CARD);
                            return v;
                        }
                    };
                    origin.setAdapter(adapter);
                    destination.setAdapter(adapter);
                    if (!stationList.items.isEmpty()) {
                        origin.setText(findDefault("ALCAZAR"), false);
                        destination.setText(findDefault("ALICANTE"), false);
                    }
                    status.setText("Listo · " + stationList.items.size() + " estaciones");
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
            String ts = time.getText().toString().trim();
            validateDate(ds);
            validateTime(ts);
            int sec = Math.max(10, Integer.parseInt(interval.getText().toString().replace("s", "").trim()));

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

            getSharedPreferences("monitor_state", MODE_PRIVATE).edit()
                    .putBoolean("active", true)
                    .putBoolean("found", false)
                    .putString("detail", "")
                    .apply();

            status.setText("Monitor activo · buscando en segundo plano…");
        } catch (Exception e) {
            status.setText("Revisa fecha, hora, intervalo y estaciones.");
        }
    }

    void stopMonitor() {
        stopService(new Intent(this, MonitorService.class));
        getSharedPreferences("monitor_state", MODE_PRIVATE).edit()
                .putBoolean("active", false)
                .putBoolean("found", false)
                .putString("detail", "")
                .apply();
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
