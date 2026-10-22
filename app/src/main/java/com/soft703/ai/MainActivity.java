package com.soft703.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    LinearLayout root;
    TextView ramText, storageText, wifiText;
    final ArrayList<String> selectedApps = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshStats();
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
        subtitle.setText("Extended Memory • Focus • Cleaner • Wi-Fi");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        ramText = card("RAM status");
        storageText = card("Storage status");
        wifiText = card("Wi-Fi status");

        button("Refresh system status", this::refreshStats);
        button("Focus Mode — choose priority apps", this::chooseApps);
        button("Open Android app settings", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        button("Clean Soft703 AI temporary cache", this::cleanOwnCache);
        button("Wi-Fi Booster — diagnose connection", this::wifiDiagnosis);
        button("Check virtual RAM / swap support", this::swapInfo);

        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setTextColor(0xFF4B5C70);
        note.setPadding(0, dp(12), 0, dp(8));
        note.setText("Device limits: an ordinary app cannot create physical RAM, force-stop arbitrary apps, or amplify the phone's Wi-Fi radio. Soft703 AI reports available information and opens Android controls where possible. Test these functions on your phone.");
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
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(7));
        root.addView(b, p);
    }

    private void refreshStats() {
        ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        ramText.setText("RAM\nAvailable now: " + mb(mi.availMem) + " MB\nTotal reported: " + mb(mi.totalMem) +
                " MB\nLow-memory state: " + (mi.lowMemory ? "Yes" : "No") +
                "\nAndroid manages system RAM automatically.");

        StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
        long free = fs.getAvailableBytes() / (1024L*1024L);
        long total = fs.getTotalBytes() / (1024L*1024L);
        storageText.setText("Storage status\nAvailable on app filesystem: " + free + " MB\nTotal filesystem: " + total +
                " MB\nThis is not a full device-wide storage inventory.");
        wifiText.setText(readWifi());
    }

    private String readWifi() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return "Wi-Fi\nWi-Fi is off. Turn it on in Android settings.";
            WifiInfo info = wm.getConnectionInfo();
            return "Wi-Fi\nSSID: " + info.getSSID() + "\nSignal: " + info.getRssi() + " dBm" +
                    "\nReported link speed: " + info.getLinkSpeed() + " Mbps\nSignal strength is not the same as internet speed or latency.";
        } catch (Exception e) {
            return "Wi-Fi\nAndroid did not expose all connection details. Check Wi-Fi settings.";
        }
    }

    private void chooseApps() {
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        ArrayList<String> labels = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (getPackageManager().getLaunchIntentForPackage(app.packageName) != null) {
                try { labels.add(getPackageManager().getApplicationLabel(app).toString()); }
                catch (Exception e) { labels.add(app.packageName); }
            }
        }
        String[] names = labels.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = selectedApps.contains(names[i]);
        new AlertDialog.Builder(this).setTitle("Choose priority apps")
            .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                String name = names[which];
                if (isChecked && !selectedApps.contains(name)) selectedApps.add(name);
                if (!isChecked) selectedApps.remove(name);
            })
            .setPositiveButton("Save selection", (d, w) ->
                new AlertDialog.Builder(this).setTitle("Focus profile saved")
                    .setMessage("Selected priority apps: " + selectedApps.size() +
                        "\nThis alpha keeps selections only for this app session. It cannot freeze other apps because standard Android apps lack that permission. Use Android settings to restrict apps where supported.")
                    .setPositiveButton("OK", null).show())
            .setNegativeButton("Cancel", null).show();
    }

    private void cleanOwnCache() {
        long before = folderSize(getCacheDir());
        deleteChildren(getCacheDir());
        long after = folderSize(getCacheDir());
        new AlertDialog.Builder(this).setTitle("Soft703 AI cache cleanup")
            .setMessage("Attempted to clear this app's temporary cache.\nEstimated cache before: " + mb(before) +
                " MB\nEstimated cache after: " + mb(after) +
                " MB\nPersonal files, other apps' data, and saved memories were not deleted.")
            .setPositiveButton("OK", null).show();
        refreshStats();
    }

    private void wifiDiagnosis() {
        new AlertDialog.Builder(this).setTitle("Wi-Fi Booster diagnosis")
            .setMessage(readWifi() + "\n\nSoftware cannot amplify the phone's antenna or increase router transmission power. For weak coverage, move closer to the router or use a compatible physical repeater. This feature diagnoses the connection; it does not physically boost it.")
            .setPositiveButton("Open Wi-Fi settings", (d,w) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
            .setNegativeButton("Close", null).show();
    }

    private void swapInfo() {
        new AlertDialog.Builder(this).setTitle("Extended Memory")
            .setMessage("A regular Android app cannot reliably enable or configure system swap on this device. This alpha does not claim extra RAM is created. Device/kernel support and required privileges must be verified before real swap can be considered.")
            .setPositiveButton("OK", null).show();
    }

    private long folderSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long size = 0;
        File[] children = f.listFiles();
        if (children != null) for (File child : children) size += folderSize(child);
        return size;
    }

    private void deleteChildren(File dir) {
        File[] children = dir.listFiles();
        if (children != null) for (File f : children) {
            if (f.isDirectory()) deleteChildren(f);
            try { f.delete(); } catch (Exception ignored) {}
        }
    }

    private long mb(long bytes) { return bytes / (1024L * 1024L); }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
