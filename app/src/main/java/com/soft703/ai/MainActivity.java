package com.soft703.ai;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView ramText, storageText, wifiText, statusText, focusText;
    private SharedPreferences prefs;
    private final Set<String> selectedPackages = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean internetTestRunning = false;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                refreshStats();
                handler.postDelayed(this, 5000L);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("soft703_settings", MODE_PRIVATE);
        selectedPackages.addAll(prefs.getStringSet("priority_packages", Collections.<String>emptySet()));
        buildUi();
        refreshStats();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(0xFFF3F6FA);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("SOFT703 AI");
        title.setTextSize(26);
        title.setTextColor(0xFF12243A);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Device care • Focus profile • Memory • Wi-Fi diagnostics");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        statusText = card("Status: starting checks…");
        ramText = card("Memory status");
        storageText = card("Storage status");
        wifiText = card("Wi-Fi status");
        focusText = card("Focus profile");

        button("Refresh all system checks", this::refreshStats);
        button("Extended Memory — inspect RAM and swap", this::swapInfo);
        button("Focus Mode — choose and save priority apps", this::chooseApps);
        button("Open Android app / battery settings", this::openAppSettings);
        button("Smart Cleaner — clear Soft703 AI cache", this::cleanOwnCache);
        button("Wi-Fi Booster — diagnose and test internet", this::wifiDiagnosis);
        button("Open Wi-Fi settings", () -> launchSettings(Settings.ACTION_WIFI_SETTINGS));
        button("Repeater / hotspot — open sharing controls", this::openSharingSettings);
        button("Run internet connectivity test", this::testInternet);

        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setTextColor(0xFF4B5C70);
        note.setPadding(0, dp(10), 0, dp(8));
        note.setText("Accuracy note: Soft703 AI checks real Android-reported status and tests internet reachability. An ordinary app cannot guarantee extra physical RAM, force-stop other apps, boost Wi-Fi radio power, or turn a phone into a simultaneous Wi-Fi repeater unless the device supports it. The app will report limits honestly rather than claim an unsupported function worked.");
        root.addView(note);
    }

    private TextView card(String heading) {
        TextView v = new TextView(this);
        v.setText(heading);
        v.setTextSize(15);
        v.setTextColor(0xFF12243A);
        v.setPadding(dp(13), dp(13), dp(13), dp(13));
        v.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(9));
        root.addView(v, p);
        return v;
    }

    private void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            try { action.run(); }
            catch (Exception e) { showMessage("Function error", safeMessage(e)); }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(6));
        root.addView(b, p);
    }

    private void refreshStats() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) throw new IllegalStateException("Android memory service is unavailable.");
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ramText.setText("MEMORY / EXTENDED MEMORY\nAvailable RAM: " + mb(mi.availMem) +
                    " MB\nTotal RAM: " + mb(mi.totalMem) + " MB\nLow-memory warning: " +
                    (mi.lowMemory ? "Yes" : "No") + "\nSwap reported: " + readSwap() +
                    "\nPriority apps saved: " + selectedPackages.size() +
                    "\nVirtual memory status is reported only; this app cannot create physical RAM.");
        } catch (Exception e) {
            ramText.setText("MEMORY\nCould not read memory: " + safeMessage(e));
        }

        try {
            StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
            storageText.setText("STORAGE\nFree app-filesystem space: " +
                    (fs.getAvailableBytes() / (1024L * 1024L)) + " MB\nTotal filesystem: " +
                    (fs.getTotalBytes() / (1024L * 1024L)) +
                    " MB\nCleaner safely targets only Soft703 AI's own cache.");
        } catch (Exception e) {
            storageText.setText("STORAGE\nCould not read storage: " + safeMessage(e));
        }
        wifiText.setText(readWifi());
        focusText.setText("FOCUS PROFILE\nSaved priority apps: " + selectedPackages.size() +
                "\nSelection is retained after closing the app. Android may not allow this app to freeze other apps.");
        statusText.setText("Status: system checks complete • auto-refresh every 5 seconds while open");
    }

    private String readWifi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null) return "WI-FI\nAndroid Wi-Fi service unavailable.";
            if (!wm.isWifiEnabled()) return "WI-FI\nWi-Fi is OFF. Turn it on in Android settings.";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return "WI-FI\nConnection details unavailable.";
            int rssi = info.getRssi();
            String signal = rssi == 0 || rssi <= -100 ? "Unknown" :
                    rssi >= -50 ? "Excellent" : rssi >= -60 ? "Very good" :
                    rssi >= -67 ? "Good" : rssi >= -75 ? "Fair" : "Weak";
            String ssid = info.getSSID();
            if (ssid == null || ssid.length() == 0) ssid = "Unavailable (Android privacy restriction)";
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            boolean connected = ni != null && ni.isConnected();
            return "WI-FI CONNECTION\nNetwork: " + ssid + "\nSignal: " + rssi +
                    " dBm (" + signal + ")\nReported link speed: " + info.getLinkSpeed() +
                    " Mbps\nNetwork connected: " + (connected ? "Yes" : "No") +
                    "\nLink speed is not the same as measured internet speed.";
        } catch (SecurityException e) {
            return "WI-FI\nAndroid restricted connection details. Use Wi-Fi settings to inspect the network.";
        } catch (Exception e) {
            return "WI-FI\nCould not read connection: " + safeMessage(e);
        }
    }

    private void chooseApps() {
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<String> packages = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (getPackageManager().getLaunchIntentForPackage(app.packageName) != null) {
                String label;
                try { label = getPackageManager().getApplicationLabel(app).toString(); }
                catch (Exception e) { label = app.packageName; }
                names.add(label);
                packages.add(app.packageName);
            }
        }
        if (names.isEmpty()) {
            showMessage("Focus Mode", "Android did not provide any launchable apps.");
            return;
        }
        boolean[] checked = new boolean[names.size()];
        for (int i = 0; i < packages.size(); i++) checked[i] = selectedPackages.contains(packages.get(i));
        new AlertDialog.Builder(this).setTitle("Choose priority apps")
            .setMultiChoiceItems(names.toArray(new String[0]), checked, (dialog, which, isChecked) -> {
                if (isChecked) selectedPackages.add(packages.get(which));
                else selectedPackages.remove(packages.get(which));
            })
            .setPositiveButton("Save selection", (d, w) -> {
                prefs.edit().putStringSet("priority_packages", new HashSet<>(selectedPackages)).apply();
                refreshStats();
                showMessage("Focus profile saved", selectedPackages.size() +
                        " priority apps saved. For stronger background restrictions, open Android battery/app settings; this app cannot silently freeze other apps.");
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void cleanOwnCache() {
        long before = folderSize(getCacheDir());
        boolean success = deleteChildren(getCacheDir());
        long after = folderSize(getCacheDir());
        showMessage("Smart Cleaner", (success ? "Cache cleanup completed." :
                "Some cache files could not be removed.") + "\nBefore: " + mb(before) +
                " MB\nAfter: " + mb(after) +
                " MB\nOnly Soft703 AI temporary cache was targeted; other apps and personal files were not touched.");
        refreshStats();
    }

    private void wifiDiagnosis() {
        String advice = "Wi-Fi signal tips:\n• Move closer to the router and reduce walls between devices.\n• Keep the router elevated and away from metal/electrical interference.\n• If internet remains slow with a strong signal, the router or internet provider may be the cause.\n\nA true repeater must receive and retransmit Wi-Fi. Android 8.1 phones generally cannot enable simultaneous repeating through an ordinary app.";
        new AlertDialog.Builder(this).setTitle("Wi-Fi Booster diagnostics")
            .setMessage(readWifi() + "\n\n" + advice)
            .setPositiveButton("Test internet", (d, w) -> testInternet())
            .setNeutralButton("Wi-Fi settings", (d, w) -> launchSettings(Settings.ACTION_WIFI_SETTINGS))
            .setNegativeButton("Close", null).show();
    }

    private void testInternet() {
        if (internetTestRunning) {
            showMessage("Internet test", "A connectivity test is already running.");
            return;
        }
        internetTestRunning = true;
        statusText.setText("Status: testing internet reachability…");
        new Thread(() -> {
            long start = System.currentTimeMillis();
            HttpURLConnection connection = null;
            String result;
            try {
                URL url = new URL("https://connectivitycheck.gstatic.com/generate_204");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                long elapsed = System.currentTimeMillis() - start;
                result = (code == 204 || (code >= 200 && code < 400))
                        ? "Internet endpoint reachable. Response: " + code + "\nRound-trip request time: " + elapsed + " ms."
                        : "Endpoint returned HTTP " + code + ". Internet access may be restricted.";
            } catch (Exception e) {
                result = "Could not reach the test endpoint: " +
                        (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) +
                        "\nCheck Wi-Fi sign-in, router internet access, or try again.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String message = result;
            handler.post(() -> {
                internetTestRunning = false;
                statusText.setText("Status: internet test finished");
                showMessage("Internet connectivity test", message +
                        "\n\nThis tests reachability, not a full download/upload speed test.");
            });
        }).start();
    }

    private void openSharingSettings() {
        if (!launchSettings("android.settings.TETHER_SETTINGS") &&
            !launchSettings("android.settings.WIRELESS_SETTINGS") &&
            !launchSettings(Settings.ACTION_WIFI_SETTINGS)) {
            showMessage("Repeater / hotspot", "Android did not expose a sharing-settings screen. Open Settings manually and look for Hotspot or Tethering.");
        }
    }

    private boolean launchSettings(String action) {
        try {
            Intent intent = new Intent(action);
            if (intent.resolveActivity(getPackageManager()) == null) return false;
            startActivity(intent);
            return true;
        } catch (Exception e) { return false; }
    }

    private void openAppSettings() {
        if (!launchSettings(Settings.ACTION_APPLICATION_SETTINGS)) {
            showMessage("App settings", "Android could not open app settings.");
        }
    }

    private void swapInfo() {
        showMessage("Extended Memory / swap", "Android-reported swap: " + readSwap() +
                "\nThis value is a read-only status check. Creating usable swap requires device/kernel support and privileged access. Soft703 AI cannot safely create extra physical RAM on this phone.");
    }

    private String readSwap() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) return line.substring("SwapTotal:".length()).trim();
            }
        } catch (Exception ignored) { }
        return "unavailable / access restricted";
    }

    private long folderSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long size = 0;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) size += folderSize(child);
        return size;
    }

    private boolean deleteChildren(File dir) {
        boolean success = true;
        File[] children = dir.listFiles();
        if (children == null) return true;
        for (File f : children) {
            if (f.isDirectory() && !deleteChildren(f)) success = false;
            try { if (f.exists() && !f.delete()) success = false; }
            catch (Exception ignored) { success = false; }
        }
        return success;
    }

    private void showMessage(String title, String message) {
        if (!isFinishing()) new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private long mb(long bytes) { return bytes / (1024L * 1024L); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}