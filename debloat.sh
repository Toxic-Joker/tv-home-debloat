#!/usr/bin/env bash
# Disable the packages listed in this TV's profile (profiles/<device>.sh) — REVERSIBLE ONLY.
# Uses `pm disable-user` (never uninstall). Undo with ./restore.sh
set -u
. "$(dirname "$0")/lib.sh"
[ ${#PKGS[@]} -gt 0 ] || { echo "No profile for '$DEVICE': create $PROFILE first"; exit 1; }
[ -f "$BDIR/LATEST" ] || { echo "No backup for $DEVICE yet: run ./backup.sh first"; exit 1; }

for p in "${PKGS[@]}"; do
  if ! $A shell pm list packages -e "$p" | tr -d '\r' | grep -qx "package:$p"; then
    echo "skip   $p (absent or already disabled)"; continue
  fi
  $A shell am force-stop "$p" >/dev/null 2>&1
  out=$($A shell pm disable-user --user 0 "$p" 2>&1)
  if echo "$out" | grep -q "disabled-user"; then
    echo "OK     $p"; grep -qx "$p" "$LOG" 2>/dev/null || echo "$p" >> "$LOG"
  else
    echo "FAIL   $p: $out"
  fi
done

# Snappier UI: halve animation durations (restore.sh resets to backed-up values)
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  $A shell settings put global $k 0.5
done
echo "Done. Disabled packages logged in $LOG"
