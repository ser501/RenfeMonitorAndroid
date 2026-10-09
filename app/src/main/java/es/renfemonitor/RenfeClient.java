package es.renfemonitor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.Normalizer;
import java.util.regex.*;

public class RenfeClient {
    static final String BASE = "https://venta.renfe.com";
    static final String STATIONS = "https://www.renfe.com/content/dam/renfe/es/General/buscadores/javascript/estacionesEstaticas.js";
    static final String PAGE = "/vol/buscarTrenEnlaces.do";
    static final String TAG = "RenfeMonitor";

    static class Station {
        final String code, name;
        Station(String c, String n) { code=c; name=n; }
    }
    static class StationList {
        final ArrayList<Station> items;
        StationList(ArrayList<Station> i) { items=i; }
    }
    static class Journey {
        String departure, arrival, duration, type, train, from, to;
        boolean available, direct;
        double price;
        String toStringLine() {
            String p = price > 0 ? String.format(Locale.US, "%.2f €", price) : "sin precio";
            return departure + " → " + arrival + " | " + type + " | tren " + train + " | " + p;
        }
    }

    static StationList getStations() throws Exception {
        String s = get(STATIONS);
        ArrayList<Station> out = new ArrayList<>();
        Pattern p = Pattern.compile(
            "\\{[^{}]*?cdgoEstacion\\s*:\\s*[\\\"']([^\\\"']+)[\\\"'][^{}]*?desgEstacion\\s*:\\s*[\\\"']([^\\\"']+)[\\\"'][^{}]*?\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(s);
        while (m.find()) addStation(out, m.group(1), m.group(2));
        if (out.isEmpty()) {
            Pattern j = Pattern.compile(
                "\"cdgoEstacion\"\\s*:\\s*\"([^\"]+)\".*?\"desgEstacion\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL);
            m = j.matcher(s);
            while (m.find()) addStation(out, m.group(1), m.group(2));
        }
        if (out.isEmpty()) throw new IOException("No se pudo interpretar el catálogo de estaciones");
        return new StationList(out);
    }

    static void addStation(ArrayList<Station> out, String code, String name) {
        code = clean(code); name = clean(name);
        if (code.isEmpty() || name.isEmpty()) return;
        for (Station x : out) if (x.code.equals(code)) return;
        out.add(new Station(code, name));
    }

    static String clean(String s) {
        if (s == null) return "";
        // Si una respuesta antigua sustituyó la Á por el carácter de
        // reemplazo, recuperamos el nombre conocido antes de limpiar marcas.
        String restored = s
            .replace("ALC�ZAR", "ALCAZAR")
            .replace("Alc�zar", "Alcazar")
            .replace("alc�zar", "alcazar")
            .replace("ALC?ZAR", "ALCAZAR")
            .replace("Alc?zar", "Alcazar")
            .replace("alc?zar", "alcazar")
            .replace("\\u00c1","Á").replace("\\u00e1","á")
            .replace("\\u00c9","É").replace("\\u00e9","é")
            .replace("\\u00cd","Í").replace("\\u00ed","í")
            .replace("\\u00d3","Ó").replace("\\u00f3","ó")
            .replace("\\u00da","Ú").replace("\\u00fa","ú")
            .replace("\\u00d1","Ñ").replace("\\u00f1","ñ");

        // La app presenta los nombres sin tildes para evitar problemas visuales.
        String normalized = Normalizer.normalize(restored, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace("\\uFFFD", "");
        return normalized.trim();
    }

    // Devuelve los horarios directos publicados por Renfe para una ruta y fecha.
    // No exige que haya plaza ahora: el monitor puede vigilar que se libere después.
    static ArrayList<Journey> getDepartures(String on, String oc, String dn, String dc, String date) throws Exception {
        return search(on, oc, dn, dc, date, "");
    }

    static ArrayList<Journey> search(String on, String oc, String dn, String dc, String date, String target) throws Exception {
        CookieManager cm = new CookieManager();
        CookieHandler.setDefault(cm);

        get(BASE + "/vol/inicio.do");

        String d = date; // Renfe's booking form/DWR uses dd/MM/yyyy.
        Map<String,String> f = new LinkedHashMap<>();
        f.put("tipoBusqueda","autocomplete");
        f.put("currenLocation","menuBusqueda");
        f.put("vengoderenfecom","SI");
        f.put("desOrigen",on); f.put("desDestino",dn);
        f.put("cdgoOrigen",oc); f.put("cdgoDestino",dc);
        f.put("idiomaBusqueda","ES");
        f.put("FechaIdaSel",d); f.put("FechaVueltaSel","");
        f.put("_fechaIdaVisual",d); f.put("_fechaVueltaVisual","");
        f.put("adultos_","1"); f.put("ninos_","0"); f.put("ninosMenores","0");
        f.put("codPromocional",""); f.put("plazaH","false"); f.put("sinEnlace","true");
        f.put("conMascota","false"); f.put("conBicicleta","false"); f.put("asistencia","false");
        f.put("franjaHoraI",""); f.put("franjaHoraV","");
        f.put("Idioma","es"); f.put("Pais","ES");
        post(BASE + "/vol/buscarTren.do", f);

        String dwrCookie = getCookie(cm, BASE, "DWRSESSIONID");
        String sid = (dwrCookie == null || dwrCookie.isEmpty())
                ? "0123456789ABCDEF0123456789ABCDEF/renfecli"
                : dwrCookie + "/renfecli";

        String body =
            "callCount=1\nwindowName=\nc0-scriptName=trainEnlacesManager\n" +
            "c0-methodName=getTrainsList\nc0-id=0\n" +
            "c0-param0=Object_Object:{atendo:reference:c0-e1, sinEnlace:reference:c0-e2, plazaH:reference:c0-e3, tipoFranjaI:reference:c0-e4, tipoFranjaV:reference:c0-e5, horaFranjaIda:reference:c0-e6, horaFranjaVuelta:reference:c0-e7, fechaSalida:reference:c0-e8, fechaVuelta:reference:c0-e9, adultos:reference:c0-e10, ninos:reference:c0-e11, ninosMenores:reference:c0-e12, trayecto:reference:c0-e13, idaVuelta:reference:c0-e14, conMascota:reference:c0-e15, conBicicleta:reference:c0-e16}\n" +
            "c0-e1=string:false\nc0-e2=string:true\nc0-e3=string:false\nc0-e4=string:\nc0-e5=string:\nc0-e6=string:\nc0-e7=string:\n" +
            "c0-e8=string:" + esc(d) + "\nc0-e9=string:\nc0-e10=string:1\nc0-e11=string:0\nc0-e12=string:0\nc0-e13=string:I\nc0-e14=string:\nc0-e15=string:false\nc0-e16=string:false\n" +
            "batchId=0\ninstanceId=0\npage=" + esc(PAGE) + "\nscriptSessionId=" + sid + "\n";

        String r = postRaw(BASE + "/vol/dwr/call/plaincall/trainEnlacesManager.getTrainsList.dwr", body, "text/plain;charset=UTF-8");
        return parseJourneys(r, target, oc, dc, d);
    }

    static ArrayList<Journey> parseJourneys(String script, String target, String originCode, String destinationCode, String date) throws Exception {
        if (script.contains("r.handleException(")) {
            throw new IOException("Renfe ha rechazado la consulta DWR.");
        }
        String marker = "r.handleCallback(\"0\",\"0\",";
        int start = script.indexOf(marker);
        if (start < 0) throw new IOException("Renfe no devolvió una respuesta DWR válida.");
        start += marker.length();
        String payload = sliceObject(script, start);
        String json = jsObjectToJson(payload);
        JSONObject root = new JSONObject(json);

        ArrayList<Journey> out = new ArrayList<>();
        JSONArray listado = root.optJSONArray("listadoTrenes");
        if (listado == null) return out;

        for (int i=0;i<listado.length();i++) {
            JSONObject block = listado.optJSONObject(i);
            if (block == null || !block.optBoolean("viajeIda", true)) continue;
            JSONArray trains = block.optJSONArray("listviajeViewEnlaceBean");
            if (trains == null) continue;
            for (int j=0;j<trains.length();j++) {
                JSONObject t = trains.optJSONObject(j);
                if (t == null) continue;
                Journey x = new Journey();
                x.departure = t.optString("horaSalida","");
                x.arrival = t.optString("horaLlegada","");
                x.duration = t.optString("duracionViaje","");
                x.type = first(t.optString("tipoTrenUno",""), t.optString("tipoTrenDos",""));
                x.from = t.optString("descripcionEstacionOrigen",onSafe(block,"descripcionEstacionOrigen"));
                x.to = t.optString("descripcionEstacionDestino",onSafe(block,"descripcionEstacionDestino"));
                x.direct = t.optBoolean("directo",false);
                String realFrom = t.optString("codigoEstacionOrigen", "");
                String realTo = t.optString("codigoEstacionDestino", "");
                if (!originCode.equals(realFrom) || !destinationCode.equals(realTo)) continue;
                String expectedDate = toIsoDate(date);
                String journeyDate = t.optString("fecha", "");
                if (!expectedDate.isEmpty() && !expectedDate.equals(journeyDate)) continue;
                x.price = parsePrice(t.optString("tarifaMinima",""));
                JSONArray fares = t.optJSONArray("tarifasDisponibles");
                boolean hasNormalFare = false;
                boolean hasFare = false;
                if (fares != null) {
                    for (int k = 0; k < fares.length(); k++) {
                        JSONObject fare = fares.optJSONObject(k);
                        if (fare == null) continue;
                        hasFare = true;
                        double fp = parsePrice(fare.optString("precioTarifa",""));
                        if (fp > 0 && (x.price <= 0 || fp < x.price)) x.price = fp;
                        if (fp > 0 && !fare.optBoolean("soloPlazasH", false)) hasNormalFare = true;
                    }
                }
                String reason = t.optString("razonNoDisponible", "");
                boolean blockedReason = !reason.isEmpty() && !"8".equals(reason);
                // Replica la lógica de disponibilidad del frontend de Renfe:
                // completo -> no; sin tarifas -> no; código de bloqueo distinto
                // de 8 -> no; y si todas las tarifas restantes son reservadas
                // (soloPlazasH) -> no para un viajero normal.
                x.available = !t.optBoolean("completo", true)
                        && hasFare
                        && !blockedReason
                        && !t.optBoolean("soloPlazaH", false)
                        && hasNormalFare;
                JSONArray legs=t.optJSONArray("trayectos");
                if (legs != null && legs.length()>0) {
                    JSONObject leg=legs.optJSONObject(0);
                    if (leg != null) x.train=stripZeros(leg.optString("cdgoTren",""));
                }
                if (x.train == null || x.train.isEmpty()) x.train = t.optString("cdgoTren","");
                if (target == null || target.trim().isEmpty()) {
                    if (x.direct) out.add(x);
                } else if (x.departure.equals(target) && x.available && x.direct) {
                    out.add(x);
                }
            }
        }
        return out;
    }

    static boolean hasNormalFare(JSONObject t) {
        JSONArray fares = t.optJSONArray("tarifasDisponibles");
        if (fares == null || fares.length() == 0) return false;
        for (int i=0;i<fares.length();i++) {
            JSONObject f = fares.optJSONObject(i);
            if (f == null) continue;
            double p = parsePrice(f.optString("precioTarifa",""));
            boolean hOnly = f.optBoolean("soloPlazasH", false);
            if (p > 0 && !hOnly) return true;
        }
        return false;
    }

    static String toIsoDate(String d) {
        try {
            String[] p = d.split("/");
            if (p.length != 3) return "";
            return p[2] + "-" + p[1] + "-" + p[0];
        } catch (Exception e) {
            return "";
        }
    }

    static String onSafe(JSONObject o,String k){ return o.optString(k,""); }
    static String first(String a,String b){ return a.isEmpty()?b:a; }
    static String stripZeros(String s){ s=s.trim(); s=s.replaceFirst("^0+(?!$)",""); return s; }
    static double parsePrice(String s){
        if(s==null||s.trim().isEmpty()) return 0;
        try{return Double.parseDouble(s.replace(".","").replace(",", "."));}catch(Exception e){return 0;}
    }

    static String sliceObject(String s, int from) throws Exception {
        int depth=0; boolean str=false,esc=false;
        for(int i=from;i<s.length();i++){
            char c=s.charAt(i);
            if(str){
                if(esc) esc=false;
                else if(c=='\\') esc=true;
                else if(c=='\"') str=false;
            } else {
                if(c=='\"') str=true;
                else if(c=='{'||c=='[') depth++;
                else if(c=='}'||c==']'){
                    depth--; if(depth==0) return s.substring(from,i+1);
                }
            }
        }
        throw new IOException("Respuesta DWR truncada.");
    }

    static String jsObjectToJson(String s) throws Exception {
        StringBuilder b=new StringBuilder(s.length()+128);
        boolean str=false,esc=false,keyPos=false;
        ArrayDeque<Character> stack=new ArrayDeque<>();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(str){
                b.append(c);
                if(esc) esc=false;
                else if(c=='\\') esc=true;
                else if(c=='\"') str=false;
                continue;
            }
            if(c=='\"'){str=true;keyPos=false;b.append(c);}
            else if(c=='{'||c=='['){stack.push(c);keyPos=c=='{';b.append(c);}
            else if(c=='}'||c==']'){if(stack.isEmpty())throw new IOException("JSON DWR inválido");stack.pop();keyPos=false;b.append(c);}
            else if(c==','){keyPos=!stack.isEmpty()&&stack.peek()=='{';b.append(c);}
            else if(keyPos && (Character.isLetter(c)||c=='_'||c=='$')){
                int j=i+1; while(j<s.length() && (Character.isLetterOrDigit(s.charAt(j))||s.charAt(j)=='_'||s.charAt(j)=='$'))j++;
                b.append('"').append(s,i,j).append('"'); i=j-1; keyPos=false;
            } else {b.append(c); if(!Character.isWhitespace(c)) keyPos=false;}
        }
        return b.toString();
    }

    static String getCookie(CookieManager cm,String base,String name){
        try{
            URI u=URI.create(base);
            for(HttpCookie c:cm.getCookieStore().get(u)) if(name.equals(c.getName())) return c.getValue();
        }catch(Exception ignored){}
        return null;
    }

    static String esc(String s){
        try{return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+","%20");}
        catch(Exception e){return s;}
    }

    static String get(String u) throws Exception{return request("GET",u,null,null);}
    static String post(String u,Map<String,String> f)throws Exception{
        StringBuilder b=new StringBuilder();
        for(Map.Entry<String,String> e:f.entrySet()){
            if(b.length()>0)b.append('&');
            b.append(esc(e.getKey())).append('=').append(esc(e.getValue()));
        }
        return request("POST",u,b.toString(),"application/x-www-form-urlencoded");
    }
    static String postRaw(String u,String body,String type)throws Exception{return request("POST",u,body,type);}

    static String request(String method,String u,String body,String type)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setRequestMethod(method);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/154 Mobile Safari/537.36");
        c.setRequestProperty("Accept","*/*");
        c.setRequestProperty("Accept-Language","es-ES,es;q=0.9");
        c.setRequestProperty("Origin",BASE);
        c.setRequestProperty("Referer",u.contains("/dwr/") ? BASE+PAGE : BASE+"/vol/inicio.do");
        if(body!=null){
            c.setDoOutput(true); c.setRequestProperty("Content-Type",type);
            try(OutputStream os=c.getOutputStream()){os.write(body.getBytes(StandardCharsets.UTF_8));}
        }
        int code=c.getResponseCode();
        InputStream in=code>=400?c.getErrorStream():c.getInputStream();
        if(in==null)throw new IOException("HTTP "+code);
        Charset cs=StandardCharsets.UTF_8;
        String ct=c.getContentType();
        if(ct!=null && (ct.toLowerCase(Locale.ROOT).contains("iso-8859-1")
                || ct.toLowerCase(Locale.ROOT).contains("windows-1252"))) {
            cs=Charset.forName("windows-1252");
        }
        byte[] data = readBytes(in);
        String text = new String(data, cs);
        // Algunas respuestas del catálogo llegan en Windows-1252 aunque no
        // anuncien charset; UTF-8 las convierte en U+FFFD y hace desaparecer letras.
        if (text.indexOf('\uFFFD') >= 0 && !containsUtf8Replacement(data)) {
            String legacy = new String(data, Charset.forName("windows-1252"));
            if (legacy.indexOf('\uFFFD') < 0) text = legacy;
        }
        if(code>=400)throw new IOException("HTTP "+code+" "+text.substring(0,Math.min(300,text.length())));
        return text;
    }

    static boolean containsUtf8Replacement(byte[] data) {
        for (int i = 0; i + 2 < data.length; i++) {
            if ((data[i] & 0xff) == 0xef && (data[i + 1] & 0xff) == 0xbf
                    && (data[i + 2] & 0xff) == 0xbd) return true;
        }
        return false;
    }

    static byte[] readBytes(InputStream in) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    static String read(InputStream in,Charset cs)throws Exception{
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,cs))){
            StringBuilder b=new StringBuilder(); String l;
            while((l=r.readLine())!=null)b.append(l).append('\n');
            return b.toString();
        }
    }
}
