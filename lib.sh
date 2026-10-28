# Shared setup for the scripts: connects to the TV and loads its profile.
# Picks the TV from $TV, else the first line of .tv. Each TV gets its own
# profile (profiles/<device>.sh) and backup folder (backup/<device>/).
cd "$(dirname "$0")"
TV="${TV:-$(head -1 .tv 2>/dev/null)}"
[ -n "$TV" ] || { echo "Set TV=<ip>:5555 or write it to .tv"; exit 1; }
ADB="tools/platform-tools/adb"; command -v adb >/dev/null && [ ! -x "$ADB" ] && ADB=adb
A="$ADB -s $TV"
timeout 15 $ADB connect "$TV" >/dev/null
[ "$(timeout 10 $A get-state 2>/dev/null)" = device ] || { echo "TV not reachable/authorized at $TV"; exit 1; }

DEVICE=$($A shell getprop ro.product.device | tr -d '\r')
BDIR="backup/$DEVICE"
LOG="$BDIR/debloat-applied.txt"
PROFILE="profiles/$DEVICE.sh"
PKGS=(); DOCK=""; KEYMAP=""; HIDDEN=""; KEEP_ADB_WIFI=0; KEEP_UPDATES=""; CLEAR_DATA=""
[ -f "$PROFILE" ] && . "$PROFILE"
echo "TV $TV: $($A shell getprop ro.product.manufacturer | tr -d '\r') $($A shell getprop ro.product.model | tr -d '\r') [$DEVICE]"
