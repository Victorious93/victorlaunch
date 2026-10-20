# VictorLaunch manual

This is the user manual for VictorLaunch, the home-screen launcher for the Victorious fleet. The same text is shown in **Settings → Help** on the phone.

Screenshot files named below are placeholders. Drop real captures into `docs/screenshots/` using those file names when you photograph a device.

---

## What VictorLaunch is

VictorLaunch is the **fleet hub on the home screen**. It is the first thing you see after unlock: a workspace, a dock, an app drawer, and a **status tray** across the top.

It is not the controller. **DroidCommand AI** (`ai.droidcommand.app`) is the controller. VictorLaunch:

- launches apps (personal profile and the Island work profile)
- shows live fleet health **only** as the controller published it
- can ask the controller to export or import a fleet backup, but **never holds the wrapping key**
- can be a profile owner of a managed work profile (“Island”)
- speaks the fleet EXTERNAL_NODE bridge so other paired apps can ask it questions

VictorLaunch does **not** scrape Mesh, VPN, Browser, or VictorSMS itself. If the controller is missing, unpaired, or silent, the tray shows “no data”. It does not invent green lights.

---

## Role as the fleet hub

Think of three layers:

1. **Home screen** — you. Icons, dock, Island tab, lockdown.
2. **Controller** — DroidCommand AI. Pairing table, aggregated `node.status.get`, fleet backup envelope.
3. **Other nodes** — Mesh, Browser, VictorSMS, and the rest. They answer the controller. VictorLaunch reads the controller’s picture.

VictorLaunch also answers `node.status.get` for **itself** (`node_health`), so the controller can include this launcher in that picture.

---

## Setup

### 1. Install the debug build

CI and `./gradlew assembleDebug` sign with the committed debug keystore. The SHA-256 of that cert is in `keystore/README.md`. Release builds are signed only from environment secrets; there is no release key in this tree.

Install the APK, then open VictorLaunch.

![Home after first launch](screenshots/01-home.png)
*Placeholder: empty workspace, status tray with “no data”, dock with the app-drawer button.*

### 2. Make it the default launcher

1. Open **Settings** (gear on the home screen) → **Default launcher**.
2. Tap **Open default-home settings**.
3. Choose **VictorLaunch**.

Android may also ask the first time you press Home.

![Default home picker](screenshots/02-default-home.png)
*Placeholder: Android’s “Choose a home app” sheet with VictorLaunch selected.*

### 3. Create the Island work profile (optional)

Island is a **managed work profile**. VictorLaunch is the **profile owner** of that profile.

1. Settings → **Island** → **Create work profile**, or open the app drawer → **Island** → **Create Island**.
2. Accept Android’s provisioning screens. This is a real managed profile, not a fake folder.
3. After provisioning, the Island tab lists apps in that profile via `LauncherApps`.

To pause or resume the profile: **Pause Island** / **Resume Island** (Quiet mode).

![Island provisioning](screenshots/03-island-provision.png)
*Placeholder: Android managed-profile provision flow.*

### 4. Pair to the controller

Status tiles stay empty until VictorLaunch can **authorize** DroidCommand AI **by package + signing certificate**.

The debug controller is **pre-pinned**:

- package `ai.droidcommand.app`
- SHA-256 `88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D`

The release cert slot is **TBD** and does not authorize. Pinning does not turn on external control.

1. Install DroidCommand AI (debug build) on the same phone.
2. If that APK’s cert matches the pin, VictorLaunch binds its bridge every 15 seconds and sends `node.status.get` targeted at `controller`. No Settings tap is required.
3. Settings → **Pair controller** can still record an extra pairing row (same package, whatever cert is installed).

If the app is not installed, the cert does not match the pin (or extra pairing), or the bridge is not exported, the tray tells you that. It still does not fill in fake Mesh/VPN/SMS rows.

![Pairing](screenshots/04-pair-controller.png)
*Placeholder: Settings pairing section showing package + SHA-256.*

### 5. Shizuku (optional, off by default)

Shizuku lets ordinary apps call a few system APIs as **ADB (shell, uid 2000)** or **root (uid 0)**. VictorLaunch can use that for freeze/hide, force-stop, granting usage access, and some Island commands **without** being device owner.

This build does **not** vendor the Shizuku server. It talks to the **stock Shizuku app** (`moe.shizuku.privileged.api`). A later PR will embed a renamed fork (Apache-2.0, no Shizuku icons or package id). See `shizuku-embed/README.md`.

**It stays off until you turn it on.** Backup import cannot turn it on.

