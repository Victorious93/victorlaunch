# Rebase onto Lawnchair

VictorLaunch is **GPLv3** and is intended to sit on
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair)
(itself a GPLv3 Launcher3 descendant).

This repository does **not** vendor Lawnchair.

## Why not a real fork in this tree

Lawnchair 16 is an AOSP-sized home app: `Android.bp`, `systemUI` /
`wmshell` git submodules, hidden-api stubs, Quickstep, and a
multi-hour debug build. GitHub Actions `./gradlew assembleDebug` on
that tree is not a reasonable CI job for a new empty private repo, and
it would bury the fleet work (status tray, Island, bridge, backup)
under a first-time AOSP import.

The code here is a **minimal Launcher3-style Kotlin + Compose
launcher**: `HOME`/`DEFAULT` activity, workspace, dock, app drawer,
work-profile Island tab. Enough to ship `applicationId
com.victorious.launch`, a published debug-cert SHA-256, and the fleet
contracts.

## Plan

1. Fork `LawnchairLauncher/lawnchair` at a tagged 14/15/16 release
   under Victorious93 (keep GPLv3, keep Lawnchair and AOSP
   attribution).
2. Overlay `applicationId com.victorious.launch`, the adaptive icon in
   `branding/`, and the charcoal/cyan/magenta theme as a Lawnchair
   overlay rather than a rewrite of Recents.
3. Move `:core` (envelope, replay, status tray, backup) unchanged —
   it has no Android types.
4. Replace `HomeActivity` with Lawnchair’s launcher activity; keep
   `StatusStrip` as a Lawnchair overlay view / Compose view stub.
5. Keep `BridgeService`, `IslandAdminReceiver`, and the Help asset.
6. Point CI at Lawnchair’s existing assemble task plus
   `:core:test`.
7. Do not copy Island/Insular sources; keep the public DPM API
   wrappers.

Until that rebase, this tree is the runnable stand-in, not a
rebranded Lawnchair APK.
