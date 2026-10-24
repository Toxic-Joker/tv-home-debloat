#!/usr/bin/env bash
# Make TV Home the Home-button launcher, Nature Slideshow the screensaver, and hold-Home quick panel. Undo with ./restore.sh
set -u
. "$(dirname "$0")/lib.sh"
mkdir -p "$BDIR"

$A shell pm list packages com.tvhome.launcher | grep -q . || { echo "Install launcher first: launcher/build-and-install.sh"; exit 1; }
$A shell cmd package set-home-activity --user 0 com.tvhome.launcher/.HomeActivity
# Google TV hijacks the Home key while these are enabled
for p in com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
  out=$($A shell pm disable-user --user 0 "$p" 2>&1); echo "$out"
  echo "$out" | grep -q disabled-user && { grep -qx "$p" "$LOG" 2>/dev/null || echo "$p" >> "$LOG"; }
done
$A shell settings put secure screensaver_components com.tvhome.launcher/.NatureDream
$A shell settings put secure screensaver_default_component com.tvhome.launcher/.NatureDream
$A shell settings put secure screensaver_enabled 1
# Hold Home -> TV Home quick panel; also remaps the remote buttons listed in the profile's KEYMAP
SVC=com.tvhome.launcher/com.tvhome.launcher.HomeKeyService
cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$cur" in *"$SVC"*) ;; null|"") $A shell settings put secure enabled_accessibility_services "$SVC" ;; *) $A shell settings put secure enabled_accessibility_services "$cur:$SVC" ;; esac
$A shell settings put secure accessibility_enabled 1
# Optionally keep Wireless debugging on across reboots (Android turns it off at boot)
if [ "$KEEP_ADB_WIFI" = 1 ]; then
  $A shell pm grant com.tvhome.launcher android.permission.WRITE_SECURE_SETTINGS && KEEP=true
else
  $A shell pm revoke com.tvhome.launcher android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; KEEP=false
fi
# This TV's dock and button remaps (receiver only accepts ADB/shell, via the DUMP permission)
$A shell am broadcast -a com.tvhome.launcher.CONFIGURE -n com.tvhome.launcher/.ConfigReceiver \
  --es dock "'$DOCK'" --es keymap "'$KEYMAP'" --es hidden "'$HIDDEN'" --ez keep_adb_wifi $KEEP | tail -1
$A shell input keyevent KEYCODE_HOME