1. Install [Shizuku](https://shizuku.rikka.app/) (or Sui on Magisk).
2. Start its server using one of the three modes below.
3. Open VictorLaunch → Settings → **Shizuku** → enable (confirm the warning) → **Grant Shizuku permission**.
4. Status should read `ready (uid 2000)` or `ready (uid 0)`.

#### Startup modes

**Wireless debugging (Android 11+)**  
Settings → Developer options → Wireless debugging. In the stock Shizuku app, pair with the six-digit code, then start. VictorLaunch’s **Open wireless debugging** button only opens that system screen; it does not type the pairing code yet.

**adb command**  
On a computer: `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`  
Re-run after every reboot until a server is embedded.

**Root**  
`su -c` the same starter, or install Sui. Root is more powerful than ADB; only do this on a device you control.

#### Risks

- ADB/root can freeze, disable, and force-stop other apps, including ones you did not mean to touch.
- On some OEMs a bad `pm` command needs a reboot to undo.
- Granting usage access this way skips the system Settings toggle — you can revoke it in Digital Wellbeing / Usage access.
- Leave the toggle **off** on a shared phone.
- We never use Shizuku’s name as our app name, never ship their launcher icons, and never use `moe.shizuku.privileged.api` as our `applicationId`.

![Shizuku settings](screenshots/13-shizuku.png)
*Placeholder: enable switch, status line, three startup modes.*

---

## Features, step by step

### Home workspace

- The grid shows apps you pinned. Long-press an app in the drawer to pin it.
- Hidden apps (Settings → Lockdown) do not appear.
- Locked apps prompt **BiometricPrompt** (or the device PIN) before they open.

![Workspace](screenshots/05-workspace.png)
*Placeholder: pinned icons on the wallpaper.*

### Dock

- Up to five apps plus the drawer button.
- Long-press an app in the drawer to add it to the dock.

![Dock](screenshots/06-dock.png)
*Placeholder: bottom dock on charcoal with cyan/magenta accents.*

### App drawer

1. Tap the grid button on the dock.
2. **Personal** lists user-profile launchers.
3. **Island** lists work-profile launchers (`LauncherApps` across `UserManager.userProfiles`).
4. If “Lock Island tab” is on, the Island tab asks for biometrics first.

![App drawer](screenshots/07-drawer.png)
*Placeholder: Personal / Island tabs.*

### Status tray

Tiles: **Mesh**, **VPN**, **Browser proxy**, **VictorSMS**, **Paired-node count**.

| Age of a snapshot | What you see |
|---|---|
| 0–30 seconds | Current |
| 30 seconds–5 minutes | Same row, marked **stale** |
| Older than 5 minutes | Row dropped |

Pull interval: 15 seconds, controller only. Empty / unknown tiles mean “we have no snapshot”, not “everything is fine”.

![Status tray](screenshots/08-status-tray.png)
*Placeholder: five tiles; some “no data”, none showing invented OK.*

### Shizuku-powered freeze, hide, force-stop

When Shizuku is **enabled, bound, and permitted**:

- **Hide** in Lockdown also runs `pm disable-user --user <id>` (system hide, not just the drawer filter).
- **Freeze / Unfreeze** run `pm suspend` / `pm unsuspend` (personal profile, plus Island freeze when a work profile exists).
- **Force stop** runs `am force-stop --user <id>`.
- **Grant usage access** runs `appops set … GET_USAGE_STATS allow`.
- Island **clone / freeze / pause** prefer these shell calls, then fall back to DevicePolicyManager if Shizuku is off.
- **Create Island via Shizuku** runs `pm create-user --managed` so you can get a work profile **without** being device owner. That user is not automatically a VictorLaunch profile-owner; DPM clone/freeze still needs the provisioning flow.

The first tap after enabling Shizuku may say the UserService is still binding. Tap again. If Shizuku is off, those buttons tell you so. They do not pretend it worked.

### Island: clone, freeze, unfreeze, hide

From Settings → Lockdown, **Clone** asks the work-profile copy (profile owner) to `installExistingPackage`. Freeze / unfreeze uses `setPackagesSuspended`. Hide / unhide uses `setApplicationHidden`. Pause / resume uses `UserManager.requestQuietModeEnabled`.

These calls no-op if there is no work profile yet.

![Island actions](screenshots/09-island-actions.png)
*Placeholder: clone / freeze controls.*

### Lockdown

- **Hide** — app omitted from drawer and workspace.
- **Lock** — BiometricPrompt before launch.
- **Lock Island tab** — BiometricPrompt before that tab.
- Folders: locked folder ids are stored in launcher preferences and gated the same way (biometric) when you open them.

![Lockdown](screenshots/10-lockdown.png)
*Placeholder: Hide / Lock toggles next to app names.*

### Backup

1. **Export launcher preferences** shares a JSON file of dock, workspace, hidden/locked apps, grid, Island-tab lock. `externalControlEnabled` is always written `false`.
2. **Import** opens a file, **dry-runs**, then asks you to confirm. Applying still forces external control **off**.
3. **Fleet bundle** (macros, other apps, wrapping key) is the controller’s job. VictorLaunch may *ask* DroidCommand AI to export or import that bundle. It never stores the wrapping key.

Forbidden names (`password`, `token`, `secret`, Twilio fields, …) refuse the file instead of stripping them.

![Backup](screenshots/11-backup.png)
*Placeholder: export / dry-run confirm dialog.*

### Install / Update fleet apps

This screen is a **stub**. The fleet manifest schema is not frozen. It does not download or install APKs.

![Fleet stub](screenshots/12-fleet-stub.png)
*Placeholder: stub explanation.*

### Help

Settings → Help (or the `?` on home) shows this manual from app assets.

---

## Permissions and why

VictorLaunch does **not** request `INTERNET`. There is no telemetry.

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | A home app has to see every launchable activity, including ones without a Play-visible launcher filter. |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Lock apps, folders, and the Island tab. |
| `PACKAGE_USAGE_STATS` (special) | Only for `running.list` on the bridge. If you never grant it in system Settings, that op returns `permission` / `not_available`. Nothing else breaks. |
| `BIND_DEVICE_ADMIN` (on the admin receiver) | Required so Android can make VictorLaunch the profile owner of the Island work profile. |
| Exported `BRIDGE` service | So the controller (and other paired nodes) can send newline-delimited JSON. Callers are authorized by **package + cert**, not by a payload field. |
| `ShizukuProvider` (`INTERACT_ACROSS_USERS_FULL` on the provider) | Lets the stock (or future embedded) Shizuku server hand us a binder. No extra runtime permission dialog from Android itself; Shizuku has its own grant prompt. |

Wallpaper is shown with `windowShowWallpaper`. VictorLaunch does not need `SET_WALLPAPER` to display the current wallpaper.

---

## Fleet integration

- **Inbound action:** `com.victorious.launch.action.BRIDGE` (Messenger, `what=1` request / `2` response).
- **Envelope:** v1.1 flat fields. See `docs/external-node.md`.
- **Always-on reads (when the caller is paired):** `capabilities.get`, `node.status.get`, `fleet.list`, `layout.get`, `installed.list`, `running.list`.
- **Own settings:** `config.export` / `config.import` with `remoteAllowed: false`. Import `requiresConfirm: true` and supports `dryRun`.
- **Actions:** `app.launch`, `layout.set`, `panel.open` run only if **external control** is on (it is not, by default). `layout.set` also needs on-device confirmation.
- **Status consumer:** `node.status.get` to **controller** every 15s.
- **Status producer:** this process’s `node_health` only. No scraping of other packages.

`remote.command` is never auto-enabled, including after backup import.

---

## Troubleshooting and FAQ

**The tray is empty.**  
Pair DroidCommand AI. If it is installed but its bridge is not exported (skeleton controller), you will see “unreachable”. That is honest.

**Home still opens another launcher.**  
Default-home settings → VictorLaunch. Some OEMs hide this under Apps → Default apps.

**Apps missing from the drawer.**  
They may be hidden in Lockdown, or `QUERY_ALL_PACKAGES` was stripped by the OS. Reinstall. Work-profile apps only show on the Island tab.

**Island tab is empty.**  
Create the work profile, or Resume if it is paused (Quiet mode). Unlock the tab if it is locked.

**Clone / freeze did nothing.**  
Those DevicePolicyManager calls run as **profile owner** in the work profile. Until provisioning finishes, the buttons only send a request.

**How do I remove the work profile?**  
On the phone: **Settings → Accounts / Users → Work → Remove work profile**. That deletes the Island user and its cloned apps. VictorLaunch in the personal profile stays. If you are looking at the work-profile copy and it is profile owner, wiping that profile is the same outcome — treat it as destructive.

**Biometric prompt never appears.**  
The device has no enrolled biometric or PIN. The lock then fails open so you are not locked out of your own phone. Enroll a credential in Android Settings.

**I need to uninstall.**  
If Island exists, remove the work profile first, then uninstall `com.victorious.launch`. Android will refuse to uninstall a profile-owner admin until the profile is gone.

**Can I turn on external control?**  
Yes, explicitly, in Settings. A backup cannot turn it on for you.

**Shizuku says waiting for binder.**  
The stock Shizuku server is not running. Start it (wireless debugging, adb, or root), then return. Enabling the VictorLaunch toggle does nothing by itself.

**Force-stop / system hide did nothing.**  
Shizuku is off, the permission was denied, or the UserService failed to bind. The drawer hide still applies locally.

---

## Privacy

- No `INTERNET` permission, no crash reporter, no analytics SDK.
- Status `detail` text is capped and scanned for secret-like names.
- Backup JSON is launcher preferences only. Signing keys, keystore passwords, API tokens, VictorSMS secrets, and Twilio fields are refused.
- Pairing stores a **certificate fingerprint**, not a private key.
- Android cloud backup of pairing prefs is excluded.
- The wrapping key for the fleet bundle never enters this app.
- The Shizuku enable flag is **not** part of launcher backup JSON.

---

## License

GNU General Public License v3.0. See `LICENSE`, `NOTICE`, and `THIRD_PARTY.md`.
