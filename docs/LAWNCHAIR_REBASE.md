# Rebase onto Lawnchair

VictorLaunch is **GPLv3** and is intended to sit on
[Lawnchair](https://github.com/LawnchairLauncher/lawnchair)
(itself a GPLv3 Launcher3 descendant).

This repository does **not** vendor the Lawnchair AOSP tree.

## What landed (largest buildable step)

The Compose letter-tile stand-in is replaced with a **Launcher3-lineage
workspace** that implements the product surfaces Lawnchair users expect,
without importing `Android.bp`, Quickstep, hidden-api stubs, or
`systemUI` / `wmshell`:

| Surface | In this tree | Notes |
|---|---|---|
| Paged workspace | Yes | `WorkspacePages` + `HorizontalPager` |
| Dock / hotseat | Yes | Up to five pins + drawer button |
| All-apps drawer + search | Yes | `AppSearch` on label / package |
| Folders | Yes | Merge two pinned apps; folder sheet |
| Widgets | Yes | System `AppWidgetHost` + bind/configure |
| Icon packs | Yes | ADW / GO / Nova / Atom theme intents + `appfilter.xml` |
| Gestures | Yes | Swipe up = drawer, swipe down = search, horizontal = pages |
| Neon cyan→magenta ring | Yes | Existing theme + adaptive icon |
| Quickstep recents | **No** | Needs Lawnchair/AOSP recents + hidden APIs |
| Lawnchair preference overlay | **No** | Settings here are VictorLaunch screens |
| Full Lawnchair 14/15/16 tree | **No** | Multi-hour Soong/Gradle hybrid |

Package id stays `com.victorious.launch`. Bridge v1.1, status tray,
backup, Mods, Network Monitor, and controller full-control + kill
switch are unchanged.

[Certain] this tree compiles as `:core:test assembleDebug`.
[Guessing] widgets, icon packs, and gestures on a real launcher
session — nothing here was tested on a phone.

## Why not a real Lawnchair APK in one pass

Lawnchair 16 is an AOSP-sized home app: `Android.bp`, `systemUI` /
`wmshell` git submodules, hidden-api stubs, Quickstep, and a
multi-hour debug build. Vendoring it would bury the fleet work and
would not produce a green `./gradlew assembleDebug` in this repo’s
AGP 8.7 / minSdk 26 setup.

## Still left

1. Fork `LawnchairLauncher/lawnchair` at a tagged 14/15/16 release
   under Victorious93 (keep GPLv3, keep Lawnchair and AOSP
   attribution).
2. Overlay `applicationId com.victorious.launch`, the adaptive icon in
   `branding/`, and the charcoal/cyan/magenta theme as a Lawnchair
   overlay rather than a rewrite of Recents.
3. Move `:core` unchanged — it has no Android types.
4. Replace `HomeActivity` with Lawnchair’s launcher activity; keep
   `StatusStrip` as a Lawnchair overlay view / Compose view stub.
5. Keep `BridgeService`, `IslandAdminReceiver`, and the Help asset.
6. Point CI at Lawnchair’s existing assemble task plus `:core:test`.
7. Do not copy Island/Insular sources; keep the public DPM API
   wrappers.

Until that import, this tree is a **buildable Lawnchair-lineage
workspace**, not a rebranded Lawnchair APK.
