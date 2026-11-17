#!/system/bin/sh
# shellcheck shell=sh
# Run by Magisk, KernelSU or APatch when the module is removed. The overlaid
# system files live inside the module directory and disappear with it; only the
# install log written by customize.sh is left to clean up.

rm -rf /data/adb/victorlaunch
