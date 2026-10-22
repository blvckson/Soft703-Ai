package com.soft703.ai;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView ramText, storageText, wifiText, statusText;
    private SharedPreferences prefs;
    private final ArrayList<ApplicationInfo> launchableApps = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                refreshStats();
                handler.postDelayed(this, 10000L);
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
        handler.postDelayed(refreshTask, 10000L);
        if (ramText != null) refreshStats();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(0xFFF4F7FB);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("SOFT703 AI");
        title.setTextSize(25);
        title.setTextColor(0xFF12243A);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("System Monitor • Focus Profile • Cleaner • Wi-Fi");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(10));
        root.addView(subtitle);

        statusText = card("Status: starting system checks…");
        ramText = card("RAM status");
        storageText = card("Storage status");
        wifiText = card("Wi-Fi status");

        button("Refresh system status", this::refreshStats);
        button("Focus Mode — choose priority apps", this::chooseApps);
        button("Open Android app settings", this::openAppSettings);
        button("Clean Soft703 AI temporary cache", this::cleanOwnCache);
        button("Wi-Fi Booster — diagnose connection", this::wifiDiagnosis);
        button("Try Wi-Fi / hotspot sharing settings", this::openSharingSettings);
        button("Check virtual RAM / swap support", this::swapInfo);

        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setTextColor(0xFF4B5C70);
        note.setPadding(0, dp(12), 0, dp(8));
        note.setText("Reliability note: Soft703 AI can monitor information Android exposes and save your focus-app choices. Android does not let an ordinary app create physical RAM, force-stop other apps, or guarantee Wi-Fi amplification. Wi-Fi repeating depends on device hardware and Android support; this app will not claim a booster is working unless it can verify that.");
        root.addView(note);
    }

    private TextView card(String heading) {
        TextView v = new TextView(this);
        v.setText(heading);
        v.setTextSize(16);
        v.setTextColor(0xFF12243A);
        v.setPadding(dp(14), dp(14), dp(14), dp(14));
        v.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(10));
        root.addView(v, p);
        return v;
    }

    private void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            try { action.run(); }
            catch (Exception e) { showMessage("Action unavailable", safeMessage(e)); }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(7));
        root.addView(b, p);
    }

    private void refreshStats() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) throw new IllegalStateException("Android memory service is unavailable.");
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            String ram = "RAM / EXTENDED MEMORY\nAvailable now: " + mb(mi.availMem) +
                    " MB\nTotal RAM reported by Android: " + mb(mi.totalMem) +
                    " MB\nLow-memory warning: " + (mi.lowMemory ? "Yes" : "No") +
                    "\nPriority apps saved: " + selectedPackages.size() +
                    "\nAndroid controls physical RAM. This app does not falsely count storage as RAM.";
            ramText.setText(ram);
        } catch (Exception e) {
            ramText.setText("RAM status\nCould not read memory information: " + safeMessage(e));
        }

        try {
            StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
            long free = fs.getAvailableBytes() / (1024L * 1024L);
            long total = fs.getTotalBytes() / (1024L * 1024L);
            storageText.setText("STORAGE\nAvailable on app filesystem: " + free +
                    " MB\nTotal app filesystem: " + total +
                    " MB\nOnly Soft703 AI's own cache can be cleaned safely here.");
        } catch (Exception e) {
            storageText.setText("Storage status\nCould not read storage information: " + safeMessage(e));
        }

        wifiText.setText(readWifi());
        if (statusText != null) statusText.setText("Status: checks completed • updates automatically every 10 seconds while open");
    }

    private String readWifi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null) return "WI-FI\nAndroid Wi-Fi service is unavailable.";
            if (!wm.isWifiEnabled()) return "WI-FI\nWi-Fi is OFF. Turn it on in Android settings.";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return "WI-FI\nWi-Fi is enabled, but Android did not provide connection details.";
            int rssi = info.getRssi();
            String signal;
            if (rssi <= -100 || rssi == 0) signal = "Unknown";
            else if (rssi >= -50) signal = "Excellent";
            else if (rssi >= -60) signal = "Very good";
            else if (rssi >= -67) signal = "Good";
            else if (rssi >= -75) signal = "Fair";
            else signal = "Weak";
            String ssid = info.getSSID();
            if (ssid == null || ssid.length() == 0) ssid = "Unavailable (Android privacy restriction)";
            return "WI-FI CONNECTION DIAGNOSTICS\nNetwork: " + ssid +
                    "\nSignal level: " + rssi + " dBm (" + signal + ")" +
                    "\nReported link speed: " + info.getLinkSpeed() + " Mbps" +
                    "\nThis is Wi-Fi link information, not an internet speed test. A stronger signal cannot be guaranteed by software.";
        } catch (SecurityException e) {
            return "WI-FI\nAndroid restricted some connection details. You can still open Wi-Fi settings.";
        } catch (Exception e) {
            return "WI-FI\nCould not read connection details: " + safeMessage(e);
        }
    }

    private void chooseApps() {
        launchableApps.clear();
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        final ArrayList<String> names = new ArrayList<>();
        final ArrayList<String> packages = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (getPackageManager().getLaunchIntentForPackage(app.packageName) != null) {
                String label;
                try { label = getPackageManager().getApplicationLabel(app).toString(); }
                catch (Exception e) { label = app.packageName; }
                launchableApps.add(app);
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
                String pkg = packages.get(which);
                if (isChecked) selectedPackages.add(pkg);
                else selectedPackages.remove(pkg);
            })
            .setPositiveButton("Save selection", (d, w) -> {
                prefs.edit().putStringSet("priority_packages", new HashSet<>(selectedPackages)).apply();
                refreshStats();
                showMessage("Focus profile saved", "Saved " + selectedPackages.size() +
                        " priority apps on this device. The selection remains after closing the app. Android does not allow an ordinary app to freeze other apps, so use the system app settings to restrict background use where available.");
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void cleanOwnCache() {
        long before = folderSize(getCacheDir());
        boolean success = deleteChildren(getCacheDir());
        long after = folderSize(getCacheDir());
        showMessage("Soft703 AI cache cleanup", (success ? "Cleanup completed." : "Cleanup completed with some files unavailable.") +
                "\nEstimated cache before: " + mb(before) + " MB" +
                "\nEstimated cache after: " + mb(after) + " MB" +
                "\nOnly this app's temporary cache was targeted. Your personal files and other apps' data were not touched.");
        refreshStats();
    }

    private void wifiDiagnosis() {
        new AlertDialog.Builder(this).setTitle("Wi-Fi Booster diagnostics")
            .setMessage(readWifi() +
                "\n\nA real repeater must receive Wi-Fi and retransmit it. Android 8.1 phones commonly cannot do this simultaneously through an ordinary app. This app cannot safely enable that hardware function by itself. Try router placement or a compatible physical repeater if coverage is weak.")
            .setPositiveButton("Open Wi-Fi settings", (d, w) -> launchSettings(Settings.ACTION_WIFI_SETTINGS))
            .setNegativeButton("Close", null).show();
    }

    private void openSharingSettings() {
        if (!launchSettings("android.settings.TETHER_SETTINGS")) {
            if (!launchSettings("android.settings.WIRELESS_SETTINGS")) {
                launchSettings(Settings.ACTION_WIFI_SETTINGS);
            }
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
            showMessage("App settings", "Android could not open the application settings screen.");
        }
    }

    private void swapInfo() {
        String swap = "unavailable";
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) {
                    swap = line.substring("SwapTotal:".length()).trim();
                    break;
                }
            }
            reader.close();
        } catch (Exception ignored) { }
        showMessage("Extended Memory / swap", "Android-reported swap: " + swap +
                "\nThis reading does not mean swap can be enabled or that it is extra physical RAM. Enabling system swap normally requires device/kernel support and privileged access. Soft703 AI will not pretend it has created extra RAM.");
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
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("OK", null).show();
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private long mb(long bytes) { return bytes / (1024L * 1024L); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}