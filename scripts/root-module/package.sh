#!/usr/bin/env bash
# Package the optional VictorLaunch root module around an already-built APK.
#
#   scripts/root-module/package.sh <path-to-apk> [output-dir]
#
# Needs: aapt2 (Android build-tools; set AAPT2 or ANDROID_HOME), zip, unzip.
# Fails (non-zero) when the APK and the module disagree, because a priv-app that
# requests a privileged permission missing from the allowlist can bootloop.
set -euo pipefail

APK=${1:?usage: package.sh <apk> [output-dir]}
OUT_DIR=${2:-build/root-module}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
MODULE_DIR="$ROOT/module"
ALLOWLIST_XML="$MODULE_DIR/system/etc/permissions/privapp-permissions-com.victorious.launch.xml"
REVIEWED="$ROOT/scripts/root-module/reviewed-permissions.txt"
PKG=com.victorious.launch

fail() { echo "ERROR: $*" >&2; exit 1; }

find_aapt2() {
  if [ -n "${AAPT2:-}" ]; then echo "$AAPT2"; return; fi
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  [ -n "$sdk" ] || fail "set AAPT2 or ANDROID_HOME"
  find "$sdk/build-tools" -maxdepth 2 -name aapt2 -type f 2>/dev/null | sort -V | tail -n1
}

[ -f "$APK" ] || fail "APK not found: $APK"
AAPT2=$(find_aapt2)
[ -x "$AAPT2" ] || fail "aapt2 not found (looked for: ${AAPT2:-none})"

prop() { sed -n "s/^$1=//p" "$MODULE_DIR/module.prop" | head -n1; }
MOD_ID=$(prop id)
MOD_VERSION=$(prop version)
MOD_CODE=$(prop versionCode)
[ "$MOD_ID" = victorlaunch ] || fail "module.prop id must be victorlaunch (got '$MOD_ID')"
[ -n "$MOD_VERSION" ] || fail "module.prop needs version"
[ -n "$MOD_CODE" ] || fail "module.prop needs versionCode"

BADGING=$("$AAPT2" dump badging "$APK")
read -r APK_PKG APK_CODE APK_NAME < <(
  sed -n "s/^package: name='\([^']*\)' versionCode='\([^']*\)' versionName='\([^']*\)'.*/\1 \2 \3/p" <<<"$BADGING" | head -n1
)
[ "$APK_PKG" = "$PKG" ] || fail "APK package is '$APK_PKG', expected $PKG"
[ "$APK_NAME" = "$MOD_VERSION" ] || fail "module.prop version '$MOD_VERSION' != APK versionName '$APK_NAME'; update module/module.prop"
[ "$APK_CODE" = "$MOD_CODE" ] || fail "module.prop versionCode '$MOD_CODE' != APK versionCode '$APK_CODE'; update module/module.prop"

mapfile -t REQUESTED < <(
  "$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission[a-z0-9-]*: name='\([^']*\)'.*/\1/p" | sort -u
)
mapfile -t ALLOWED < <(sed -n 's/.*<permission name="\([^"]*\)".*/\1/p' "$ALLOWLIST_XML" | sort -u)
mapfile -t REVIEWED_LIST < <(grep -v '^[[:space:]]*#' "$REVIEWED" | grep -v '^[[:space:]]*$' | sort -u)
[ "${#REQUESTED[@]}" -gt 0 ] || fail "aapt2 reported no requested permissions; refusing to continue"

in_list() { local needle=$1; shift; local x; for x in "$@"; do [ "$x" = "$needle" ] && return 0; done; return 1; }

status=0
for perm in "${ALLOWED[@]}"; do
  in_list "$perm" "${REQUESTED[@]}" || { echo "ERROR: allowlist grants $perm but the APK does not request it" >&2; status=1; }
done
for perm in "${REQUESTED[@]}"; do
  case "$perm" in
    android.permission.*)
      in_list "$perm" "${ALLOWED[@]}" || in_list "$perm" "${REVIEWED_LIST[@]}" || {
        echo "ERROR: APK requests unreviewed $perm: add it to the allowlist XML if it is signature|privileged, else to scripts/root-module/reviewed-permissions.txt" >&2
        status=1
      }
      ;;
    *) ;;
  esac
done
[ "$status" -eq 0 ] || fail "permission allowlist does not match the APK"

echo "Package: $APK_PKG $APK_NAME ($APK_CODE)"
echo "Requested permissions: ${#REQUESTED[@]}; allowlisted privileged: ${ALLOWED[*]}"

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
cp -a "$MODULE_DIR/." "$STAGE/"
mkdir -p "$STAGE/system/priv-app/VictorLaunch"
cp "$APK" "$STAGE/system/priv-app/VictorLaunch/VictorLaunch.apk"

mkdir -p "$OUT_DIR"
ZIP="$(cd "$OUT_DIR" && pwd)/victorlaunch-root-module-$MOD_VERSION.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -q -r -X "$ZIP" META-INF module.prop customize.sh uninstall.sh system)
echo "Wrote $ZIP"
