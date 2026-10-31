# Shared fleet theme (schemaVersion 1)

VictorLaunch (`com.victorious.launch`) is the **single source of truth**
for the fleet palette. Don picks a mode in Settings → **Fleet theme**.
Other fleet apps **read** that snapshot and re-apply it live. They do
not write it.

This contract is for other fleet bots. Implement a client; do not
re-implement a second picker.

## Authority

| Field | Value |
|---|---|
| Authority | `com.victorious.launch.theme` |
| Path | `current` |
| URI | `content://com.victorious.launch.theme/current` |
| MIME | `vnd.android.cursor.item/vnd.com.victorious.launch.theme.current` |
| Write | **None.** `insert` / `update` / `delete` throw `SecurityException`. |

Query returns **one row**.

## Permission and verification

Declared permission:

```
com.victorious.launch.permission.READ_FLEET_THEME
protectionLevel = signature
```

**Do not rely on that permission alone.** The fleet does **not** share a
signing key. A signature permission only matches same-signer callers
(VictorLaunch itself, or a future same-key build). Differently signed
fleet APKs will not be granted it.

The provider is **exported without `android:readPermission`**. Every
`query()` checks:

1. `callingPackage`
2. that package’s signing-cert SHA-256
3. pins: `FleetThemeConsumers` (theme-read only) / `TrustedPeers` /
   `FleetPins.KNOWN` / user share-target pins / extra controller pairing

Untrusted callers get `SecurityException`. VictorLaunch querying itself
is always allowed.

Write methods stay blocked. `android:writePermission` is the signature
permission so a stranger cannot even reach those methods.

## Broadcast

| Field | Value |
|---|---|
| Action | `com.victorious.launch.action.FLEET_THEME_CHANGED` |
| Delivery | **Explicit** `setPackage(...)` to pinned / known fleet packages that are installed |
| Extras | `schemaVersion` (int), `mode` (string), `iconStyle` (string), `updatedAtMs` (long) |

There is no implicit leak of the palette. After the broadcast, query the
provider again for the full JSON / columns. The extras are a wake-up,
not the whole theme.

Also register a `ContentObserver` on the URI. VictorLaunch calls
`ContentResolver.notifyChange` on every change.

## Columns

| Column | Type | Notes |
|---|---|---|
| `schemaVersion` | int | Always `1` in this revision |
| `mode` | string | `LIGHT` / `DARK` / `NEON_FLEET` / `MATERIAL_YOU` |
| `iconStyle` | string | `SYSTEM` / `MONOCHROME` / `TINTED` / `ICON_PACK` |
| `iconPackPackage` | string | Empty if none |
| `json` | string | Full snapshot (see below) |
| `updatedAtMs` | long | Epoch millis |
| `background` `surface` `primary` `secondary` `onBackground` `muted` `danger` `stale` `tile` | string | `#AARRGGBB` |

Unknown columns must be ignored. A future schema will bump
`schemaVersion` and keep old columns.

## JSON schema (version 1)

```json
{
  "schemaVersion": 1,
  "mode": "NEON_FLEET",
  "iconStyle": "SYSTEM",
  "iconPackPackage": "",
  "updatedAtMs": 0,
  "palette": {
    "background": "#FF0B0C12",
    "surface": "#FF161821",
    "primary": "#FF00E5FF",
    "secondary": "#FFE840FF",
    "onBackground": "#FFF5F7FF",
    "muted": "#FF9AA3B5",
    "danger": "#FFFF6B8A",
    "stale": "#FFFFC14D",
    "tile": "#CC1A1D2B"
  }
}
```

Reject `schemaVersion != 1` until you ship a v2 client. Hex is
`#RRGGBB` or `#AARRGGBB`. **`schemaVersion` stays `1` in this
revision.** Do not invent a v2 payload.

### Palette keys → Material3 roles

VictorLaunch applies the snapshot through `VictorLaunchTheme`
(`app/.../theme/Theme.kt`). Each palette key maps to these
`ColorScheme` roles — match this if you use Material3:

| Palette key | Material3 role(s) actually set | Also used as |
|---|---|---|
| `background` | `background` | `onPrimary`, `onSecondary`, `onError` |
| `surface` | `surface` | — |
| `primary` | `primary` | — |
| `secondary` | `secondary` | — |
| `onBackground` | `onBackground` | `onSurface` |
| `muted` | `onSurfaceVariant` | `outline` |
| `danger` | `error` | — |
| `tile` | `surfaceVariant` | — |
| `stale` | *(none)* | Freshness / widget accent only. Not a `ColorScheme` field. |

