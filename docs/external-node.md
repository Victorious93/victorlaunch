# EXTERNAL_NODE bridge (VictorLaunch)

Status: **documented contract**. The inbound Messenger service and the
JVM dispatcher exist in this tree so `node.status.get` /
`capabilities.get` / `config.export|import` can be real. The debug
controller is **pinned** in `TrustedPeers` so a matching
`ai.droidcommand.app` install can bind without a Settings tap. Live
traffic against a running controller is **unverified**.

Aligned with droidcommand-AI `docs/specs/REMOTE_CONTROL.md` as frozen
in PRs **#233** and **#234** (envelope v1.1).

---

## Transport

- Binding: Android `Messenger` (newline-delimited JSON in the message
  bundle).
- Action: `com.victorious.launch.action.BRIDGE`
- `Message.what`: `1` request, `2` response
- Bundle keys: `request` / `response` (UTF-8 JSON)
- `protocol_major` 1. Minor **1** is advertised in `capabilities.get`.
  Do not require `protocol_minor` on the request.

Authorization is **package + signing certificate** of
`Message.sendingUid`. Never a phone number, keyword, or payload field.

VictorLaunch’s **own** debug APK cert (what other fleet nodes pin for
`com.victorious.launch`) is published as a SHA-256 only — see
`keystore/README.md`. The keystore and passwords are not in this tree.
The previously committed debug cert
`1B:06:A8:9E:7F:F5:BA:21:C9:A4:F8:F1:F0:4F:95:E5:62:78:A1:22:18:AD:37:33:28:CE:E7:83:1D:4F:BA:CA`
is **REVOKED**. Git history still contains that JKS; do not trust it.

### Pinned controller (trusted-peer allowlist)

The outbound status pull binds `ai.droidcommand.app` /
`ai.droidcommand.app.action.BRIDGE` only when that APK’s signing cert
matches a pinned row (or an extra Settings pairing row). Inbound
bridge calls use the same allowlist.

| Package | Kind | SHA-256 |
|---|---|---|
| `ai.droidcommand.app` | debug | `88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D` |
| `ai.droidcommand.app` | release | **TBD** — slot reserved; a blank/TBD value does **not** authorize |

Comparison is colon-insensitive and case-insensitive.

### Don’s choice: pinned controller has full control

This **deviates** from the fleet default that destructive ops stay
gated. Don explicitly chose that the **pinned debug controller only**
(`ai.droidcommand.app` + the debug cert above) has **full control** of
VictorLaunch:

- External control is **ON by default** for that caller.
- `app.launch`, `layout.set`, and `panel.open` run with **no**
  on-device confirmation (`layout.set` included).
- `capabilities.get` for that caller reports `remoteAllowed: true` and
  `requiresConfirm: false` for those three ops.

Every **other** peer keeps the old defaults: external control **OFF**,
and `layout.set` still `requiresConfirm: true` even if the generic
“other peers” switch is later turned on.

A user-visible **kill switch** in Settings (“Allow controller full
control”) revokes the privilege. Package+cert authorization,
replay/nonce/ttl, and an audit log of every remote action still apply.
`config.import` / launcher backup **cannot** turn this on (or turn on
generic external control for non-controller peers). `config.export` /
`config.import` stay `remoteAllowed: false` even for the controller.

---

## Envelope v1.1 (flat fields)

CamelCase inside the envelope. If the caller wraps a BridgeRequest, the
same names live under `parameters` and `requestId` **is** the outer
`request_id`.

| Field | Type | Notes |
|---|---|---|
| `op` | string | Operation name |
| `requestId` | string | Correlate the response |
| `targetNodeRef` | string | This node’s id, `com.victorious.launch`, or (when we are the client) `controller` |
| `actionRef` | string | Optional; ignored for standalone ops |
| `params` | object | Default `{}`. Unknown keys ignored. `args` is accepted as an alias. |
| `nonce` | string | 128-bit unique (32 hex chars, or UUID) |
| `timestampMs` | number | Epoch ms |
| `ttlMs` | number | Default `120000`, max `300000` |
| `destructive` | bool | Advisory only |
| `trigger` | object | Optional, informational |

Example (NDJSON-style object):

