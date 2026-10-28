#!/usr/bin/env bash
# Keep a TV debloated after firmware / app updates, without touching anything when all is fine.
# Checks: firmware changed, debloated apps re-enabled, TV Home not the Home app, key service off,
# new apps added by the firmware. On drift it re-runs debloat.sh + set-launcher.sh (without pressing
# Home, so whatever is playing isn't interrupted). Safe to run on a schedule: ./maintain.sh
#   ./maintain.sh --install-schedule   run it every 2 hours from this computer (crontab)
#   ./maintain.sh --remove-schedule
set -u
cd "$(dirname "$0")"
if [ "${1:-}" = "--install-schedule" ] || [ "${1:-}" = "--remove-schedule" ]; then
  me="$PWD/maintain.sh"
  { crontab -l 2>/dev/null | grep -vF "$me"
    [ "$1" = "--install-schedule" ] && for tv in $(cat .tv); do echo "17 */2 * * * TV=$tv $me >/dev/null 2>&1"; done
  } | crontab -
  echo "Schedule now:"; crontab -l 2>/dev/null | grep -F "$me" || echo "(none)"
  exit 0
fi

. "$(dirname "$0")/lib.sh" >/dev/null
[ -f "$BDIR/LATEST" ] || { echo "No backup for $DEVICE"; exit 1; }
B=$(cat "$BDIR/LATEST")
LOGF="$BDIR/maintain.log"
say() { echo "$(date '+%F %T') $*" | tee -a "$LOGF"; }
reasons=()

fp=$($A shell getprop ro.build.fingerprint | tr -d '\r')
[ -f "$BDIR/fingerprint" ] || echo "$fp" > "$BDIR/fingerprint"
if [ "$fp" != "$(cat "$BDIR/fingerprint")" ]; then
  reasons+=("firmware updated: $(cat "$BDIR/fingerprint") -> $fp")
fi
enabled=$($A shell pm list packages -e --user 0 | sed 's/^package://' | tr -d '\r')
for p in $(cat "$LOG" 2>/dev/null); do
  echo "$enabled" | grep -qx "$p" && reasons+=("re-enabled: $p")
done
home=$($A shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r')
case "$home" in com.tvhome.launcher/*) ;; *) reasons+=("home app is $home") ;; esac
a11y=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$a11y" in *com.tvhome.launcher*) ;; *) reasons+=("key service off") ;; esac
new=$(comm -13 <(sed 's/^package://' "$B/packages-system.txt" | tr -d '\r' | sort -u) \
               <($A shell pm list packages -s | sed 's/^package://' | tr -d '\r' | sort -u))
[ -n "$new" ] && say "new system apps since backup (review for bloat): $(echo $new)"

if [ ${#reasons[@]} -eq 0 ]; then
  say "ok ($fp)"; exit 0
fi
for r in "${reasons[@]}"; do say "drift: $r"; done
./debloat.sh >> "$LOGF" 2>&1
NO_HOME=1 ./set-launcher.sh >> "$LOGF" 2>&1
echo "$fp" > "$BDIR/fingerprint"
say "re-applied debloat + TV Home"
