package es.renfemonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RenfeBookingActivity extends Activity {
    private static final String BASE = "https://venta.renfe.com";
    private static final int BG = Color.rgb(10, 8, 24);
    private static final int CARD = Color.rgb(29, 23, 43);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(205, 197, 218);
    private static final int PINK = Color.rgb(232, 63, 173);

    private WebView web;
    private ProgressBar progress;
    private TextView status;
    private boolean submitted = false;

    private String originName, originCode, destinationName, destinationCode, date, time;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        Intent intent = getIntent();
        originName = safe(intent.getStringExtra("originName"));
        originCode = safe(intent.getStringExtra("originCode"));
        destinationName = safe(intent.getStringExtra("destName"));
        destinationCode = safe(intent.getStringExtra("destCode"));
        date = safe(intent.getStringExtra("date"));
        time = safe(intent.getStringExtra("time"));

        buildUi();

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                progress.setIndeterminate(true);
                status.setText("Conectando con Renfe…");
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!submitted) {
                    // Primero obtenemos la sesión/cookies del sitio oficial y después
                    // enviamos el formulario con el viaje ya rellenado.
                    submitted = true;
                    status.setText("Preparando el trayecto y consultando los resultados…");
                    view.postUrl(BASE + "/vol/buscarTren.do",
                            buildPostBody().getBytes(StandardCharsets.UTF_8));
                    return;
                }
                progress.setVisibility(View.GONE);
                status.setText("Trayecto enviado a Renfe. Selecciona el servicio y continúa con la compra.");
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) {}
                return true;
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    status.setText("Renfe no ha podido cargar los resultados. Pulsa REINTENTAR para volver a enviar el trayecto.");
                }
            }
        });

        status.setText("Abriendo el buscador oficial de Renfe…");
        web.loadUrl(BASE + "/vol/inicio.do");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setBackground(round(CARD, PINK, 16));

        TextView title = new TextView(this);
        title.setText("RENFE · CONTINUAR COMPRA");
        title.setTextColor(WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);

        TextView journey = new TextView(this);
        journey.setText(originName + " → " + destinationName
                + "\n" + date + " · salida " + time + " · 1 adulto");
        journey.setTextColor(MUTED);
        journey.setTextSize(13);
        LinearLayout.LayoutParams journeyLp = new LinearLayout.LayoutParams(-1, -2);
        journeyLp.topMargin = dp(6);
        header.addView(journey, journeyLp);

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextColor(WHITE);
        status.setTextSize(13);
        status.setPadding(dp(14), dp(9), dp(14), dp(9));
        root.addView(status);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(10), dp(7), dp(10), dp(7));
        bottom.setBackgroundColor(CARD);

        Button retry = makeButton("↻ REINTENTAR");
        retry.setOnClickListener(v -> {
            submitted = false;
            status.setText("Volviendo a preparar el trayecto…");
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);
            web.loadUrl(BASE + "/vol/inicio.do");
        });
        bottom.addView(retry, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button official = makeButton("WEB OFICIAL");
        official.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.renfe.com/es/es")));
            } catch (Exception ignored) {
                status.setText("No se ha podido abrir el navegador.");
            }
        });
        LinearLayout.LayoutParams officialLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        officialLp.leftMargin = dp(8);
        bottom.addView(official, officialLp);

        root.addView(bottom);
        setContentView(root);
    }

    private String buildPostBody() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("tipoBusqueda", "autocomplete");
        fields.put("currenLocation", "menuBusqueda");
        fields.put("vengoderenfecom", "SI");
        fields.put("desOrigen", originName);
        fields.put("desDestino", destinationName);
        fields.put("cdgoOrigen", originCode);
        fields.put("cdgoDestino", destinationCode);
        fields.put("idiomaBusqueda", "ES");
        fields.put("FechaIdaSel", date);
        fields.put("FechaVueltaSel", "");
        fields.put("_fechaIdaVisual", date);
        fields.put("_fechaVueltaVisual", "");
        fields.put("adultos_", "1");
        fields.put("ninos_", "0");
        fields.put("ninosMenores", "0");
        fields.put("codPromocional", "");
        fields.put("plazaH", "false");
        fields.put("sinEnlace", "true");
        fields.put("conMascota", "false");
        fields.put("conBicicleta", "false");
        fields.put("asistencia", "false");
        // Renfe interpreta esta hora como el inicio de la franja de salida.
        fields.put("franjaHoraI", time);
        fields.put("franjaHoraV", "");
        fields.put("Idioma", "es");
        fields.put("Pais", "ES");

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(encode(field.getKey())).append('=').append(encode(field.getValue()));
        }
        return body.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(round(Color.rgb(37, 30, 53), PINK, 12));
        return button;
    }

    private GradientDrawable round(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
