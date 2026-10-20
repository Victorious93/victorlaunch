# shizuku-embed

Apache-2.0. Upstream: [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku).

## What this PR actually ships

- **Skeleton** for a built-in Shizuku server/manager (`starter/`,
  `server/`, `manager/` are placeholders — see Plan).
- **Client fallback** against the **stock Shizuku app**
  (`moe.shizuku.privileged.api`) via `dev.rikka.shizuku:api` +
  `provider`.
- **Gated** privileged ops: freeze/hide, force-stop, grant usage
  access, Island profile create/quiet-mode via a Shizuku
  `UserService` running as ADB (uid 2000) or root (uid 0).
- Startup **instructions** for wireless debugging (Android 11+),
  `adb shell`, and root. Wireless pairing *code entry* for an
  embedded server is not implemented here.

Nothing in this module was run against a device.

## Why the full fork is not in this PR

Upstream Shizuku is its own app: native `starter`, `server` as
`app_process`, manager UI for wireless pairing, git submodules
(`api`), hidden-API stubs, and a separate `applicationId`. Embedding
that tree into `com.victorious.launch` also has to obey Apache 2.0
§6 (no Shizuku name, no `moe.shizuku.privileged.api`, no their
launcher icons). Doing that rebase plus keeping CI at
`assembleDebug` is a follow-up, not this first launcher PR.

## Plan (next PR)

1. `git subtree` (or submodule) `RikkaApps/Shizuku` at a tagged
   release into `shizuku-embed/upstream/`.
2. Rename manager `applicationId` — **do not** keep
   `moe.shizuku.privileged.api`. Serve the binder from
   `com.victorious.launch` so the client in this module talks to
   ourselves.
3. Keep their Apache LICENSE + this NOTICE. Drop their mipmaps;
   VictorLaunch's adaptive icon stays.
4. Wire the three starters for real:
   - **Wireless debugging (API 30+):** AdbMdns + pairing code
     (port from `manager` pairing activity).
   - **adb command:** write `start.sh` into app storage and show
     `adb shell sh <path>`.
   - **Root:** `su -c` the starter binary.
5. Point CI at `:shizuku-embed:assembleDebug` as well if native
   code is pulled in (NDK).

Until then, install stock Shizuku, enable the toggle in
VictorLaunch Settings, grant the Shizuku permission prompt.

## Real vs stubbed in this module

| Piece | Status |
|---|---|
| Apache LICENSE + NOTICE, no Shizuku name/icons/`applicationId` | Real |
| `:shizuku-embed` Gradle module + `ShizukuProvider` in the app | Real |
| Client against stock Shizuku (`dev.rikka.shizuku:api` 13.1.5) | Real |
| Gate: Settings toggle, confirm dialog, backup cannot enable | Real |
| Startup mode *docs* + open wireless-debug / stock manager | Real |
| `PrivilegedCommands` (`pm suspend`, `disable-user`, `am force-stop`, `appops GET_USAGE_STATS`, `pm create-user --managed`, `install-existing`) | Real (unit-tested strings) |
| `PrivilegedUserService` running those commands as uid 2000/0 | Real code, **not device-tested** |
| Native starter binary, AdbMdns pairing sheet, in-process server | **Stub** (`starter/`, `server/`, `manager/` READMEs; `EmbeddedShizukuServer.isEmbedded() == false`) |
