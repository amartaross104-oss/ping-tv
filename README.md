# Ping Internet TV

A small ping indicator for Google TV / Android TV. It draws `● 14 ms` right beside the clock on the home screen, with no background, and hides when another app is in front. Opening the app shows a live ping screen with a 60-second graph.

- Measures TCP connect time to `1.1.1.1:443` (median of 3). Works the same on Wi-Fi and LAN cable.
- Dot colour: green under 60 ms, yellow under 150 ms, orange above, red when offline.
- Starts at boot. No third-party libraries; plain Android SDK.

## Build

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). Set `ANDROID_HOME` and `JAVA_HOME`, then on Windows:

```powershell
./build.ps1
```

This produces `PingTV.apk`, signed with a local debug keystore that the script creates on first run (not committed).

## Install

```bash
adb connect <tv-ip>:5555
adb install -r PingTV.apk
adb shell appops set my.pingtv.tv SYSTEM_ALERT_WINDOW allow
adb shell appops set my.pingtv.tv GET_USAGE_STATS allow
adb shell am start -n my.pingtv.tv/.MainActivity
```

The two `appops` grants let the app draw over the home screen and detect when the launcher is in front. The overlay position is tuned for a 16:9 Google TV home screen (see `OverlayService.java`).