Roles VictorLaunch does not set (`tertiary`, `primaryContainer`,
`inverseSurface`, `scrim`, …) stay at the consumer’s Material3
defaults. `stale` is a fleet-only accent (status tiles / widgets);
do not invent an M3 role for it.

### Missing or invalid keys

Consumers apply **per key**. If a palette key is absent, blank, or
not valid `#RRGGBB` / `#AARRGGBB`, keep **that consumer app’s
default** for the mapped role. Do not invent a color, do not
substitute another palette key, and do not silently paint neon
unless neon is already your app default.

A missing key must not fail the whole snapshot. Valid keys still
apply.

### Modes

| Mode | Meaning |
|---|---|
| `NEON_FLEET` | Default charcoal + cyan/magenta ring |
| `DARK` | Neutral dark + cooler accents |
| `LIGHT` | Light surfaces, darker cyan/magenta for contrast |
| `MATERIAL_YOU` | System accent1/accent2/neutral on API 31+; **neon fallback** below 12 |

### Icon styles (launcher-side)

Other apps may ignore `iconStyle`. VictorLaunch applies it to third-party
icons on the workspace, dock, folders, and drawer:

- `SYSTEM` — raw launcher icon
- `MONOCHROME` — AdaptiveIcon `monochrome` layer on API 33+, else desaturate + tint
- `TINTED` — accent `SRC_ATOP`
- `ICON_PACK` — ADW / GO / Nova / Atom `appfilter.xml` (then the same
  tint rules do not apply unless the user later picks mono/tint)

## Live-update guidance (other fleet apps)

1. On start, `query(content://com.victorious.launch.theme/current)`.
2. Register a `ContentObserver` on that URI.
3. Register a receiver for `com.victorious.launch.action.FLEET_THEME_CHANGED`
   (exported, because the send is explicit to your package).
4. On either callback, query again and apply `palette.*` to your theme.
5. If query throws `SecurityException`, your package+cert is not pinned.
   Ask Don to **Trust installed fleet share apps** or add a pin. Do not
   invent colors.
6. Never write the provider. Never treat a payload field as auth.

## VictorLaunch publisher pins

Other fleet apps that **verify the provider** (that the process
serving `content://com.victorious.launch.theme/current` is actually
VictorLaunch) pin **this** package + cert. These are not
`FleetThemeConsumers` entries — they identify the source.

| Package | Status | SHA-256 |
|---|---|---|
| `com.victorious.launch` | **current** (debug) | `B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69` |
| `com.victorious.launch` | **next** | none published |
| `com.victorious.launch` | **REVOKED** | `1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA` |
| `com.victorious.launch` | **release** | **TBD, no release key exists yet.** Do not invent a SHA-256. |

Source of the debug SHA-256: `keystore/README.md`. The JKS is not in
the tree. History is not rewritten; the revoked cert was the old
committed debug keystore.

### Cert rotation

Consumers that pin VictorLaunch accept **current and next**
(overlap while a rotation is in flight) and **reject the revoked
list**. A blank / TBD / unpublished `next` or `release` slot does
**not** authorize. Until a next cert is published, only **current**
is valid.

## Pinned theme consumers

These package+cert pairs may **query the theme provider** and receive
the explicit change broadcast. They do **not** get bridge pairing,
fleet-install trust (unless already listed in `FleetPins.KNOWN` for
another reason), or remote-control ops (`app.launch` / `layout.set` /
`panel.open`). A package may have more than one allowed cert.

