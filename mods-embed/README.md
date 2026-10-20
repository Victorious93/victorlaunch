# mods-embed

GPLv3. Upstream:

| Tool | Repo | Stock package |
|---|---|---|
| LSPosed | github.com/LSPosed/LSPosed | `org.lsposed.manager` |
| LSPatch | github.com/LSPosed/LSPatch | `org.lsposed.lspatch` |
| Geto (90% confidence; Don's spelling) | github.com/JackEblan/Geto | `com.android.geto` |
| MMRL | github.com/MMRLApp/MMRL | `com.dergoogler.mmrl` |

## What this PR actually ships

- **Skeletons** under `lsposed/`, `lspatch/`, `geto/`, `mmrl/`.
- **Stock-app client**: detect install, open the manager, Shizuku helpers
  (`pm grant`, `pm path`, `ls /data/adb/modules`).
- **Gated** Mods screen (master + per-tool, default off).
- LSPatch **does not rewrite APKs** here. Confirm-to-install is wired
  as a PackageInstaller hand-off once a patched file exists.
- MMRL **does not download repos** (no `INTERNET`). Non-root browse is
  a static name list plus a hand-off.

Nothing in this module was run against a device.

## Why the full forks are not in this PR

LSPosed is a Zygisk/Riru native module plus a manager. LSPatch is a
Java rewriter with native libs and a separate `applicationId`. Geto
and MMRL are full Compose apps (MMRL also wants network). Embedding
them while keeping CI at `assembleDebug` and no `INTERNET` is a
follow-up.

## Plan (later PR)

1. `git subtree` tagged releases into each skeleton dir.
2. Keep GPLv3 LICENSE + this NOTICE. Do not reuse their launcher icons
   as VictorLaunch’s icon.
3. LSPosed: detect `/data/adb/modules/*lsposed*`, bind manager for
   module/scope lists.
4. LSPatch: run `lspatch.jar` in-process or via app_process; write the
   patched APK; `ACTION_INSTALL_PACKAGE` with user confirm.
5. Geto: reuse its secure-settings templates or keep handing off after
   `WRITE_SECURE_SETTINGS`.
6. MMRL: optional local `repo.json` cache; still no silent GitHub poll
   without an explicit user-approved network path.

Until then, install the stock apps, enable Mods, grant Shizuku if you
want the non-root grants.

## Real vs stubbed

| Piece | Status |
|---|---|
| LICENSE + NOTICE | Real |
| Gate + catalog + command builders | Real (unit-tested) |
| Detect/open stock managers | Real code, **not device-tested** |
| Shizuku `pm grant` / `pm path` / `ls /data/adb/modules` | Real builders |
| PackageInstaller confirm-to-install for a user-picked patched APK | Real code, **not device-tested** |
| Magisk / KernelSU / APatch zip install command builders | Real (unit-tested); run only after confirm |
| Zygisk LSPosed in-process, module/scope API | **Stub** |
| `lspatch.jar` rewrite | **Stub** |
| Geto settings engine | **Stub** (grant + hand-off) |
| MMRL repo download / WebUI | **Stub** (static list + hand-off) |
