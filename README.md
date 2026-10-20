# VictorLaunch

Fleet-native Android home-screen launcher (`com.victorious.launch`).

VictorLaunch is the on-device hub for the Victorious / droidcommand-AI
fleet: a HOME activity with workspace, dock and app drawer, a status
tray that **only** reads the controller’s `node.status.get`, an Island
work profile, and an EXTERNAL_NODE bridge.

License: **GNU GPL v3.0**. See `LICENSE`, `NOTICE`, `THIRD_PARTY.md`.

## Honesty

This is a **minimal Launcher3-style Kotlin launcher**, not a vendored
Lawnchair tree. Lawnchair (GPLv3) remains the intended base — see
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

Network Monitor: gated `TelephonyCallback` / `PhoneStateListener` for
RAT, signal, cell ID, and 2G-drop alerts. RAT lock needs Shizuku/root
and is **not** universal. Cell/band lock is not written. Fleet read op:
`network.status`.

## Build

```bash
./gradlew assembleDebug
```

Debug builds sign with `keystore/victorlaunch-debug.jks` (passwords
and cert SHA-256 in `keystore/README.md`). Release signing is
environment-only (`VL_RELEASE_*`). Never commit a release key.

CI runs `./gradlew assembleDebug` on pulls and on `main`, and uploads
the APK.

## Docs

- `docs/MANUAL.md` — user manual (also the in-app Help screen)
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
