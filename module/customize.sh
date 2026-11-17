#!/system/bin/sh
# shellcheck shell=sh
# shellcheck disable=SC2034,SC2154
# Sourced by the Magisk, KernelSU or APatch module installer (busybox ash).
# MODPATH, ZIPFILE, MAGISK_VER_CODE, KSU, KSU_VER_CODE, APATCH, APATCH_VER_CODE,
# ui_print, abort and set_perm* are provided by the installer environment.

PKG="com.victorious.launch"
STATE_DIR="/data/adb/victorlaunch"
APK_DIR="$MODPATH/system/priv-app/VictorLaunch"
APK="$APK_DIR/VictorLaunch.apk"
PERMS_XML="$MODPATH/system/etc/permissions/privapp-permissions-$PKG.xml"

# APatch also exports KSU=true for compatibility, so it must be tested first.
detect_manager() {
  if [ "$APATCH" = "true" ]; then
    MANAGER="APatch"
    MANAGER_VER="${APATCH_VER:-unknown} (${APATCH_VER_CODE:-?})"
  elif [ "$KSU" = "true" ]; then
    MANAGER="KernelSU"
    MANAGER_VER="${KSU_VER:-unknown} (${KSU_VER_CODE:-?})"
  elif [ -n "$MAGISK_VER_CODE" ]; then
    MANAGER="Magisk"
    MANAGER_VER="${MAGISK_VER:-unknown} ($MAGISK_VER_CODE)"
  else
    MANAGER=""
  fi
}

set_permissions() {
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644 u:object_r:system_file:s0
  set_perm_recursive "$MODPATH/system/etc/permissions" 0 0 0755 0644 u:object_r:system_file:s0
  set_perm "$APK" 0 0 0644 u:object_r:system_file:s0
  set_perm "$PERMS_XML" 0 0 0644 u:object_r:system_file:s0
  if [ -f "$MODPATH/uninstall.sh" ]; then
    set_perm "$MODPATH/uninstall.sh" 0 0 0755
  fi
  set_perm "$MODPATH/customize.sh" 0 0 0755
}

detect_manager
if [ -z "$MANAGER" ]; then
  abort "! Unsupported installer: need Magisk 20.4+, KernelSU or APatch."
fi
ui_print "- Root manager: $MANAGER $MANAGER_VER"

SDK="${API:-$(getprop ro.build.version.sdk)}"
if [ -z "$SDK" ] || [ "$SDK" -lt 26 ]; then
  abort "! Android 8.0 (API 26) or newer is required (found API ${SDK:-unknown})."
fi
ui_print "- Android API level: $SDK"

[ -f "$APK" ] || abort "! VictorLaunch.apk is missing from this zip. Re-download the module."
[ -f "$PERMS_XML" ] || abort "! Privileged-permission allowlist is missing from this zip."

USER_COPY="$(pm path "$PKG" 2>/dev/null | head -n 1)"
case "$USER_COPY" in
  *"/data/app/"*)
    ui_print "! A user-installed VictorLaunch is present: $USER_COPY"
    ui_print "! Uninstall it before rebooting, or the system copy may not take effect"
    ui_print "! (a signature or version mismatch can also make Android ignore one copy)."
    ui_print "! Uninstalling erases its data; export a layout backup first."
    HAD_USER_COPY="yes"
    ;;
  *)
    HAD_USER_COPY="no"
    ;;
esac

mkdir -p "$STATE_DIR"
{
  echo "installed_by=$MANAGER $MANAGER_VER"
  echo "api=$SDK"
  echo "user_copy_present=$HAD_USER_COPY"
} > "$STATE_DIR/install.log"

set_permissions

ui_print "- VictorLaunch will be a privileged system app after the next reboot."
ui_print "- The launcher works without this module. To recover from a bad boot,"
ui_print "  see docs/ROOT_MODULE.md in the repo (create /data/adb/modules/victorlaunch/disable)."
