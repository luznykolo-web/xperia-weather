package pl.luznykolo.sonyxperiaz2weather;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.TrustManagerFactory;
import org.conscrypt.Conscrypt;

public class MainActivity extends Activity {
    WeatherView view;
    Handler h = new Handler();

    final String URLS =
        "https://api.open-meteo.com/v1/forecast?latitude=51.1136&longitude=20.8716" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure,visibility" +
        "&hourly=temperature_2m,precipitation_probability,weather_code" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
        "&timezone=Europe%2FWarsaw&forecast_days=6";

    final Runnable ticker = new Runnable() {
        public void run() {
            if (view != null) view.invalidate();
            h.postDelayed(this, 1000);
        }
    };

    final Runnable refresh = new Runnable() {
        public void run() {
            fetch();
            h.postDelayed(this, 15 * 60 * 1000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (Throwable ignored) {}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hide();
        view = new WeatherView();
        setContentView(view);
        ticker.run();
        refresh.run();
    }

    void fetch() {
        view.status = "Łączenie…";
        view.invalidate();

        new Thread(new Runnable() {
            public void run() {
                try {
                    String j = get(URLS);
                    getPreferences(0).edit().putString("cache", j).apply();
                    show(j, true);
                } catch (final Exception e) {
                    final String c = getPreferences(0).getString("cache", null);
                    if (c != null) {
                        show(c, false);
                    } else {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                view.status = "Błąd połączenia: " + e.getClass().getSimpleName();
                                view.invalidate();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    // Networking / trust path retained from the working v9 baseline.
    String get(String u) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caIn = getResources().openRawResource(R.raw.isrgrootx1);
        Certificate ca;
        try { ca = cf.generateCertificate(caIn); }
        finally { caIn.close(); }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("isrg-root-x1", ca);

        TrustManagerFactory tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), null);

        HttpsURLConnection c =
            (HttpsURLConnection) new URL(u).openConnection();
        c.setSSLSocketFactory(sc.getSocketFactory());
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "SonyXperiaZ2Weather/1");

        InputStream in = c.getInputStream();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder b = new StringBuilder();
        String x;
        while ((x = r.readLine()) != null) b.append(x);
        r.close();
        return b.toString();
    }

    void show(final String raw, final boolean online) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    view.setData(new JSONObject(raw));
                    view.status = online
                        ? "Ostatnia aktualizacja: " +
                          new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date())
                        : "Offline — ostatnie dane";
                    view.invalidate();
                } catch (Exception e) {
                    view.status = "Błąd danych pogodowych";
                    view.invalidate();
                }
            }
        });
    }

    int rnd(double d) { return (int)Math.round(d); }

    String hm(String s) {
        return s != null && s.length() >= 16 ? s.substring(11, 16) : "--:--";
    }

    String weatherText(int c) {
        if (c == 0) return "Bezchmurnie";
        if (c <= 2) return "Częściowe zachmurzenie";
        if (c == 3) return "Pochmurno";
        if (c == 45 || c == 48) return "Mgła";
        if (c >= 51 && c <= 57) return "Mżawka";
        if (c >= 61 && c <= 67) return "Deszcz";
        if (c >= 71 && c <= 77) return "Śnieg";
        if (c >= 80 && c <= 82) return "Przelotny deszcz";
        if (c >= 95) return "Burza";
        return "Pogoda";
    }

    String windDir(double deg) {
        String[] a = {"N","NE","E","SE","S","SW","W","NW"};
        int i = (int)Math.round((((deg % 360) + 360) % 360) / 45.0) % 8;
        return a[i];
    }

    class WeatherView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        JSONObject data;
        String status = "Łączenie…";
        Rect src = new Rect();

        Bitmap bgDaySunny, bgDayCloudy, bgDayRain, bgDayStorm, bgDaySnow, bgDayFog;
        Bitmap bgNightClear, bgNightCloudy, bgNightRain, bgNightStorm, bgNightSnow, bgNightFog;

        Bitmap sun, partly, cloud, rain, moon, moonCloud, snow, storm, fog;
        Bitmap pin, thermo, drop, windIcon, pressureIcon, eye;

        WeatherView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            bgDaySunny = load(R.drawable.bg_day_sunny);
            bgDayCloudy = load(R.drawable.bg_day_cloudy);
            bgDayRain = load(R.drawable.bg_day_rain);
            bgDayStorm = load(R.drawable.bg_day_storm);
            bgDaySnow = load(R.drawable.bg_day_snow);
            bgDayFog = load(R.drawable.bg_day_fog);
            bgNightClear = load(R.drawable.bg_night_clear);
            bgNightCloudy = load(R.drawable.bg_night_cloudy);
            bgNightRain = load(R.drawable.bg_night_rain);
            bgNightStorm = load(R.drawable.bg_night_storm);
            bgNightSnow = load(R.drawable.bg_night_snow);
            bgNightFog = load(R.drawable.bg_night_fog);

            sun = load(R.drawable.ic3d_sun);
            partly = load(R.drawable.ic3d_partly);
            cloud = load(R.drawable.ic3d_cloud);
            rain = load(R.drawable.ic3d_rain);
            moon = load(R.drawable.ic3d_moon);
            moonCloud = load(R.drawable.ic3d_moon_cloud);
            snow = load(R.drawable.ic3d_snow);
            storm = load(R.drawable.ic3d_storm);
            fog = load(R.drawable.ic3d_fog);

            pin = load(R.drawable.ui_pin);
            thermo = load(R.drawable.ui_thermo);
            drop = load(R.drawable.ui_drop);
            windIcon = load(R.drawable.ui_wind);
            pressureIcon = load(R.drawable.ui_pressure);
            eye = load(R.drawable.ui_eye);
        }

        Bitmap load(int id) {
            return BitmapFactory.decodeResource(getResources(), id);
        }

        void setData(JSONObject j) { data = j; }

        void setText(float size, boolean bold, int color) {
            p.setTextSize(size);
            p.setColor(color);
            p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.clearShadowLayer();
        }

        void txt(Canvas c, String s, float x, float y, float size, boolean bold) {
            setText(size, bold, Color.WHITE);
            p.setShadowLayer(2.5f, 0, 1, 0x99000000);
            c.drawText(s, x, y, p);
            p.clearShadowLayer();
        }

        void txtColor(Canvas c, String s, float x, float y, float size, boolean bold, int color) {
            setText(size, bold, color);
            p.setShadowLayer(2f, 0, 1, 0x99000000);
            c.drawText(s, x, y, p);
            p.clearShadowLayer();
        }

        void center(Canvas c, String s, float x, float y, float size, boolean bold, int color) {
            setText(size, bold, color);
            p.setTextAlign(Paint.Align.CENTER);
            p.setShadowLayer(2f, 0, 1, 0x99000000);
            c.drawText(s, x, y, p);
            p.clearShadowLayer();
        }

        void right(Canvas c, String s, float x, float y, float size, boolean bold, int color) {
            setText(size, bold, color);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setShadowLayer(2f, 0, 1, 0x99000000);
            c.drawText(s, x, y, p);
            p.clearShadowLayer();
        }

        void glass(Canvas c, float l, float t, float r, float b, float radius) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(225, 2, 33, 60));
            c.drawRoundRect(l, t, r, b, radius, radius, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            p.setColor(Color.rgb(34, 182, 238));
            c.drawRoundRect(l, t, r, b, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }

        void line(Canvas c, float x1, float y1, float x2, float y2) {
            p.setColor(Color.argb(190, 70, 210, 255));
            p.setStrokeWidth(1f);
            c.drawLine(x1, y1, x2, y2, p);
        }

        void bitmap(Canvas c, Bitmap b, int l, int t, int r, int bot) {
            if (b == null) return;
            src.set(0, 0, b.getWidth(), b.getHeight());
            c.drawBitmap(b, src, new Rect(l, t, r, bot), p);
        }

        void drawBackgroundCover(Canvas c, Bitmap b) {
            if (b == null) return;
            float sw = b.getWidth();
            float sh = b.getHeight();
            float dw = getWidth();
            float dh = getHeight();
            float scale = Math.max(dw / sw, dh / sh);
            float cropW = dw / scale;
            float cropH = dh / scale;
            float left = (sw - cropW) / 2f;
            float top = (sh - cropH) / 2f;
            Rect srcBg = new Rect(
                Math.max(0, Math.round(left)),
                Math.max(0, Math.round(top)),
                Math.min(b.getWidth(), Math.round(left + cropW)),
                Math.min(b.getHeight(), Math.round(top + cropH))
            );
            Rect dstBg = new Rect(0, 0, getWidth(), getHeight());
            c.drawBitmap(b, srcBg, dstBg, p);
        }

        boolean isNight(JSONObject daily) {
            try {
                String sr = daily.getJSONArray("sunrise").getString(0);
                String ss = daily.getJSONArray("sunset").getString(0);
                String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(new Date());
                return now.compareTo(sr) < 0 || now.compareTo(ss) > 0;
            } catch (Exception e) {
                Calendar cal = Calendar.getInstance();
                int h = cal.get(Calendar.HOUR_OF_DAY);
                return h < 6 || h >= 19;
            }
        }

        boolean nightForTime(JSONObject daily, String iso) {
            try {
                String date = iso.substring(0,10);
                JSONArray dates = daily.getJSONArray("time");
                JSONArray sr = daily.getJSONArray("sunrise");
                JSONArray ss = daily.getJSONArray("sunset");
                for (int i=0;i<dates.length();i++) {
                    if (date.equals(dates.getString(i))) {
                        return iso.compareTo(sr.getString(i)) < 0 || iso.compareTo(ss.getString(i)) > 0;
                    }
                }
            } catch (Exception ignored) {}
            try {
                int hh = Integer.parseInt(iso.substring(11,13));
                return hh < 6 || hh >= 19;
            } catch (Exception e) {
                return false;
            }
        }

        Bitmap weatherIcon(int code, boolean night) {
            if (code == 0) return night ? moon : sun;
            if (code <= 2) return night ? moonCloud : partly;
            if (code == 3) return night ? moonCloud : cloud;
            if (code == 45 || code == 48) return fog;
            if (code >= 51 && code <= 67) return rain;
            if (code >= 71 && code <= 77) return snow;
            if (code >= 80 && code <= 82) return rain;
            if (code >= 95) return storm;
            return cloud;
        }

        Bitmap weatherBackground(int code, boolean night) {
            if (night) {
                if (code == 0) return bgNightClear;
                if (code <= 3) return bgNightCloudy;
                if (code == 45 || code == 48) return bgNightFog;
                if (code >= 71 && code <= 77) return bgNightSnow;
                if (code >= 95) return bgNightStorm;
                if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return bgNightRain;
                return bgNightCloudy;
            } else {
                if (code == 0) return bgDaySunny;
                if (code <= 3) return bgDayCloudy;
                if (code == 45 || code == 48) return bgDayFog;
                if (code >= 71 && code <= 77) return bgDaySnow;
                if (code >= 95) return bgDayStorm;
                if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return bgDayRain;
                return bgDayCloudy;
            }
        }

        @Override protected void onDraw(Canvas raw) {
            super.onDraw(raw);

            JSONObject current = null, daily = null, hourly = null;
            int code = 3;
            boolean night = false;
            try {
                if (data != null) {
                    current = data.getJSONObject("current");
                    daily = data.getJSONObject("daily");
                    hourly = data.getJSONObject("hourly");
                    code = current.getInt("weather_code");
                    night = isNight(daily);
                }
            } catch (Exception ignored) {}

            Bitmap bg = weatherBackground(code, night);
            if (bg != null) drawBackgroundCover(raw, bg);
            else raw.drawColor(Color.rgb(5, 35, 62));

            // Xperia Z2 Tablet is 1920x1200 (16:10). Keep the approved 1024x600
            // composition at one uniform scale so NOTHING is stretched. The small
            // extra vertical area shows only the matching photographic background.
            float scale = getWidth() / 1024f;
            float contentHeight = 600f * scale;
            float offsetY = (getHeight() - contentHeight) / 2f;
            if (offsetY < 0f) {
                scale = getHeight() / 600f;
                float contentWidth = 1024f * scale;
                raw.save();
                raw.translate((getWidth() - contentWidth) / 2f, 0f);
            } else {
                raw.save();
                raw.translate(0f, offsetY);
            }
            raw.scale(scale, scale);
            Canvas c = raw;

            // Exact approved composition: large clock left, 3D condition icon center,
            // large temperature, details right, two glass forecast panels below.
            glass(c, 28, 73, 332, 220, 16);
            glass(c, 515, 82, 735, 220, 16);
            glass(c, 748, 28, 995, 288, 16);
            glass(c, 25, 321, 510, 523, 16);
            glass(c, 522, 321, 995, 523, 16);

            Date now = new Date();
            txt(c, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 45, 170, 100, true);
            txt(c, new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("pl","PL")).format(now),
                45, 205, 15, true);

            txt(c, "SKARŻYSKO-KAMIENNA", 795, 59, 17, true);
            txt(c, "51.1136° N, 20.8716° E", 795, 82, 12, false);
            bitmap(c, pin, 758, 40, 790, 76);

            if (current != null && daily != null && hourly != null) {
                try {
                    bitmap(c, weatherIcon(code, night), 350, 76, 510, 236);

                    String temp = rnd(current.getDouble("temperature_2m")) + "°C";
                    center(c, temp, 625, 166, 60, true, Color.WHITE);
                    center(c, weatherText(code), 625, 199, 17, true, Color.WHITE);

                    String[] labels = {"Odczuwalna", "Wilgotność", "Wiatr", "Ciśnienie", "Widoczność"};
                    String windTxt = rnd(current.getDouble("wind_speed_10m")) + " km/h";
                    if (current.has("wind_direction_10m")) {
                        windTxt += "  " + windDir(current.getDouble("wind_direction_10m"));
                    }
                    String[] vals = {
                        rnd(current.getDouble("apparent_temperature")) + "°C",
                        rnd(current.getDouble("relative_humidity_2m")) + "%",
                        windTxt,
                        rnd(current.optDouble("surface_pressure", 0)) + " hPa",
                        String.format(Locale.US, "%.0f km", current.optDouble("visibility", 0) / 1000.0)
                    };
                    Bitmap[] mi = {thermo, drop, windIcon, pressureIcon, eye};

                    for (int i=0;i<5;i++) {
                        int y = 102 + i * 36;
                        bitmap(c, mi[i], 760, y - 15, 787, y + 12);
                        txt(c, labels[i], 796, y + 6, 14, false);
                        right(c, vals[i], 976, y + 6, i==2 ? 14 : 16, true, Color.WHITE);
                    }

                    txt(c, "Prognoza godzinowa", 44, 350, 19, true);
                    line(c, 44, 362, 492, 362);
                    txt(c, "Prognoza na 5 dni", 541, 350, 19, true);
                    line(c, 541, 362, 977, 362);

                    JSONArray ht = hourly.getJSONArray("time");
                    JSONArray hT = hourly.getJSONArray("temperature_2m");
                    JSONArray hc = hourly.getJSONArray("weather_code");
                    JSONArray pr = hourly.getJSONArray("precipitation_probability");

                    String currentHour = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(now);
                    int start = 0;
                    for (int i=0;i<ht.length();i++) {
                        if (ht.getString(i).compareTo(currentHour) >= 0) { start = i; break; }
                    }

                    for (int k=0;k<6 && start+k<ht.length();k++) {
                        int z = start + k;
                        float x = 68 + k * 80;
                        if (k>0) line(c, x-40, 374, x-40, 507);

                        String iso = ht.getString(z);
                        center(c, hm(iso), x, 389, 13, false, Color.WHITE);
                        bitmap(c, weatherIcon(hc.getInt(z), nightForTime(daily, iso)),
                               (int)x-30, 395, (int)x+30, 455);
                        center(c, rnd(hT.getDouble(z)) + "°", x, 481, 18, true, Color.WHITE);
                        txtColor(c, "●", x-24, 505, 12, false, Color.rgb(42, 207, 255));
                        center(c, pr.optInt(z,0) + "%", x+7, 505, 12, true, Color.rgb(42, 207, 255));
                    }

                    JSONArray dt = daily.getJSONArray("time");
                    JSONArray mx = daily.getJSONArray("temperature_2m_max");
                    JSONArray mn = daily.getJSONArray("temperature_2m_min");
                    JSONArray dc = daily.getJSONArray("weather_code");
                    JSONArray pp = daily.getJSONArray("precipitation_probability_max");

                    SimpleDateFormat inf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", new Locale("pl","PL"));
                    SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM", Locale.US);

                    for (int k=1;k<=5 && k<dt.length();k++) {
                        float x = 565 + (k-1) * 91;
                        if (k>1) line(c, x-45, 374, x-45, 507);

                        Date day = inf.parse(dt.getString(k));
                        center(c, dayFmt.format(day), x, 387, 14, true, Color.WHITE);
                        center(c, dateFmt.format(day), x, 406, 11, false, Color.WHITE);
                        bitmap(c, weatherIcon(dc.getInt(k), false),
                               (int)x-29, 411, (int)x+29, 467);
                        center(c, rnd(mx.getDouble(k)) + "°", x, 486, 18, true, Color.WHITE);
                        center(c, rnd(mn.getDouble(k)) + "°", x, 504, 13, false, Color.rgb(112, 201, 255));
                        txtColor(c, "●", x-22, 520, 11, false, Color.rgb(42, 207, 255));
                        center(c, pp.optInt(k,0) + "%", x+7, 520, 11, true, Color.rgb(42, 207, 255));
                    }
                } catch (Exception ignored) {}
            } else {
                bitmap(c, weatherIcon(code, night), 350, 76, 510, 236);
                center(c, "--°C", 625, 166, 60, true, Color.WHITE);
                center(c, "Pobieranie pogody…", 625, 199, 16, true, Color.WHITE);
                txt(c, "Prognoza godzinowa", 44, 350, 19, true);
                line(c, 44, 362, 492, 362);
                txt(c, "Prognoza na 5 dni", 541, 350, 19, true);
                line(c, 541, 362, 977, 362);
            }

            p.setColor(Color.argb(145, 0, 0, 0));
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0, 552, 1024, 600, p);
            txt(c, "Stacja Pogodowa", 24, 580, 14, false);
            right(c, status, 988, 580, 11, false, Color.WHITE);

            raw.restore();
        }
    }

    void hide() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) hide();
    }

    @Override public void onBackPressed() {}
}
