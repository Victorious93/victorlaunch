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
`#RRGGBB` or `#AARRGGBB`.

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

Wrong cert for a listed package is refused. Source: `FleetThemeConsumers.PINS`.

## Honesty

- **[Certain]** codec, pin checks, and schema constants have unit tests.
- **[Guessing]** ContentProvider / explicit broadcast / Material You
  system colors on a phone — not device-tested.
- GitHub Actions is billing-blocked; local `./gradlew :core:test assembleDebug`
  is the gate.
