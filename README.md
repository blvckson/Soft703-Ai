# Soft703 AI

An early Android project for device status, Focus Mode selection, app-owned cache cleanup, Wi-Fi diagnostics, and checking virtual-memory limitations.

## Build
GitHub Actions builds a debug APK on pushes to `main` and via manual workflow dispatch. Download the artifact named `Soft703-AI-debug-apk` from the completed workflow run.

## Current alpha limits
- Reports Android's available/total RAM; it does not create physical RAM.
- Focus Mode currently lets the user select priority apps for the current session and links to Android settings. It cannot force-stop/freeze other apps without privileged device-management access.
- Cleaner only attempts to clear this app's cache; it does not delete user files or other apps' data.
- Wi-Fi tool displays Android-exposed signal/link data and opens Wi-Fi settings. It cannot increase radio power or replace a physical repeater.
- Virtual RAM/swap support has not been enabled; device/kernel support must be verified first.
- This initial build is a prototype and needs testing on the target Tecno P703 running Android 8.1.0.
