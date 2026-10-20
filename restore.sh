#!/usr/bin/env bash
# Undo everything debloat.sh / set-launcher.sh did, using the backup taken beforehand.
#   ./restore.sh                     re-enable apps, restore settings + Google TV launcher
#   ./restore.sh --remove-launcher   ...and also uninstall the TV Home launcher
#   BACKUP=backup/<timestamp> ./restore.sh   use a specific backup snapshot
set -u
cd "$(dirname "$0")"
TV="${TV:-$(cat .tv 2>/dev/null)}"; [ -n "$TV" ] || { echo "Set TV=<ip>:5555 or write it to .tv"; exit 1; }
ADB="tools/platform-tools/adb"; command -v adb >/dev/null && [ ! -x "$ADB" ] && ADB=adb
A="$ADB -s $TV"
B="${BACKUP:-$(cat backup/LATEST)}"
[ -d "$B" ] || { echo "Backup folder $B not found"; exit 1; }
$ADB connect "$TV" >/dev/null
$A get-state >/dev/null || { echo "TV not reachable at $TV"; exit 1; }
echo "Restoring from $B"

# 1) Re-enable every package we disabled (log), plus anything enabled in the snapshot but disabled now
want=$( { cat backup/debloat-applied.txt 2>/dev/null; sed 's/^package://' "$B/packages-enabled.txt"; } | sort -u)
now_disabled=$($A shell pm list packages -d | sed 's/^package://' | tr -d '\r' | sort -u)
for p in $(comm -12 <(echo "$want") <(echo "$now_disabled")); do
  echo "enable $p: $($A shell pm enable --user 0 "$p" 2>&1 | tr -d '\r')"
done

# 2) Restore settings we touched to their backed-up values (delete if they did not exist)
restore_setting() { # namespace key
  local v; v=$(grep -m1 "^$2=" "$B/settings-$1.txt" | cut -d= -f2- | tr -d '\r')
  if [ -n "$v" ]; then $A shell settings put "$1" "$2" "$v"; else $A shell settings delete "$1" "$2" >/dev/null; fi
}
for k in window_animation_scale transition_animation_scale animator_duration_scale; do restore_setting global $k; done
for k in screensaver_components screensaver_default_component screensaver_enabled enabled_accessibility_services accessibility_enabled; do restore_setting secure $k; done
restore_setting system screen_off_timeout

# 3) Original home launcher
orig=$(tail -1 "$B/home-launcher.txt" | tr -d '\r')
$A shell cmd package set-home-activity --user 0 "$orig"
if [ "${1:-}" = "--remove-launcher" ]; then $A uninstall com.tvhome.launcher; fi
$A shell input keyevent KEYCODE_HOME
: > backup/debloat-applied.txt
echo "Restore complete. Disabled now: $($A shell pm list packages -d | wc -l) packages."
