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

### Pinned controller (trusted-peer allowlist)

The outbound status pull binds `ai.droidcommand.app` /
`ai.droidcommand.app.action.BRIDGE` only when that APK’s signing cert
matches a pinned row (or an extra Settings pairing row). Inbound
bridge calls use the same allowlist.

| Package | Kind | SHA-256 |
|---|---|---|
| `ai.droidcommand.app` | debug | `88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D` |
| `ai.droidcommand.app` | release | **TBD** — slot reserved; a blank/TBD value does **not** authorize |

Comparison is colon-insensitive and case-insensitive. Pinning a
controller does **not** turn on action ops (`app.launch`,
`layout.set`, `panel.open` stay `remoteAllowed: false` until the
on-device external-control switch is flipped).

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
| `fleet.list` | read | true | false |
| `layout.get` | read | true | false |
| `installed.list` | read | true | false |
| `running.list` | read | true | false |
| `config.export` | action | **false** | false |
| `config.import` | action | **false** | **true** |
| `app.launch` | action | false | false |
| `layout.set` | action | false | **true** |
| `panel.open` | action | false | false |

---

## Read ops

- `capabilities.get` — table above.
- `fleet.list` — pairing rows this launcher knows (may be empty).
- `layout.get` — dock / workspace / grid. Real prefs, not samples.
- `installed.list` — `LauncherApps` across profiles.
- `running.list` — usage stats. Without `PACKAGE_USAGE_STATS` granted:
  error category `permission`. If the API is missing: `not_available`.
- `node.status.get` — this process only (`node_health`). Never a scrape
  of Mesh/VPN/SMS.

---

## Action ops

`app.launch`, `layout.set`, `panel.open` run **only when external
control is ON**. It is **OFF by default**. Otherwise
`external_control_disabled`.

`layout.set` also needs on-device confirmation
(`confirmation_required` without it).

`config.export` / `config.import` cover **launcher preferences only**.
A remote caller gets `capability_off` (`remoteAllowed: false`).
`config.import` supports `params.dryRun` / envelope `dryRun`. After
any successful import, `externalControlEnabled` is forced **false**.
VictorLaunch may ask the controller to export/import the fleet bundle
but does not wrap or unwrap that file.

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
