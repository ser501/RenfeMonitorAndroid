package es.renfemonitor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class RenfeClient {
    static final String BASE = "https://venta.renfe.com";
    static final String STATIONS = "https://www.renfe.com/content/dam/renfe/es/General/buscadores/javascript/estacionesEstaticas.js";

    static class Station {
        final String code, name;
        Station(String c, String n) { code=c; name=n; }
    }
    static class StationList {
        final ArrayList<Station> items;
        StationList(ArrayList<Station> i) { items=i; }
    }

    static StationList getStations() throws Exception {
        String s = get(STATIONS);
        ArrayList<Station> out = new ArrayList<>();
        Pattern p = Pattern.compile(
            "\\{[^{}]*?cdgoEstacion\\s*:\\s*[\"']([^\"']+)[\"'][^{}]*?desgEstacion\\s*:\\s*[\"']([^\"']+)[\"'][^{}]*?\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(s);
        while (m.find()) {
            String code = m.group(1).trim(), name = m.group(2).trim();
            if (!code.isEmpty() && !name.isEmpty()) out.add(new Station(code,name));
        }
        if (out.isEmpty()) {
            Pattern j = Pattern.compile(
                "\"cdgoEstacion\"\\s*:\\s*\"([^\"]+)\".*?\"desgEstacion\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL);
            m = j.matcher(s);
            while (m.find()) out.add(new Station(m.group(1).trim(),m.group(2).trim()));
        }
        if (out.isEmpty()) throw new IOException("No se pudo interpretar el catálogo de estaciones");
        return new StationList(out);
    }

    static boolean search(String on, String oc, String dn, String dc, String date, String target) throws Exception {
        CookieManager cm = new CookieManager();
        CookieHandler.setDefault(cm);

        get(BASE + "/vol/inicio.do");

        Map<String,String> f = new LinkedHashMap<>();
        f.put("tipoBusqueda","autocomplete"); f.put("currenLocation","menuBusqueda");
        f.put("vengoderenfecom","SI"); f.put("desOrigen",on); f.put("desDestino",dn);
        f.put("cdgoOrigen",oc); f.put("cdgoDestino",dc); f.put("idiomaBusqueda","ES");
        f.put("FechaIdaSel",date); f.put("FechaVueltaSel","");
        f.put("_fechaIdaVisual",date); f.put("_fechaVueltaVisual","");
        f.put("adultos_","1"); f.put("ninos_","0"); f.put("ninosMenores","0");
        f.put("codPromocional",""); f.put("plazaH","false"); f.put("sinEnlace","false");
        f.put("conMascota","false"); f.put("conBicicleta","false"); f.put("asistencia","false");
        f.put("franjaHoraI",""); f.put("franjaHoraV",""); f.put("Idioma","es"); f.put("Pais","ES");
        post(BASE + "/vol/buscarTren.do", f);

        String dwrCookie = getCookie(cm, BASE, "DWRSESSIONID");
        String sid = (dwrCookie == null || dwrCookie.isEmpty())
                ? randomSession() : dwrCookie + "/renfeandroid";

        String body =
            "callCount=1\nwindowName=\nc0-scriptName=trainEnlacesManager\n" +
            "c0-methodName=getTrainsList\nc0-id=0\n" +
            "c0-param0=Object_Object:{atendo:reference:c0-e1, sinEnlace:reference:c0-e2, plazaH:reference:c0-e3, tipoFranjaI:reference:c0-e4, tipoFranjaV:reference:c0-e5, horaFranjaIda:reference:c0-e6, horaFranjaVuelta:reference:c0-e7, fechaSalida:reference:c0-e8, fechaVuelta:reference:c0-e9, adultos:reference:c0-e10, ninos:reference:c0-e11, ninosMenores:reference:c0-e12, trayecto:reference:c0-e13, idaVuelta:reference:c0-e14, conMascota:reference:c0-e15, conBicicleta:reference:c0-e16}\n" +
            "c0-e1=string:false\nc0-e2=string:false\nc0-e3=string:false\nc0-e4=string:\nc0-e5=string:\nc0-e6=string:\nc0-e7=string:\n" +
            "c0-e8=string:" + esc(date) + "\nc0-e9=string:\nc0-e10=string:1\nc0-e11=string:0\nc0-e12=string:0\nc0-e13=string:I\nc0-e14=string:false\nc0-e15=string:false\nc0-e16=string:false\n" +
            "batchId=0\ninstanceId=0\npage=%2Fvol%2FbuscarTrenEnlaces.do\nscriptSessionId=" + sid + "\n";

        String r = postRaw(BASE + "/vol/dwr/call/plaincall/trainEnlacesManager.getTrainsList.dwr", body, "text/plain;charset=UTF-8");
        return containsAvailableAtExactHour(r, target);
    }

    static boolean containsAvailableAtExactHour(String r, String target) {
        Pattern p = Pattern.compile(
            "\"?horaSalida\"?\s*[:=]\s*\"?" + Pattern.quote(target) +
            "\"?[^{}]{0,1800}?\"?completo\"?\s*[:=]\s*false",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        if (p.matcher(r).find()) return true;
        Pattern p2 = Pattern.compile(
            "\"?completo\"?\s*[:=]\s*false[^{}]{0,1800}?\"?horaSalida\"?\s*[:=]\s*\"?" +
            Pattern.quote(target),
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        return p2.matcher(r).find();
    }

    static String getCookie(CookieManager cm, String base, String name) {
        try {
            URI u = URI.create(base);
            for (HttpCookie c : cm.getCookieStore().get(u)) if (name.equals(c.getName())) return c.getValue();
        } catch(Exception ignored) {}
        return null;
    }

    static String randomSession() {
        return UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT) + "/renfeandroid";
    }

    static String esc(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+","%20"); }
        catch(Exception e) { return s; }
    }

    static String get(String u) throws Exception { return request("GET",u,null,null); }

    static String post(String u, Map<String,String> f) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> e : f.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(esc(e.getKey())).append('=').append(esc(e.getValue()));
        }
        return request("POST",u,b.toString(),"application/x-www-form-urlencoded");
    }

    static String postRaw(String u,String body,String type) throws Exception {
        return request("POST",u,body,type);
    }

    static String request(String method, String u, String body, String type) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setRequestMethod(method);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36");
        c.setRequestProperty("Accept","*/*");
        c.setRequestProperty("Referer",BASE+"/vol/buscarTrenEnlaces.do");
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type",type);
            try(OutputStream os=c.getOutputStream()){ os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code=c.getResponseCode();
        InputStream in=code>=400?c.getErrorStream():c.getInputStream();
        if(in==null) throw new IOException("HTTP "+code);
        String text=read(in);
        if(code>=400) throw new IOException("HTTP "+code+" "+text.substring(0,Math.min(250,text.length())));
        return text;
    }

    static String read(InputStream in) throws Exception {
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            StringBuilder b=new StringBuilder(); String l;
            while((l=r.readLine())!=null)b.append(l).append('\n');
            return b.toString();
        }
    }
}
