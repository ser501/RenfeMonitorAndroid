package es.renfemonitor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.Gravity;
import android.content.Intent;
import android.net.Uri;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RenfeBookingActivity extends Activity {
    private static final String BASE = "https://venta.renfe.com";
    private WebView web;
    private ProgressBar progress;
    private boolean submitted = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 8, 24));
        getWindow().setNavigationBarColor(Color.rgb(10, 8, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackgroundColor(Color.rgb(29, 23, 43));

        TextView title = new TextView(this);
        title.setText("ABRIENDO TU VIAJE EN RENFE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getIntent().getStringExtra("originName") + " → "
                + getIntent().getStringExtra("destName") + " · "
                + getIntent().getStringExtra("date") + " · "
                + getIntent().getStringExtra("time"));
        subtitle.setTextColor(Color.rgb(220, 210, 230));
        subtitle.setTextSize(12);
        header.addView(subtitle);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setSupportMultipleWindows(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                progress.setIndeterminate(false);
                progress.setVisibility(View.GONE);
                if (!submitted && url != null && url.contains("venta.renfe.com/vol/inicio.do")) {
                    submitted = true;
                    progress.setVisibility(View.VISIBLE);
                    progress.setIndeterminate(true);
                    submitSearch();
                }
            }
        });

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        web.loadUrl(BASE + "/vol/inicio.do");
    }

    private void submitSearch() {
        String on = getIntent().getStringExtra("originName");
        String oc = getIntent().getStringExtra("originCode");
        String dn = getIntent().getStringExtra("destName");
        String dc = getIntent().getStringExtra("destCode");
        String date = getIntent().getStringExtra("date");
        String time = getIntent().getStringExtra("time");

        String form = param("tipoBusqueda", "autocomplete")
                + "&" + param("currenLocation", "menuBusqueda")
                + "&" + param("vengoderenfecom", "SI")
                + "&" + param("desOrigen", on)
                + "&" + param("desDestino", dn)
                + "&" + param("cdgoOrigen", oc)
                + "&" + param("cdgoDestino", dc)
                + "&" + param("idiomaBusqueda", "ES")
                + "&" + param("FechaIdaSel", date)
                + "&" + param("FechaVueltaSel", "")
                + "&" + param("_fechaIdaVisual", date)
                + "&" + param("_fechaVueltaVisual", "")
                + "&" + param("adultos_", "1")
                + "&" + param("ninos_", "0")
                + "&" + param("ninosMenores", "0")
                + "&" + param("codPromocional", "")
                + "&" + param("plazaH", "false")
                + "&" + param("sinEnlace", "true")
                + "&" + param("conMascota", "false")
                + "&" + param("conBicicleta", "false")
                + "&" + param("asistencia", "false")
                + "&" + param("franjaHoraI", time)
                + "&" + param("franjaHoraV", "")
                + "&" + param("Idioma", "es")
                + "&" + param("Pais", "ES");

        web.postUrl(BASE + "/vol/buscarTren.do",
                form.getBytes(StandardCharsets.UTF_8));
    }

    private String param(String key, String value) {
        try {
            return URLEncoder.encode(key, "UTF-8") + "="
                    + URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return key + "=";
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