| Package | Cert SHA-256 | Notes |
|---|---|---|
| `ai.hackerai.companion` | `AF:1C:8F:C5:44:9F:58:4A:46:C8:96:CC:C9:7F:35:B0:7B:35:7E:96:EE:66:5F:3A:BF:CB:0D:28:4D:F5:23:55` | Theme-read only |
| `ai.hackerai.companion` | `9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D` | Release signer. Also a `FleetPins.KNOWN` install pin (pre-existing). Still **not** a controller. |
| `com.victorious.mesh` | `F4:C9:25:B8:E8:0B:55:AB:83:E6:04:13:EE:49:86:57:DF:85:03:BB:30:D8:1F:9F:17:43:F2:2B:F5:36:B9:F4` | Theme-read only |
| `com.victorious.mango` | `08:02:D9:4C:BB:CB:C3:F8:0C:9D:2F:C5:30:57:9E:5B:BD:E2:26:AC:7C:40:F4:52:31:0C:A1:0A:DA:EC:FE:55` | Theme-read only |
| `com.victorious.victoryped` | `63:AA:41:BB:C5:0A:06:27:A0:4F:55:96:F1:11:12:E0:36:E4:51:B5:9F:4C:26:0F:AB:FB:92:E1:78:D2:F0:79` | Theme-read only |
| `com.victorious.taskbar` | `4F:58:A5:C5:75:93:5E:10:3B:1F:73:C0:7A:D2:E0:0A:FF:64:57:76:27:71:1B:77:96:79:37:D5:FC:3B:C2:92` | Theme-read only |
| `ai.rootforge.os.debug` | `77:7A:C7:00:87:05:A8:B4:B8:93:AC:EF:0E:03:E9:1C:7F:49:52:C9:52:FE:B2:6E:06:D7:90:B9:14:86:64:6F` | Theme-read only. The `.debug` suffix is the real applicationId; `ai.rootforge.os` is **not** pinned. |
| `com.identityshield.companion` | `22:4F:C1:64:D6:8A:28:88:B9:FE:8F:10:C7:B6:B5:E5:78:67:2B:D2:F8:97:05:06:68:4B:23:A9:43:FA:44:1D` | Theme-read only |
| `com.victorious.keyboard.debug` | `47:1E:AD:6B:3F:96:7E:F3:C8:63:EF:49:08:74:8B:3B:E3:63:F9:1D:75:0B:FD:B1:E9:28:05:BC:F1:6B:AF:4F` | Theme-read only. The `.debug` suffix is the real applicationId; `com.victorious.keyboard` is **not** pinned. |
| `ai.droidcommand.app` | `88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D` | Theme-read entry matching the existing controller debug pin. Bridge pairing, full-control, and the kill switch are **unchanged**. |
| `com.victorious.spoof` | `A4:84:62:2B:7E:86:E1:D9:F8:04:56:6E:B7:89:2D:EE:3C:64:EB:8E:B9:13:9E:04:25:BC:65:F3:EF:D0:FF:EE` | Theme-read only |
| `ai.pentestswarm.companion` | `5E:3A:B1:23:1F:7E:DA:18:78:B8:FF:D8:9E:AC:B1:F7:2E:EB:66:AF:3D:BA:2E:01:33:51:24:68:42:23:30:C7` | Theme-read only |
| `ai.edlcommander.companion` | `E5:60:B6:92:BD:E6:51:9E:B7:CB:B5:2C:E4:DC:91:5A:19:E4:41:A0:1A:5D:03:68:82:71:AC:99:78:8A:58:C8` | Theme-read only |
| `ai.victorious.filemanager` | `8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2` | Theme-read only. Same signer as `.debug`. |
| `ai.victorious.filemanager.debug` | `8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2` | Theme-read only. Same signer as the release applicationId. |
| `com.victorious.cam` | `8B:56:F8:18:93:B2:4F:02:BC:6C:93:39:39:36:32:3E:C5:74:86:D9:34:E2:9B:73:7C:E1:A7:B4:24:C5:B6:0D` | Theme-read only |
| `com.termux` | `67:17:C5:5A:BE:9E:0D:F8:C6:03:C9:A0:F3:F3:67:B6:CA:BD:B0:1F:3E:58:3A:4F:F6:44:E5:DF:3D:4C:31:D3` | Theme-read only. VictorSuite signer. An upstream / F-Droid `com.termux` cert is **refused**. |
| `com.victorious.sms` | `BA:81:F5:C7:6E:2C:59:04:E8:A0:E5:0C:80:67:A2:A1:6C:23:14:86:2F:13:DA:7F:49:67:94:8A:53:E7:D1:7A` | Theme-read only (VictorSMS debug). Another cert on this package is **refused**. |

Wrong cert for a listed package is refused. Source: `FleetThemeConsumers.PINS`.

## Honesty

- **[Certain]** codec, pin checks, and schema constants have unit tests.
- **[Guessing]** ContentProvider / explicit broadcast / Material You
  system colors on a phone — not device-tested.
- GitHub Actions **Android CI** is running and completing successfully
  on recent `main` pushes and PRs (checked 2026-10-27). Local
  `./gradlew :core:test assembleDebug` remains the agent merge gate.
