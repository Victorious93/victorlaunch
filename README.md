<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="docs/assets/banner.svg" alt="VictorLaunch: neon cyan-to-magenta ring on dark charcoal" width="100%">
</p>

<p align="center">
  <a href="https://github.com/Victorious93/victorlaunch/actions/workflows/android.yml"><img alt="Android CI" src="https://github.com/Victorious93/victorlaunch/actions/workflows/android.yml/badge.svg?branch=main"></a>
  <a href="LICENSE"><img alt="License: GPL v3" src="https://img.shields.io/github/license/Victorious93/victorlaunch?color=00E5FF"></a>
  <a href="https://github.com/Victorious93/victorlaunch/releases/tag/debug-latest"><img alt="Latest debug release" src="https://img.shields.io/github/v/release/Victorious93/victorlaunch?include_prereleases&label=debug%20release&color=FF2BD6"></a>
</p>

# VictorLaunch

Nova-class, Lawnchair-lineage home-screen launcher for the Victorious fleet.

**VictorLaunch works as a standalone launcher; no other fleet apps are required.** Fleet
features (status tray, fleet dashboard, fleet long-press actions, share targets) light up
only for fleet apps that are actually installed, and show "no data" or hide themselves
otherwise.

**Download:** the latest debug APK is the `debug-latest` pre-release,
[releases/tag/debug-latest](https://github.com/Victorious93/victorlaunch/releases/tag/debug-latest).
CI rebuilds and republishes it on every push to `main`. It is a debug-signed build, not a
release build.

**Package id:** `com.victorious.launch` · minSdk 26 · targetSdk 35

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

## What it is

A HOME activity with a paged workspace, dock and app drawer, a status tray that **only**
reads the controller's `node.status.get`, an Island work profile, and an `EXTERNAL_NODE`
bridge so [droidcommand-AI](https://github.com/Victorious93/droidcommand-AI) (`ai.droidcommand.app`)
can read its status and, subject to the gates documented there, ask it to act. The bridge is a Messenger
service (`com.victorious.launch.action.BRIDGE`) authorized by package + signing
certificate. See [`docs/external-node.md`](docs/external-node.md). Live traffic against a
running controller is documented there as unverified.

## Features

Each item below exists in the code; the manual ([`docs/MANUAL.md`](docs/MANUAL.md)) has the
step-by-step version.

| Feature | What it does |
|---|---|
| **Dev panel** | Wireless debugging toggle, quick developer toggles, animation scales, and a logcat viewer with filters and export. Writes go through `WRITE_SECURE_SETTINGS`, Shizuku, or opted-in root. See [`docs/developer-tools.md`](docs/developer-tools.md). |
| **App inspector** | Long-press an app, then Inspect app: versions, SDK levels, install source, signing certificate SHA-256, permissions, component counts. Extract APK copies the base APK and splits into a folder you pick. |
| **Command palette** | Type `>` in drawer search. Run commands through Shizuku, opted-in root, or Termux `RUN_COMMAND`, with saved commands and a confirm step for destructive-looking commands. Every run is an explicit tap. |
| **GitHub widgets** | Pull requests, Actions, and releases home-screen widgets backed by `api.github.com`. The token is kept in encrypted preferences and never exported. |
| **Device widgets** | Device info, root status, and CPU / RAM / battery widgets. |
| **Fleet dashboard** | A home page you swipe to: install state, version, update-available (from a local manifest), optional running state (needs usage access), and Mesh / VPN / Browser / Messaging tiles from the controller's tray. |
| **Fleet long-press actions** | Extra actions for known fleet apps using only public intents and shortcuts the target app publishes. |
| **Layout backup / restore** | Export launcher preferences through the system file picker; import runs a dry-run with a preview diff and needs confirmation. Secrets and risky switches are never written or enabled by an import. |
| **Theme gallery** | One-tap palettes, including Neon ring (charcoal `#0B0F1A`, cyan, magenta), plus icon-pack previews. Published to fleet apps through a read-only provider. |
| **Notification badges** | Opt-in icon dots and long-press previews via a notification listener. Content stays in memory only. Off until you enable it and grant notification access. |
| **URL tools** | Offline safety check, optional VirusTotal scan (your own key, only on tap), redirect expand, is.gd shorten, tracking-parameter cleaner, QR code. Also a share / Open-with target. |
| **Mesh / Router VPN toggles** | Mesh VPN on/off (Victorious Mesh) and Router VPN on/off (VictorMango) in fleet long-press and the dashboard tiles. Shown only when the target app's published shortcut or intent resolves at runtime; never a silent toggle and not a bridge op. |

Also included: the Island work profile, gesture actions, a drawer with fuzzy search and
categories, fleet status widgets, share-to-fleet targets, fleet install/update from a local
manifest, a gated Network Monitor, and gated Shizuku and Mods integrations (details below).

## Lineage and license

VictorLaunch is licensed under the **GNU GPL v3.0** (see [`LICENSE`](LICENSE),
[`NOTICE`](NOTICE), [`THIRD_PARTY.md`](THIRD_PARTY.md)).

It is a **Lawnchair-lineage** Kotlin launcher: [Lawnchair](https://github.com/LawnchairLauncher/lawnchair)
(GPL-3.0) is the intended base, built on Android Launcher3 (AOSP, Apache-2.0). Lawnchair and
Launcher3 source are **not vendored** in this tree; the workspace is a Kotlin implementation of
Launcher3-style behaviour. The Island work-profile UX is credited to
[Island](https://github.com/oasisfeng/island) (GPL-3.0); no Island source is included. See
[`docs/LAWNCHAIR_REBASE.md`](docs/LAWNCHAIR_REBASE.md).

## Honesty

This is a **Lawnchair-lineage Kotlin launcher** (paged workspace,
folders, AppWidgetHost widgets, ADW/GO/Nova icon packs, swipe
gestures), **not** a vendored Lawnchair AOSP tree. Quickstep recents
and the full Lawnchair 14/15/16 import are still outstanding — see
`docs/LAWNCHAIR_REBASE.md`. Nothing here was tested on a phone.

Shizuku: this tree ships an **embed skeleton** (`shizuku-embed/`) plus a
**client of the stock Shizuku app**. The native starter, pairing UI, and
in-process server are **not** vendored. Privileged freeze/hide/force-stop
and Island shell ops are real command builders + a Shizuku `UserService`,
gated behind an explicit Settings toggle. See `shizuku-embed/README.md`.

Mods: a gated **Mods** screen for LSPosed, LSPatch, Geto, and MMRL
(all GPLv3). Full engines are **not** vendored (`mods-embed/` skeletons
+ stock-app clients). Geto is identified as JackEblan/Geto
(`com.android.geto`) at **90% confidence**. See `mods-embed/README.md`.

Share sheet targets (VictorSMS, Browser, File Manager, DroidCommand AI)
only appear for installed apps whose package+cert is pinned.

Fleet status AppWidgets (mesh/VPN, browser, messaging, health) reuse
the status-tray pull (15 s, fresh/stale/drop).

Shared fleet theme: Settings is the source of truth (light / dark /
neon / Material You). Exported read-only ContentProvider + explicit
change broadcast, gated by package+cert (signature permission is
declared but is not enough — the fleet does not share a signing key).
Launcher icons honor monochrome / tint / icon packs. See
`docs/specs/FLEET_THEME.md`.

Island: profile-owner work profile with clone / freeze / unfreeze,
offline (DPM restrictions + Quiet mode; does **not** drop packets),
and lockdown (freeze all + pause). Wipe requires an in-app confirm.

Fleet install: Settings → Install / Update loads a **local**
`fleet-manifest.json` (schema v1) and installs a user-picked APK
through PackageInstaller after cert-pin checks. Fleet install does not use the network.

Network Monitor: gated `TelephonyCallback` / `PhoneStateListener` for
RAT, signal, cell ID, and 2G-drop alerts. RAT lock needs Shizuku/root
and is **not** universal. Cell/band lock is not written. Fleet read op:
`network.status`.

## Build

```bash
./gradlew assembleDebug
```

Needs JDK 17 and the Android SDK (platform 35).

Debug builds sign with a **gitignored** `keystore/victorlaunch-debug.jks`
(CI: `VL_DEBUG_KEYSTORE_B64` + password secrets). The public cert
SHA-256 is in `keystore/README.md`. If the JKS is missing, Gradle
falls back to the default Android debug key and warns. Release signing
is environment-only (`VL_RELEASE_*`). Never commit a keystore or its
passwords. The old committed debug cert `1B:06:A8:…:BA:CA` is **REVOKED**.

CI runs `./gradlew assembleDebug` on pulls and on `main`, and uploads
the APK.

## Docs

- `docs/MANUAL.md` — user manual (also the in-app Help screen)
- `docs/fleet-manifest.md` — local fleet update manifest (schema v1)
- `docs/external-node.md` — Messenger bridge v1.1
- `docs/ROOT_MODULE.md` — optional Magisk / KernelSU / APatch module that installs VictorLaunch as a privileged system app (not required)
- `docs/LAWNCHAIR_REBASE.md` — why Lawnchair is not vendored yet
- `shizuku-embed/README.md` — Shizuku client fallback + embed plan
- `mods-embed/README.md` — LSPosed / LSPatch / Geto / MMRL skeletons + plan
- `docs/specs` live upstream in [droidcommand-AI](https://github.com/Victorious93/droidcommand-AI)
  (`STATUS_TRAY.md`, `UNIFIED_BACKUP.md`)

## Privacy

`INTERNET` is declared for GitHub home-screen widgets (`https://api.github.com`)
and for URL Tools (`https://www.virustotal.com` / `https://is.gd` on an explicit
tap, plus the user-supplied URL's hosts when you tap Expand). There is no
telemetry. Status and backup refuse secret-like fields. The GitHub token and
any VirusTotal API key are stored in EncryptedSharedPreferences and are never
part of `config.export`. VictorLaunch never holds the fleet backup wrapping key.

Notification badges use an opt-in `NotificationListenerService` (no extra
`uses-permission`). They stay off until you flip the in-app toggle and grant
notification access in system Settings. Preview text stays in memory only —
it is never logged and never included in `config.export`.
