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

## Command palette (drawer search → type `>`)

Type `>` (optionally followed by a command) in the drawer search and tap
**Open command palette**. Pick a target and tap **Run**; output appears below.

- **Shizuku shell**: `sh -c` through VictorLaunch's Shizuku user service (adb or root uid).
- **Root (su)**: only after *Dev panel → Allow root*.
- **Termux**: Termux's `RUN_COMMAND` intent, `bash -c` in the background, output returned to the palette.
  Setup: install Termux; in Termux run
  `mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings`;
  then Android Settings → Apps → VictorLaunch → Permissions → *Run commands in Termux environment* → Allow.

Saved commands (with favorites) live in local preferences and are not included in `config.export`.
Commands that look destructive (`rm`, `dd`, `reboot`, `pm uninstall`/`clear`, `mkfs`/`format`, writes to
block devices, `settings put`, …) show a confirmation dialog first.

**Safety:** every run is an explicit tap in the palette. The bridge has no exec/shell op
(`Ops.ALL` is asserted to contain none in `CommandPaletteTest`), and no external app or paired node can trigger a command.
