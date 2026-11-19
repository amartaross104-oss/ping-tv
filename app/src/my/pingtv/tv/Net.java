package my.pingtv.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

/** Ping measurement and status labels shared by the app screen and the home screen card. */
public class Net {
    static final String HOST = "1.1.1.1";
    static final int PORT = 443;

    /** Median TCP connect time in ms over 3 tries, or -1 when the internet is unreachable. */
    public static int ping() {
        long[] r = new long[3];
        int ok = 0;
        for (int i = 0; i < 3; i++) {
            Socket s = new Socket();
            try {
                long t0 = System.nanoTime();
                s.connect(new InetSocketAddress(HOST, PORT), 2000);
                r[ok++] = (System.nanoTime() - t0) / 1000000L;
            } catch (Exception e) {
                // counted as a failure
            } finally {
                try { s.close(); } catch (Exception e) { }
            }
        }
        if (ok == 0) return -1;
        long[] g = Arrays.copyOf(r, ok);
        Arrays.sort(g);
        return (int) g[ok / 2];
    }

    public static String type(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            if (n == null) return "Tiada sambungan";
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null) return "Tiada sambungan";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Kabel LAN";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            return "Rangkaian lain";
        } catch (Exception e) {
            return "Tidak diketahui";
        }
    }

    public static String status(int ms) {
        if (ms < 0) return "Putus";
        if (ms < 60) return "Baik";
        if (ms < 150) return "Sederhana";
        return "Perlahan";
    }

    public static int color(int ms) {
        if (ms < 0) return 0xFFE5534B;
        if (ms < 60) return 0xFF4FC7B2;
        if (ms < 150) return 0xFFE8BB6B;
        return 0xFFF08A4B;
    }
}
