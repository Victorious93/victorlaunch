# Optional root module

VictorLaunch works fully as a normal user-installed app. This module is an
**optional** extra for rooted devices: it installs the same APK systemlessly as a
privileged system app (`priv-app`) so that the three privileged permissions the app
can use are granted automatically instead of via Shizuku, root, or `adb shell pm grant`.

> **Status:** nothing in this module has been tested on a device. The packaging and
> permission checks run in CI against the built APK; the install, boot and
> permission-grant behaviour described below is expected AOSP/Magisk behaviour, not an
> observed result. Read [Risks](#risks-and-bootloop-recovery) before flashing.

## What it installs

```
module.prop
customize.sh                      # Magisk / KernelSU / APatch detection, permissions
uninstall.sh                      # removes the install log on module removal
META-INF/com/google/android/update-binary    # standard Magisk installer stub
META-INF/com/google/android/updater-script   # "#MAGISK"
system/priv-app/VictorLaunch/VictorLaunch.apk
system/etc/permissions/privapp-permissions-com.victorious.launch.xml
```

There is no `service.sh`: the module does no work at boot. The permission grant is
done by Android's package manager from the allowlist XML, so nothing needs to run
after boot.

### Privileged permissions (and nothing else)

The allowlist contains only the `signature|privileged` permissions that the built
APK's merged manifest actually requests:

| Permission | Used by | Without it |
|---|---|---|
| `android.permission.WRITE_SECURE_SETTINGS` | Dev panel toggles (`DevSettings.hasWriteSecureSettings`) | Falls back to Shizuku, opted-in root, or a manual `pm grant`; otherwise the control is disabled with an explanation |
| `android.permission.READ_LOGS` | Dev panel logcat viewer (`DevSettings.hasReadLogs`) | The viewer shows only VictorLaunch's own lines |
| `android.permission.PACKAGE_USAGE_STATS` | Fleet dashboard "running" status and the bridge `running.list` read (checked through `AppOpsManager`) | The dashboard shows a usage-access hint; `running.list` reports a permission error |

Every use is checked at runtime and degrades gracefully, so the app does not require
the module. The other permissions in the manifest are normal, runtime (dangerous), or
signature-only and do not need an allowlist entry.

Quickstep / recents permissions (for example `MANAGE_ACTIVITY_TASKS`,
`START_TASKS_FROM_RECENTS`) are **not** included, because VictorLaunch does not request
them. Quickstep recents is still outstanding (see [LAWNCHAIR_REBASE.md](LAWNCHAIR_REBASE.md)).
If a future change adds such a permission to the manifest, CI fails until the allowlist
(or the reviewed list) is updated.

## Getting the zip

CI builds it on every push and pull request:

1. Open the **Android CI** run, then the artifact **victorlaunch-root-module**.
2. GitHub wraps artifacts in an outer zip. Unzip it once; the flashable file is
   `victorlaunch-root-module-<version>.zip`. Do not unzip that one.

GitHub requires you to be signed in to download Actions artifacts.

To build it yourself from a debug APK you already built (needs `aapt2` from the Android
build-tools, plus `zip`):

```bash
ANDROID_HOME=$HOME/Android/Sdk scripts/root-module/package.sh app/build/outputs/apk/debug/app-debug.apk build/root-module
```

The script refuses to produce a zip if `module/module.prop` (`version`, `versionCode`)
does not match the APK, or if the allowlist and the APK's requested permissions disagree.
When you bump `versionName`/`versionCode` in `app/build.gradle.kts`, bump
`module/module.prop` to match.

## Install

Requirements: Android 8.0 (API 26) or newer, and Magisk 20.4+, KernelSU, or APatch.

1. **Export a layout backup** in VictorLaunch (Settings), and note your launcher settings.
2. **Uninstall the user-installed VictorLaunch first** if it is present (Settings →
   Apps → VictorLaunch → Uninstall). See [Signature notes](#signature-notes). This erases
   its data, which is why you export first.
3. Flash the zip:
   - **Magisk:** Modules → Install from storage → pick the zip.
   - **KernelSU:** Module → Install → pick the zip.
   - **APatch:** Module → Install → pick the zip.
4. Reboot. The installer warns if it still sees a user-installed copy.
5. Set VictorLaunch as the default home app if needed, then import your layout backup.

`customize.sh` detects the manager from the environment the installer provides
(`APATCH`, then `KSU`, then `MAGISK_VER_CODE`; APatch is tested first because it also
exports `KSU=true`) and aborts on anything else, on Android older than 8.0, or if the
APK or allowlist is missing from the zip.

### Check it worked

```bash
adb shell dumpsys package com.victorious.launch | grep -E "codePath|privateFlags|WRITE_SECURE_SETTINGS|READ_LOGS|PACKAGE_USAGE_STATS"
```

Expect `codePath=/system/priv-app/VictorLaunch` and `PRIVILEGED` in the flags, with the
three permissions `granted=true`. In the app, Dev panel shows `WRITE_SECURE_SETTINGS: granted`
and `READ_LOGS: granted`. (These commands are the expected check; they have not been run
against this module.)

## Update

Flash a newer module zip (higher `versionCode`) and reboot. The system copy is replaced.
Installing a normal APK over a system copy is not the supported path for this module.

## Uninstall

Remove the module in Magisk, KernelSU, or APatch and reboot. The overlaid files are
removed and `uninstall.sh` deletes `/data/adb/victorlaunch/` (the install log).
VictorLaunch then disappears from the device unless you reinstall it as a normal app, and
its data may be removed with it. If VictorLaunch was your default home app, Android falls
back to another launcher or asks you to pick one; make sure a stock launcher exists.

## Risks and bootloop recovery

- A **priv-app that requests a privileged permission missing from its allowlist can
  fail the boot-time permission check** on Android 9+ and bootloop. That is why the
  allowlist is limited to what the APK requests and CI compares the two. A build whose
  manifest changed after the zip was made is the dangerous case: always flash the
  module built from the same APK version.
- Any module can bootloop a device for reasons unrelated to this one (conflicts with other
  modules, vendor partitions, or an OEM that enforces extra checks). Treat flashing as risky,
  keep a full backup, and do not flash this on a device you cannot recover.
- Rooting and modules can break banking apps, Play Integrity, and OTA updates.

**Recovery**, from least to most invasive. Marker files work the same on Magisk, KernelSU
and APatch because all three read `/data/adb/modules/<id>/`:

1. Boot to the manager's **safe mode** if your device allows it (it disables modules for
   that boot). The key combination depends on the manager and device.
2. With `adb` (it only helps if the device gets far enough to expose it) or from a custom
   recovery with `/data` decrypted:

   ```bash
   touch /data/adb/modules/victorlaunch/disable    # skip the module on next boot
   touch /data/adb/modules/victorlaunch/remove     # remove it on next boot
   ```

   Magisk also supports `adb wait-for-device shell magisk --remove-modules` to remove all modules.
3. Last resort: in recovery, `rm -rf /data/adb/modules/victorlaunch`, then reboot.
4. If the device still will not boot, reflash the stock boot image. This module does not
   touch the boot partition.

## Signature notes

- The zip packages the **debug** APK from CI, signed with the VictorLaunch debug key
  (public certificate SHA-256 in [`keystore/README.md`](../keystore/README.md); the key
  itself is never committed and is not in the zip). A priv-app signed with a debug key is
  fine for a personal device but is not a release-quality or Play-compatible setup.
- **Uninstall the user-installed copy first.** If a user-installed copy with a different
  signature is present, Android treats the two as conflicting and may ignore one of them,
  and the system copy may not take effect. Even with the same key, a user copy with an
  equal or higher `versionCode` can take precedence over the system copy, in which case
  the module appears to do nothing. Uninstalling first avoids both cases.
- If you build the APK locally without `keystore/victorlaunch-debug.jks`, Gradle falls
  back to the default Android debug key, which will not match the CI-signed copy. Do not
  mix APKs signed with different keys on the same device.
- Release signing is environment-only (`VL_RELEASE_*`); this module is not wired to it.

## Verified vs not verified

Verified in CI (see the **Package root module** job): shellcheck of the scripts,
packaging against the real built APK, matching `module.prop` and APK version, the
allowlist matching the APK's requested permissions, and the zip layout via `unzip -l`.

Not verified: flashing with Magisk, KernelSU or APatch, boot, the privileged grants on a
device, the system-versus-user-copy conflict behaviour, and any recovery step above.
