#!/usr/bin/env bash
# Free storage held by apps that debloat.sh disabled:
#  - roll their downloaded updates back to the factory version built into the TV
#    (re-enabling an app later lets Play Store update it again; its old data is wiped)
#  - clear the data of the profile's CLEAR_DATA packages (e.g. downloaded TTS voices)
# Packages in the profile's KEEP_UPDATES are left alone.
set -u
. "$(dirname "$0")/lib.sh"
[ -s "$LOG" ] || { echo "Nothing disabled yet on $DEVICE (run ./debloat.sh first)"; exit 1; }
before=$($A shell df /data | tail -1 | awk '{print $4}')
for p in $(cat "$LOG"); do
  case " $KEEP_UPDATES " in *" $p "*) echo "keep   $p (KEEP_UPDATES)"; continue ;; esac
  out=$($A shell pm uninstall-system-updates "$p" 2>&1 | tr -d '\r')
  echo "$out" | grep -qi success && echo "freed  $p" || echo "skip   $p: $out"
done
for p in $CLEAR_DATA; do
  echo "clear  $p: $($A shell pm clear --user 0 "$p" 2>&1 | tr -d '\r')"
done
after=$($A shell df /data | tail -1 | awk '{print $4}')
echo "Free space: $((before / 1024)) MB -> $((after / 1024)) MB"
