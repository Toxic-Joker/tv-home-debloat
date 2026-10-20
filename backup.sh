#!/usr/bin/env bash
# Snapshot the TV's state before changing anything. restore.sh uses the latest snapshot.
set -u
cd "$(dirname "$0")"
TV="${TV:-$(cat .tv 2>/dev/null)}"; [ -n "$TV" ] || { echo "Set TV=<ip>:5555 or write it to .tv"; exit 1; }
ADB="tools/platform-tools/adb"; command -v adb >/dev/null && [ ! -x "$ADB" ] && ADB=adb
A="$ADB -s $TV"
$ADB connect "$TV" >/dev/null
if [ -s backup/debloat-applied.txt ] && [ "${1:-}" != "--force" ]; then
  echo "Changes already applied; a new snapshot would replace the pre-debloat one restore.sh needs. Use --force to override."; exit 1
fi
B=backup/$(date +%Y%m%d-%H%M%S); mkdir -p "$B"; echo "$B" > backup/LATEST

$A shell pm list packages -f -U > "$B/packages-all.txt"
$A shell pm list packages -e > "$B/packages-enabled.txt"
$A shell pm list packages -d > "$B/packages-disabled.txt"
$A shell pm list packages -s > "$B/packages-system.txt"
$A shell pm list packages -3 > "$B/packages-user.txt"
for ns in global secure system; do $A shell settings list $ns > "$B/settings-$ns.txt"; done
$A shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$B/home-launcher.txt"
$A shell getprop > "$B/getprop.txt"
$A shell dumpsys meminfo > "$B/meminfo.txt"
$A exec-out screencap -p > "$B/screen.png"
echo "Saved $B"
