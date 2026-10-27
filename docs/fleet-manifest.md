# Fleet update manifest (VictorLaunch client)

**Status:** schemaVersion **1** is published by
[Victorious93/fleet-manifest](https://github.com/Victorious93/fleet-manifest)
(`schema.json`, live feed `fleet-manifest.json`). This is **not**
provisional. VictorLaunch still cannot fetch it itself.

## Why local files

VictorLaunch has **no `INTERNET` permission**. It will not poll
GitHub Pages or `raw.githubusercontent.com`. A user (or the
controller, later) must hand it:

1. a `fleet-manifest.json` (Open Document)
2. the APK bytes for the row they want to install (Open Document)

Install always goes through `PackageInstaller` and the **system
confirm sheet**.

## Schema (v1)

Required top-level: `schemaVersion` (const 1), `generatedAt`, `apps[]`.

Per app: `id`, `name`, `repo`, `applicationId`, `versionName`,
`versionCode`, `debugBuild`, `releaseTag`, `releaseNotesUrl`,
`publishedAt`, `certSha256` (colon-hex or JSON null), `assets[]`
(`abi`, `url`, `sizeBytes`, `sha256` lowercase hex).

## Trust

An entry is trusted only when `certSha256` is non-null **and** that
digest is on the pin allow-list for that `applicationId`:

| Package | Pin |
|---|---|
| `ai.droidcommand.app` | controller debug `88:00:51:…:A0:C8:4D` |
| `com.victorious.launch` | VictorLaunch debug `B9:9A:AF:…:4A:69` |
| `ai.hackerai.companion` | BOUND_COMPANION release `9E:A7:B3:…:2D:8D` |
| `ai.pentestswarm.companion` | same BOUND_COMPANION release |
| `ai.edlcommander.companion` | same BOUND_COMPANION release |

Empty `pinnedCertSha256` in `apps.yml` means **do not invent a pin**.
Those rows show as **UNTRUSTED** and the Install button stays off.

The selected APK’s signer must match the row’s `certSha256`. If the
row lists an asset SHA-256, the file digest must match too.

## Live feed (out of process)

https://victorious93.github.io/fleet-manifest/fleet-manifest.json

Copy that file onto the phone (USB, Files, controller) and load it
from Settings → Install / Update fleet apps.
