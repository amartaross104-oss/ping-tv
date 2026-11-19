package my.pingtv.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** Draws a small ping pill beside the clock, only while the Google TV home screen is in front. */
public class OverlayService extends Service {
    static final String HOME = "com.google.android.apps.tv.launcherx";

    private WindowManager wm;
    private PillView pill;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean run = true;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("ping", "Ping Internet", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "ping")
                .setContentTitle("Ping Internet")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, n);
        }

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds();
        int W = b.width(), H = b.height();
        int pw = (int) (0.095f * W), ph = (int) (0.055f * H);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                pw, ph,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (0.768f * W) - pw;
        lp.y = (int) (0.100f * H) - ph / 2;
        pill = new PillView(this);
        pill.setVisibility(View.GONE);
        try {
            wm.addView(pill, lp);
        } catch (Exception e) {
            android.util.Log.e("PingTV", "overlay not allowed: " + e);
            stopSelf();
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }).start();
    }

    private void loop() {
        UsageStatsManager um = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        String top = "";
        long since = System.currentTimeMillis() - 10 * 60 * 1000L;
        long lastPing = 0;
        while (run) {
            long now = System.currentTimeMillis();
            try {
                UsageEvents ev = um.queryEvents(since, now);
                UsageEvents.Event e = new UsageEvents.Event();
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e);
                    if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) top = e.getPackageName();
                }
                since = now;
            } catch (Exception ex) {
                // keep the last known package
            }
            final boolean show = HOME.equals(top);
            ui.post(new Runnable() {
                public void run() {
                    pill.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
            if (show && now - lastPing > 5000) {
                lastPing = now;
                final int ms = Net.ping();
                ui.post(new Runnable() {
                    public void run() {
                        pill.set(ms);
                    }
                });
            }
            try {
                Thread.sleep(show ? 1000 : 2000);
            } catch (InterruptedException ie) {
                return;
            }
        }
    }

    @Override
    public void onDestroy() {
        run = false;
        try {
            wm.removeView(pill);
        } catch (Exception e) {
            // already removed
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    static class PillView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int ms = -2;

        PillView(Context c) {
            super(c);
        }

        void set(int v) {
            ms = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            // no background: the text sits directly on the home screen, tinted like the clock beside it
            int c = ms == -2 ? 0xFF86A6A2 : Net.color(ms);
            p.setColor((c & 0x00FFFFFF) | 0xB8000000);
            cv.drawCircle(h * 0.3f, h / 2, h * 0.11f, p);
            p.setColor(0xB3C9CDD1);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(h * 0.56f);
            String t = ms == -2 ? "…" : (ms < 0 ? "Putus" : ms + " ms");
            p.setTextAlign(Paint.Align.LEFT);
            float tx = h * 0.58f;
            float ty = h / 2 - (p.descent() + p.ascent()) / 2;
            cv.drawText(t, tx, ty, p);
        }
    }
}
