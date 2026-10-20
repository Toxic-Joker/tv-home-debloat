#!/usr/bin/env bash
# Make TV Home the Home-button launcher, Nature Slideshow the screensaver, and hold-Home quick panel. Undo with ./restore.sh
set -u
cd "$(dirname "$0")"
TV="${TV:-$(cat .tv 2>/dev/null)}"; [ -n "$TV" ] || { echo "Set TV=<ip>:5555 or write it to .tv"; exit 1; }
ADB="tools/platform-tools/adb"; command -v adb >/dev/null && [ ! -x "$ADB" ] && ADB=adb
A="$ADB -s $TV"
$ADB connect "$TV" >/dev/null
LOG=backup/debloat-applied.txt

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
# Hold Home -> TV Home quick panel (accessibility service that only watches the Home key)
SVC=com.tvhome.launcher/com.tvhome.launcher.HomeKeyService
cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$cur" in *"$SVC"*) ;; null|"") $A shell settings put secure enabled_accessibility_services "$SVC" ;; *) $A shell settings put secure enabled_accessibility_services "$cur:$SVC" ;; esac
$A shell settings put secure accessibility_enabled 1
$A shell input keyevent KEYCODE_HOME
