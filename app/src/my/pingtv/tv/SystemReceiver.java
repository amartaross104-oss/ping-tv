package my.pingtv.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the ping pill when the TV boots. */
public class SystemReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            c.startForegroundService(new Intent(c, OverlayService.class));
        } catch (Exception e) {
            android.util.Log.w("PingTV", "start failed: " + e);
        }
    }
}
