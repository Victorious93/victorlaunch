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

CI and matching local debug builds sign with a **gitignored** debug keystore. CI decodes it from GitHub secrets (`VL_DEBUG_KEYSTORE_B64` and the password secrets). Locally, put `keystore/victorlaunch-debug.jks` and export `VL_DEBUG_*`. If the file is missing, Gradle falls back to the default Android debug key and warns — that APK will not match the published cert. The current debug certificate SHA-256 is in `keystore/README.md`. The old committed cert `1B:06:A8:…:BA:CA` is **REVOKED**. History is not rewritten. Release builds are signed only from environment secrets; there is no release key in this tree.

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

The release cert slot is **TBD** and does not authorize.

**Risk:** Don chose that this pinned debug controller has **full control** of VictorLaunch (`app.launch`, `layout.set`, `panel.open`, no confirmation). That **deviates** from the fleet rule “destructive ops always gated”. Revoke it with Settings → **Allow controller full control**. Other peers stay off and still need confirmation.

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

### 6. Mods (optional, off by default)

Mods is a **gated** screen for four GPLv3 tools. VictorLaunch does **not** vendor their engines (Zygisk, APK rewriter, Geto UI, MMRL WebUI). It detects the **stock apps**, opens them, and uses Shizuku/root **command builders** when you already enabled Shizuku. Each item has a **root** path and a **non-root** path.

**It stays off until you turn it on.** Master switch, then a per-tool switch, each with a confirm dialog. Backup import cannot turn Mods on.

1. Home → **Mods** (puzzle icon) or Settings → **Mods**.
2. Enable **Mods**, read the warning, confirm.
3. Enable the tool you want, confirm its risks.
4. Install the stock app if it is missing, then use **Open stock …**.

Nothing on this screen was tested on a device.

#### LSPosed (GPLv3, github.com/LSPosed/LSPosed)

Stock packages: `org.lsposed.manager`, `org.lsposed.lspd`.

| Path | What this build does |
|---|---|
| **Root / Zygisk** | List `/data/adb/modules` via Shizuku/root, detect names containing `lsposed` / `zygisk_lsposed` / `riru_lsposed`, open the stock manager so you can enable modules and scopes there. |
| **Non-root** | LSPosed needs Zygisk/Riru. Use **LSPatch** instead. If the manager APK is installed it can still be opened. |

**Risks:** Xposed modules run inside other apps. A bad module can steal data or break the system. Reboot to undo. Never install a module you do not trust. In-process Zygisk and the manager’s module/scope API are **not** embedded (`mods-embed/lsposed/` is a skeleton).

#### LSPatch (GPLv3, github.com/LSPosed/LSPatch)

Stock package: `org.lsposed.lspatch`.

| Path | What this build does |
|---|---|
| **Root** | Same patcher hand-off as non-root, or install LSPosed as a Magisk/KernelSU module via MMRL. |
| **Non-root** | Pick a target app, resolve its APK path with Shizuku `pm path`, open stock LSPatch to patch, then **Install patched APK** so Android’s system installer asks you to confirm. |

`lspatch.jar` is **not** vendored. This APK never silent-installs. You must allow unknown sources for VictorLaunch (`REQUEST_INSTALL_PACKAGES`).

**Risks:** Patched APKs resign the app. Play Integrity / banking apps may refuse them. Uninstall the patched package to undo. Confirm every install.

#### Geto (identified as JackEblan/Geto)

