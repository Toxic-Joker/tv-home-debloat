#!/usr/bin/env bash
# Debloat Xiaomi Mi TV Stick (Google TV, Android 14) — REVERSIBLE ONLY.
# Uses `pm disable-user` (never uninstall). Undo with ./restore.sh
set -u
cd "$(dirname "$0")"
TV="${TV:-$(cat .tv 2>/dev/null)}"; [ -n "$TV" ] || { echo "Set TV=<ip>:5555 or write it to .tv"; exit 1; }
ADB="tools/platform-tools/adb"; command -v adb >/dev/null && [ ! -x "$ADB" ] && ADB=adb
A="$ADB -s $TV"
$ADB connect "$TV" >/dev/null

PKGS=(
  # --- Xiaomi telemetry / ads / promo tabs ---
  com.miui.tv.analytics                       # Xiaomi analytics
  com.xiaomi.statistic                        # Xiaomi statistics/telemetry
  com.xiaomi.android.tvsetup.partnercustomizer # pushes partner/promo content
  com.xm.webcontent                           # promo web content
  com.xiaomo.tv.milegal                       # legal/privacy nags
  com.mitv.tvhome.atv                         # Mi Home tab
  com.mitv.tvhome.michannel                   # Mi channels
  com.mitv.tvhome.mitvplus                    # Mi TV+ (ad-supported)
  com.mitv.tvhome.oemtab                      # OEM tab in Google TV
  # --- Screen mirroring (not used) ---
  com.xiaomi.mi_connect_service
  com.xiaomi.mitv.smartshare
  com.xiaomi.wfdsinkhelperservice
  # --- Google extras (not used) ---
  com.google.android.apps.mediashell          # Chromecast built-in
  com.google.android.katniss                  # Assistant / voice search
  com.google.android.apps.tv.dreamx           # Ambient mode (promoted content)
  com.google.android.youtube.tv               # official YouTube (SmartTube kept)
  com.google.android.youtube.tvmusic          # YouTube Music
  com.google.android.play.games
  com.google.android.feedback
  com.android.tv.feedbackconsent
  com.google.android.syncadapters.calendar
  com.android.printspooler
  # --- Bundled streaming apps (not used) ---
  com.amazon.amazonvideo.livingroom           # Prime Video
  # --- Ad / personalization sandbox (may refuse: mainline module) ---
  com.android.adservices.api
  com.android.ondevicepersonalization.services
  com.android.federatedcompute.services
)

LOG=backup/debloat-applied.txt
mkdir -p backup
for p in "${PKGS[@]}"; do
  if ! $A shell pm list packages -e "$p" | grep -qx "package:$p"; then
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
