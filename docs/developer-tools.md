# Developer tools

VictorLaunch's developer features. None of them is reachable from the bridge
(`BridgeService`); they run only from on-device taps.

## Privilege model

Writes and privileged reads try, in order:

1. `WRITE_SECURE_SETTINGS` held by VictorLaunch (global/secure keys only).
2. Shizuku (Settings → Shizuku), as adb (uid 2000) or root.
3. Root via `su`, **only** if you turned on *Dev panel → Allow root*. `su` is never run otherwise.

When none is available, the control is disabled and shows why.

One-time grants (either from the Dev panel with Shizuku/root, or from a PC):

```
adb shell pm grant com.victorious.launch android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.victorious.launch android.permission.READ_LOGS
```

After granting `READ_LOGS`, force-stop and reopen VictorLaunch. Android 13+ may also
show a one-time "allow log access" prompt.

## Dev panel (top bar → developer icon)

- Wireless debugging toggle (`adb_wifi_enabled`, Android 11+) with an `adb connect ip:port` hint when the port is readable.
- USB debugging status (read-only; change it in Developer options).
- Quick toggles: show taps, pointer location, layout bounds (`debug.layout`), stay awake while charging.
  Show taps, pointer location and layout bounds always need Shizuku or root.
- Animation scales (window / transition / animator).
- Logcat viewer: tag, text, package (by PID) and minimum level filters; pause, clear, share, export via the system file picker.

## App inspector (long-press any app → Inspect app)

Package, version name/code, target/min SDK, install source, signing certificate
SHA-256 (tap to copy), requested/granted permissions, component counts and APK size.
**Extract APK…** copies the base APK and every split into a folder you pick
(Storage Access Framework); nothing is written anywhere else.
