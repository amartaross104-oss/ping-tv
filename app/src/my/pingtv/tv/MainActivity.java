package my.pingtv.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** Live ping screen. Opened from the home screen card or the app list. */
public class MainActivity extends Activity {
    private PingView view;
    private volatile boolean running;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new PingView(this);
        setContentView(view);
        hideBars();

        cleanUpOldCard();
        try {
            startForegroundService(new Intent(this, OverlayService.class));
        } catch (Exception e) {
            android.util.Log.w("PingTV", "start failed: " + e);
        }
    }

    /** Removes the big home screen card and its refresh job from the earlier version. */
    private void cleanUpOldCard() {
        try {
            SharedPreferences sp = getSharedPreferences("pt", 0);
            long ch = sp.getLong("ch", -1);
            if (ch != -1) {
                getContentResolver().delete(TvContract.buildChannelUri(ch), null, null);
                sp.edit().remove("ch").apply();
            }
            ((android.app.job.JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(43);
        } catch (Exception e) {
            android.util.Log.w("PingTV", "cleanup failed: " + e);
        }
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        running = true;
        new Thread(new Runnable() {
            public void run() {
                while (running) {
                    final int ms = Net.ping();
                    final String net = Net.type(MainActivity.this);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            view.push(ms, net);
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onPause() {
        running = false;
        super.onPause();
    }

    static class PingView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] hist = new int[60];
        private int n = 0;
        private int last = -2;
        private String net = "";

        PingView(Context c) {
            super(c);
        }

        void push(int ms, String netType) {
            if (n == hist.length) {
                System.arraycopy(hist, 1, hist, 0, hist.length - 1);
                n--;
            }
            hist[n++] = ms;
            last = ms;
            net = netType;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float u = Math.min(w / 100f, h / 56.25f);
            cv.drawColor(0xFF071316);

            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(2.4f * u);
            p.setColor(0xFF86A6A2);
            cv.drawText("PING INTERNET  ·  " + Net.HOST, 6 * u, 7 * u, p);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(net, w - 6 * u, 7 * u, p);
            p.setTextAlign(Paint.Align.LEFT);

            int col = Net.color(last);
            p.setColor(col);
            cv.drawCircle(7.2f * u, 31.8f * u, 1.3f * u, p);

            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setColor(0xFFEAF2F0);
            String big = last == -2 ? "…" : (last < 0 ? "Putus" : String.valueOf(last));
            p.setTextSize((last < 0 && last != -2 ? 10 : 15) * u);
            cv.drawText(big, 6 * u, 26 * u, p);
            if (last >= 0) {
                float bw = p.measureText(big);
                p.setTextSize(4.5f * u);
                p.setColor(0xFF86A6A2);
                cv.drawText(" ms", 6 * u + bw, 26 * u, p);
            }
            p.setTextSize(4 * u);
            p.setColor(col);
            cv.drawText(last == -2 ? "Mengukur" : Net.status(last), 10 * u, 33 * u, p);

            // graph of the last 60 seconds
            float gx = 6 * u, gw = w - 12 * u, gy = 38 * u, gh = 13 * u;
            p.setColor(0xFF1F4146);
            p.setStrokeWidth(0.15f * u);
            cv.drawLine(gx, gy + gh, gx + gw, gy + gh, p);
            int max = 100;
            for (int i = 0; i < n; i++) if (hist[i] > max) max = hist[i];
            if (max > 400) max = 400;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.35f * u);
            p.setColor(0xFF4FC7B2);
            Path path = new Path();
            boolean started = false;
            for (int i = 0; i < n; i++) {
                float x = gx + gw * i / (hist.length - 1f);
                if (hist[i] < 0) {
                    started = false;
                    continue;
                }
                float y = gy + gh - gh * Math.min(hist[i], max) / max;
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            cv.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE5534B);
            for (int i = 0; i < n; i++) {
                if (hist[i] < 0) cv.drawCircle(gx + gw * i / (hist.length - 1f), gy + gh, 0.5f * u, p);
            }
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(1.8f * u);
            p.setColor(0xFF56777A);
            cv.drawText("60 saat lalu", gx, gy + gh + 3 * u, p);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText("sekarang", gx + gw, gy + gh + 3 * u, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }
}
