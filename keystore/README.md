# Debug signing

VictorLaunch debug APKs are signed with a **dedicated debug key** that
is **not** in this repository. Only the certificate SHA-256 is public.

| Field | Value |
|---|---|
| Local file (gitignored) | `keystore/victorlaunch-debug.jks` |
| Type | PKCS12 |
| Alias | `victorlaunchdebug` |
| Certificate SHA-256 | `B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69` |

Passwords and the keystore bytes are **not** committed. They live in
GitHub Actions secrets:

| Secret | Meaning |
|---|---|
| `VL_DEBUG_KEYSTORE_B64` | Base64 of `victorlaunch-debug.jks` |
| `VL_DEBUG_STORE_PASSWORD` | Store password |
| `VL_DEBUG_KEY_ALIAS` | Key alias (`victorlaunchdebug`) |
| `VL_DEBUG_KEY_PASSWORD` | Key password |

## Local builds

1. Place `keystore/victorlaunch-debug.jks` (gitignored).
2. Export `VL_DEBUG_STORE_PASSWORD`, `VL_DEBUG_KEY_ALIAS`, and
   `VL_DEBUG_KEY_PASSWORD`.
3. `./gradlew assembleDebug`

If the JKS is missing, Gradle signs with the **default Android debug
keystore** (`~/.android/debug.keystore`) and prints a warning. That
APK will **not** match the published debug cert. Fleet nodes that pin
the SHA-256 above will refuse it.

## CI

The workflow decodes `VL_DEBUG_KEYSTORE_B64` into the gitignored JKS
and passes the password secrets into `assembleDebug`.

## Revoked debug certificates

History is **not** rewritten. These certs were in the tree when the
repo was private and must not be trusted:

| SHA-256 | Status |
|---|---|
| `1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA` | **REVOKED** (old committed `keystore/victorlaunch-debug.jks`, store/key password was the well-known `android`) |

Uninstall any APK signed with a revoked cert before installing a build
signed with the current debug cert.

## Release signing

Release builds read **only** from the environment / CI secrets. No
release keystore is committed.

| Variable | Meaning |
|---|---|
| `VL_RELEASE_STORE_FILE` | Path to the release keystore (must be present to create the `release` signing config) |
| `VL_RELEASE_STORE_PASSWORD` | Store password |
| `VL_RELEASE_KEY_ALIAS` | Key alias |
| `VL_RELEASE_KEY_PASSWORD` | Key password |