Don wrote **“Geto”** with no URL. This tree treats that as **[JackEblan/Geto](https://github.com/JackEblan/Geto)** (`com.android.geto`, **GPL-3.0-only**). **Confidence: 90%.** It is the well-known Android app that applies per-app `WRITE_SECURE_SETTINGS` (developer options, animations, and similar) and sits next to LSPosed / LSPatch / MMRL on a Shizuku or rooted phone. Don did not paste a repository URL, so this is not 100%. Unrelated “GETO” acronyms were rejected.

| Path | What this build does |
|---|---|
| **Root** | `pm grant com.android.geto android.permission.WRITE_SECURE_SETTINGS` via `su`/Shizuku-as-root, then open Geto. |
| **Non-root** | The same grant through Shizuku (ADB). Without Shizuku, only a hand-off to the stock app. |

Geto’s settings engine is **not** vendored (`mods-embed/geto/` is a skeleton).

**Risks:** `WRITE_SECURE_SETTINGS` can hide developer options and change global settings. A banking app may still detect other signals. Revoke the grant in App permissions / appops.

#### MMRL (GPLv3, github.com/MMRLApp/MMRL)

Stock package: `com.dergoogler.mmrl`. License checked: **GPL-3.0**.

| Path | What this build does |
|---|---|
| **Root** | Open MMRL to browse/install Magisk, KernelSU, and APatch modules. Optional **Install zip via Magisk / KernelSU / APatch** copies a zip you pick and runs `magisk --install-module`, `ksud module install`, or `apd module install` **only after you confirm**. Not auto-run. |
| **Non-root** | **Read-only:** a static list of well-known repo **names** (this APK has no `INTERNET` and does not download). Hand-off to stock MMRL, which itself needs root to install. |

MMRL’s WebUI and repo fetch are **not** vendored (`mods-embed/mmrl/` is a skeleton).

**Risks:** Magisk/KernelSU/APatch modules modify the system at boot. A bad zip can bootloop. Keep a recovery path.

See `mods-embed/README.md` for the later-PR embed plan.

![Mods screen](screenshots/14-mods.png)
*Placeholder: master switch off, four tool cards, stock-app hand-off buttons.*

---

## Features, step by step

### Home workspace

VictorLaunch’s home is a **Lawnchair-lineage** workspace (Launcher3
ideas: pages, dock, folders, widgets, icon packs, gestures). It is
**not** a vendored Lawnchair APK. Quickstep recents are not included.

- Swipe **up** to open the app drawer. Swipe **down** to focus search.
- Swipe **left / right** (or the pager) for extra pages.
- Long-press an app in the drawer → pin to workspace, pin to dock, or
  start a **folder** (needs one other pinned app).
- Tap the widget button to bind a system `AppWidget` (Android may ask
  for bind permission). [Likely] some providers need a configure
  activity that can fail.
- Settings → **Home (Lawnchair lineage)** picks an ADW / GO / Nova
  icon pack when one is installed; otherwise system icons.
- Hidden apps (Settings → Lockdown) do not appear.
- Locked apps prompt **BiometricPrompt** (or the device PIN) before they open.

[Certain] the workspace compiles in this tree. [Guessing] gestures and
widget bind on a phone — nothing here was device-tested.

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

Tiles: **Network** (this phone’s radio, from the local monitor), **Mesh**, **VPN**, **Browser proxy**, **VictorSMS**, **Paired-node count**.

| Age of a snapshot | What you see |
|---|---|
| 0–30 seconds | Current |
| 30 seconds–5 minutes | Same row, marked **stale** |
| Older than 5 minutes | Row dropped |

Pull interval: 15 seconds, controller only. Empty / unknown tiles mean “we have no snapshot”, not “everything is fine”.

![Status tray](screenshots/08-status-tray.png)
*Placeholder: five tiles; some “no data”, none showing invented OK.*

### Network Monitor (optional, off by default)

Home → **cell-tower icon**, or Settings → **Network Monitor**. Everything stays **off** until you enable it and grant **phone state** + **fine location**. Backup import cannot turn it on. History and cell IDs stay on this phone. There is no telemetry and no `INTERNET`. Nothing here was tested on a device.

**This is not a universal IMSI-catcher detector.** An unexpected drop to 2G/GSM is a *heuristic* that can also be rural coverage, a carrier fallback, or airplane-mode recovery.

#### What it monitors

On Android 12+ it registers a `TelephonyCallback` (display info, signal, cell info, service state, data state). Older APIs use `PhoneStateListener`. It records:

- RAT: 5G NR, NR-NSA, LTE / LTE-CA, 3G, 2G/GSM
- RSRP / RSRQ / SINR when the serving cell reports them
- Serving cell ID (MCC-MNC-CID / NCI) — not IMSI/IMEI
- Registered / data-connection state

#### Alerts

| Alert | Meaning |
|---|---|
| `unexpected_2g` | Was 3G+ and landed on 2G/GSM. Treat as suspicious, not proof. |
| `downgrade` | Generation dropped (NR→LTE, LTE→3G, …) without landing on 2G. |
| `sudden_cell_change` | Serving cell ID changed while RSRP was still usable. Handover is often normal. |
| `signal_drop` | RSRP fell by 12 dB or more between samples. |

The home-screen **Network** tile is **this phone**, not a scrape of Mesh/VPN. `node.status.get` includes a `network` domain. Paired controllers can also call **`network.status`** for the richer JSON (RAT, signal, last alerts, limits).

#### Prevent drops — non-root

A normal app **cannot** lock the preferred network type. Android 12+ `TelephonyManager.setAllowedNetworkTypesForReason` needs `MODIFY_PHONE_STATE` (shell/root via Shizuku, or a carrier privilege).

Without Shizuku:

1. **Open Allow 2G / operator settings** — SIM / network-operator screen. Flip **Allow 2G** off if the OEM shows it (Pixel/AOSP Android 14+; many OEMs hide or rename it).
2. **Preferred network / wireless settings** — set LTE/NR preferred if the OEM exposes it.
3. **Sticky-network hint:** stay on LTE/NR when the carrier allows it. Do not roam onto GSM “for coverage” on a phone you care about.
4. **Wi-Fi fallback:** if you see 2G, turn Wi-Fi on and use it instead of the fake-looking cell.

#### Prevent drops — root / Shizuku

When the monitor is on **and** Shizuku is ready:

- **Block 2G** writes `settings put global allowed_2g 0` and `enable_2g 0`, then tries `setAllowedNetworkTypesForReason`.
- **Lock LTE/NR only** uses bitmask LTE+LTE_CA+NR and `preferred_network_mode=23` (`NT_MODE_NR_LTE` on AOSP). **Samsung and other OEMs use different integers** — the modem may ignore this.
- **Auto-recover** (optional) toggles airplane mode via `cmd connectivity airplane-mode` / settings / broadcast when a drop or 2G alert fires, with a 60s cooldown and at most 3 attempts per 10 minutes. It **drops the current call**.
- **Toggle radio now** does the same recover once, after confirm.

**Cell/band lock is not written.** Qualcomm NV / `at+qnwlock` / Samsung debug are modem-specific and easy to brick. The command builder refuses to run them.

#### Per-device limits

- No SIM / Wi-Fi-only tablet: RAT stays `none`.
- Location toggle off: cell ID and some signal fields are missing even with the permission granted.
- Dual-SIM: this build reads the default data subscription only.
- MVNOs and OEM “smart 5G” switches can downgrade without a fake BTS.
- `preferred_network_mode` and Allow 2G are **not** the same across Pixel, Samsung, Xiaomi, and carrier-branded firmware.

![Network Monitor](screenshots/15-network.png)
*Placeholder: enable switch, current RAT, alert list, lock toggles.*

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

Settings → **Install / Update fleet apps**.

1. Tap **Load local manifest** and pick a `fleet-manifest.json`
   (`schemaVersion` 1 from Victorious93/fleet-manifest). VictorLaunch
   has **no INTERNET**, so it will not download the live feed.
2. Each row shows manifest version vs installed version. Rows whose
   `certSha256` is missing or not on the pin allow-list stay
   **UNTRUSTED** and will not install.
3. Tap **Install APK…** / **Update APK…**, pick the matching APK.
   VictorLaunch checks the APK signer (and asset SHA-256 when present)
   then opens the **system PackageInstaller confirm sheet**. It never
   silent-installs.

See `docs/fleet-manifest.md`. [Certain] the parser and pin checks have
unit tests. [Guessing] PackageInstaller on a phone — not device-tested.

![Fleet install](screenshots/12-fleet-stub.png)
*Placeholder: manifest rows with Install / Update.*

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
| `REQUEST_INSTALL_PACKAGES` | LSPatch patched-APK install **and** fleet Install/Update. Android still shows the system confirm sheet. Unknown-sources must be allowed for this app. |
| `READ_PHONE_STATE` | Network Monitor RAT / data state. Runtime-granted. Off until you enable the monitor. |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Serving-cell ID and some signal fields. Runtime-granted. Cell info is blocked by the OS without this. |

Wallpaper is shown with `windowShowWallpaper`. VictorLaunch does not need `SET_WALLPAPER` to display the current wallpaper.

---

## Fleet integration

- **Inbound action:** `com.victorious.launch.action.BRIDGE` (Messenger, `what=1` request / `2` response).
- **Envelope:** v1.1 flat fields. See `docs/external-node.md`.
- **Always-on reads (when the caller is paired):** `capabilities.get`, `node.status.get`, `network.status`, `fleet.list`, `layout.get`, `installed.list`, `running.list`.
- **Own settings:** `config.export` / `config.import` with `remoteAllowed: false`. Import `requiresConfirm: true` and supports `dryRun`.
- **Actions (pinned debug controller):** `app.launch`, `layout.set`, `panel.open` run with **no confirmation** while Settings → **Allow controller full control** is on (default ON). Don chose this; it **deviates** from the fleet rule that destructive ops stay gated. Turn the kill switch **off** to revoke it. Package+cert, replay/nonce/ttl, and an audit log still apply.
- **Actions (every other peer):** those ops run only if **Allow other peers** is on (default **off**). `layout.set` still needs on-device confirmation. Backup import cannot turn this on.
- **Status consumer:** `node.status.get` to **controller** every 15s.
- **Status producer:** this process’s `node_health` plus `network` when the monitor is enabled. No scraping of other packages. `network.status` is the richer on-device radio JSON.

Generic `remote.command` / other-peer external control is never auto-enabled, including after backup import. The controller kill switch is also not set by backup.

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
The pinned debug controller already has it (kill switch ON by default). For any other peer: yes, explicitly, under **Allow other peers**. A backup cannot turn either of those on for you.

**How do I revoke the controller?**  
Settings → **Allow controller full control** → off. Package+cert pairing and status pulls still work; action ops from the controller then follow the other-peer rules.

**Shizuku says waiting for binder.**  
The stock Shizuku server is not running. Start it (wireless debugging, adb, or root), then return. Enabling the VictorLaunch toggle does nothing by itself.

**Force-stop / system hide did nothing.**  
Shizuku is off, the permission was denied, or the UserService failed to bind. The drawer hide still applies locally.

**Mods buttons say enable first.**  
Master switch **and** that tool’s switch must both be on. They default off and are not set by backup import.

**Open stock LSPosed / LSPatch / Geto / MMRL does nothing.**  
The stock GPLv3 app is not installed. This APK is a client + skeleton, not a full embed.

**Install patched APK never appears.**  
Allow unknown sources for VictorLaunch (Mods → Unknown-sources permission), then pick an APK. The system installer must still be confirmed. The rewriter is not in this APK.

**Geto grant failed.**  
Enable Shizuku, grant its permission, and install `com.android.geto`. Root or ADB can `pm grant WRITE_SECURE_SETTINGS`; a normal app cannot.

**Network tile says off / no data.**  
Enable Network Monitor and grant phone + location. Turn the system location toggle on. Wi-Fi-only devices have no serving cell.

**Block 2G / LTE lock did nothing.**  
A normal app cannot change that. Enable Shizuku (or root), tap **Apply lock now**, and still expect OEM ignore. Use the system Allow 2G screen as the fallback.

**Is unexpected 2G an IMSI catcher?**  
No claim. It is a heuristic. Rural GSM, carrier fallback, and a fake BTS can look the same from these APIs.

---

## Privacy

- No `INTERNET` permission, no crash reporter, no analytics SDK.
- Status `detail` text is capped and scanned for secret-like names.
- Backup JSON is launcher preferences only. Signing keys, keystore passwords, API tokens, VictorSMS secrets, and Twilio fields are refused.
- Pairing stores a **certificate fingerprint**, not a private key.
- Android cloud backup of pairing prefs is excluded.
- The wrapping key for the fleet bundle never enters this app.
- The Shizuku enable flag is **not** part of launcher backup JSON.
- Mods master/per-tool flags are **not** part of launcher backup JSON. Import ignores any Mods keys.
- Network Monitor flags and cell-ID history are **not** part of launcher backup JSON and never leave the device. Import ignores Network Monitor keys.

---

## License

GNU General Public License v3.0. See `LICENSE`, `NOTICE`, and `THIRD_PARTY.md`.
