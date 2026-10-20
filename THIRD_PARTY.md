# Third-party licenses

VictorLaunch is GNU GPL v3.0. Combined work with Lawnchair (when
rebased) remains GPL v3.0.

## Intended base (not vendored)

| Project | License | Notes |
|---|---|---|
| [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) | GPL-3.0 | Planned rebase. Not copied into this tree. |
| Android Launcher3 (AOSP) | Apache-2.0 | Lawnchair’s upstream. Not copied. |

## Concept credit (no source copied)

| Project | License | What we used |
|---|---|---|
| [Island](https://github.com/oasisfeng/island) | GPL-3.0 | Managed work profile as an “Island”; clone / freeze / quiet mode as a product idea. Implemented with public `DevicePolicyManager`, `UserManager.requestQuietModeEnabled`, `LauncherApps`. |
| Insular | GPL-3.0 | Same family of work-profile launchers. No source copied. |

## Build / runtime (Apache 2.0 unless noted)

| Component | License |
|---|---|
| AndroidX Core, Activity, Lifecycle, Navigation, Compose, Biometric | Apache-2.0 |
| Kotlin stdlib / coroutines | Apache-2.0 |
| kotlinx.serialization | Apache-2.0 |
| Material icons (Compose) | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) / [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (`dev.rikka.shizuku:api`, `provider`) | Apache-2.0 (API repo also notes MIT). Client fallback; server not vendored. See `shizuku-embed/`. |

Gradle, the Android Gradle Plugin, and the JDK are tools, not
distributed inside the APK.

## Branding

The neon cyan-to-magenta ring lockup is original VictorLaunch art
generated for this repository (`branding/victorlaunch-logo-square.png`
and the adaptive icon layers).