```json
{
  "op": "capabilities.get",
  "requestId": "6f1a9c2e-3b84-4d91-9e0a-11a0c84d0001",
  "targetNodeRef": "victorlaunch-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "actionRef": "",
  "params": {},
  "nonce": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "timestampMs": 1760920000000,
  "ttlMs": 120000,
  "destructive": false
}
```

When VictorLaunch **pulls the controller** it sends the controller’s
outer BridgeRequest (`operation`, snake_case wrapper) with the frozen
top-level fields inside `parameters` (`args`, not `params`).

---

## Replay protection

Keyed by `(sourceNodeRef, nonce)` where `sourceNodeRef` is the caller
package list from `sendingUid`, not a payload field. Cache retention ≥
10 minutes.

Reject:

| Condition | Error |
|---|---|
| `now > timestampMs + ttlMs` | `expired` |
| `timestampMs > now + 120000` | `expired` (future skew only) |
| seen `(sourceNodeRef, nonce)` | `replay_detected` |

Past validity is TTL-only (PR #234). A request with
`timestampMs = now - 200000` and `ttlMs = 300000` is still valid.

---

## `capabilities.get`

Returns `protocolMajor: 1`, `protocolMinor: 1`, and per-op
`{ name, kind, remoteAllowed, requiresConfirm }`.

| Op | kind | remoteAllowed (default) | requiresConfirm |
|---|---|---|---|
| `capabilities.get` | read | true | false |
| `node.status.get` | read | true | false |
| `network.status` | read | true | false |
| `fleet.list` | read | true | false |
| `layout.get` | read | true | false |
| `installed.list` | read | true | false |
| `running.list` | read | true | false |
| `config.export` | action | **false** | false |
| `config.import` | action | **false** | **true** |
| `app.launch` | action | false (true for pinned controller) | false |
| `layout.set` | action | false (true for pinned controller) | **true** (false for pinned controller) |
| `panel.open` | action | false (true for pinned controller) | false |

---

## Read ops

- `capabilities.get` — table above.
- `fleet.list` — pairing rows this launcher knows (may be empty).
- `layout.get` — dock / workspace / grid. Real prefs, not samples.
- `installed.list` — `LauncherApps` across profiles.
- `running.list` — usage stats. Without `PACKAGE_USAGE_STATS` granted:
  error category `permission`. If the API is missing: `not_available`.
- `node.status.get` — this process (`node_health`, plus `network` when
  the Network Monitor is enabled). Never a scrape of Mesh/VPN/SMS.
- `network.status` — on-device radio JSON (RAT, RSRP/RSRQ/SINR, cell
  ID, alerts). `enabled: false` when the monitor is off. Limits string
  is always present. Not a lock confirmation.

---

## Action ops

For the **pinned debug controller** (kill switch ON, Don’s choice):
`app.launch`, `layout.set`, `panel.open` run immediately. No confirm
sheet. This is a known deviation from “destructive ops always gated”.

For **every other peer**: those ops run **only when** the generic
external-control switch is ON. It is **OFF by default**. Otherwise
`external_control_disabled`. `layout.set` also needs on-device
confirmation (`confirmation_required` without it).

Every remote action (including denials) is appended to an in-process
audit log and `Log.i("VictorLaunchAudit", …)`.

`config.export` / `config.import` cover **launcher preferences only**.
A remote caller — **including the controller** — gets `capability_off`
(`remoteAllowed: false`). `config.import` supports `params.dryRun` /
envelope `dryRun`. After any successful import, `externalControlEnabled`
is forced **false** and any `controllerFullControl` flag in the file is
ignored. VictorLaunch may ask the controller to export/import the fleet
bundle but does not wrap or unwrap that file.

---

## Errors

`invalid_parameters`, `not_paired`, `not_found`, `internal`,
`unauthorized`, `replay_detected`, `expired`,
`external_control_disabled`, `confirmation_required`,
`unsupported_op`, `capability_off`, plus `permission` /
`not_available` for `running.list`.

`targetNodeRef` blank → `invalid_parameters`. Unknown id →
`not_found`. Literal `controller` is used when **we** call the
controller, not as a name for this node.

---

## What is not in this document

Live privileged execution, Kali/pentest gates (this app has none),
holding a backup wrapping key, scraping peer nodes, or a claim that
the bridge was tested on a device.
