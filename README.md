<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="assets/logo.png" alt="VictorLaunch logo" width="200">
</p>

# VictorLaunch

Nova-class, Lawnchair-based fleet home-screen launcher.

**Package id:** `com.victorious.launch`

**Status:** CI green *(from [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md))*

## Install / build

Kotlin Gradle app (`app/build.gradle.kts`). Typical: `./gradlew :app:assembleDebug`.

_Steps above are taken from this repo’s build files / existing docs; anything not exercised in this refresh is unlabeled as verified on a device._

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

Fleet-native Android home-screen launcher (`com.victorious.launch`).

VictorLaunch is the on-device hub for the Victorious / droidcommand-AI
fleet: a HOME activity with workspace, dock and app drawer, a status
tray that **only** reads the controller’s `node.status.get`, an Island
work profile, and an EXTERNAL_NODE bridge.

License: **GNU GPL v3.0**. See `LICENSE`, `NOTICE`, `THIRD_PARTY.md`.

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

Fleet status AppWidgets (mesh/VPN, browser, messaging, health) reuse
the status-tray pull (15 s, fresh/stale/drop).

Fleet install: Settings → Install / Update loads a **local**
`fleet-manifest.json` (schema v1) and installs a user-picked APK
through PackageInstaller after cert-pin checks. No INTERNET.

Network Monitor: gated `TelephonyCallback` / `PhoneStateListener` for
RAT, signal, cell ID, and 2G-drop alerts. RAT lock needs Shizuku/root
and is **not** universal. Cell/band lock is not written. Fleet read op:
`network.status`.

## Build

```bash
./gradlew assembleDebug
```

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
- `docs/LAWNCHAIR_REBASE.md` — why Lawnchair is not vendored yet
- `shizuku-embed/README.md` — Shizuku client fallback + embed plan
- `mods-embed/README.md` — LSPosed / LSPatch / Geto / MMRL skeletons + plan
- `docs/specs` live upstream in [droidcommand-AI](https://github.com/Victorious93/droidcommand-AI)
  (`STATUS_TRAY.md`, `UNIFIED_BACKUP.md`)

## Privacy

No `INTERNET` permission. No telemetry. Status and backup refuse
secret-like fields. VictorLaunch never holds the fleet backup wrapping
key.
