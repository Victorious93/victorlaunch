# Debug signing

`victorlaunch-debug.jks` is a dedicated **debug-only** key for
VictorLaunch. Debug builds (local and CI) sign with it. Release
signing does **not** use this file.

| Field | Value |
|---|---|
| File | `keystore/victorlaunch-debug.jks` |
| Type | JKS |
| Alias | `victorlaunchdebug` |
| Store password | `android` |
| Key password | `android` |
| Certificate SHA-256 | `1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA` |

The password is the well-known Android debug password. This key is
not secret. Fleet pairing uses the **certificate**, not the password.

## Release signing

Release builds read **only** from the environment / CI secrets. No
release keystore is committed.

| Variable | Meaning |
|---|---|
| `VL_RELEASE_STORE_FILE` | Path to the release keystore (must be present to create the `release` signing config) |
| `VL_RELEASE_STORE_PASSWORD` | Store password |
| `VL_RELEASE_KEY_ALIAS` | Key alias |
| `VL_RELEASE_KEY_PASSWORD` | Key password |

Phones that already have a VictorLaunch debug build signed with a
different key need one uninstall before this APK will install.
