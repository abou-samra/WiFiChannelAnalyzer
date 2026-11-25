# Wi-Fi Channel Analyzer (Android)

Android port of the Windows PowerShell tool. Scans nearby Wi-Fi networks and recommends the
least crowded 2.4 GHz and 5 GHz channel using the same scoring model as the original script.

## Build the APK

### Option A: Android Studio (easiest)
1. Install Android Studio and choose File > Open, then select this folder.
2. Wait for Gradle sync to finish.
3. Build > Build APK(s). Android Studio shows a "locate" link to `app/build/outputs/apk/debug/app-debug.apk`.

### Option B: GitHub (no local install)
1. Push this folder to a GitHub repository.
2. Go to Actions > "Build APK" > Run workflow.
3. Download `WiFiChannelAnalyzer-debug-apk` from the finished run.

## Install on a phone
Copy `app-debug.apk` to the phone, open it, and allow "Install unknown apps" when asked.

## Notes
- Android requires the Location permission AND the Location switch to be on to list Wi-Fi networks.
- Android limits apps to a few scans per couple of minutes; the app falls back to the latest cached scan.
- 6 GHz networks are ignored for now.
- For Google Play you need a signed release bundle (Build > Generate Signed Bundle / APK).
