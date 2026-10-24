# Continental Edison CDI Smart TV (Google TV, Android 14, MediaTek)
# Packages disabled by debloat.sh (pm disable-user, reversible).
# Kept on purpose because remote buttons depend on them: Netflix, Prime Video (com.amazon...),
# MediaTek TV services (Input / TV / USB / hotkeys), Bluetooth autopair (mic button), official YouTube.
PKGS=(
  # --- Tracking / ads / demo ---
  tv.anoki.acr.anokiacroptin                  # ACR: fingerprints what's on screen for ad tracking
  fusion.android.tv.demo                      # in-store demo mode
  com.mediatek.android.leanbacklauncher.partnercustomizer # partner promo channels
  com.google.android.feedback
  com.android.tv.feedbackconsent
  # --- Google extras (not used) ---
  com.google.android.apps.mediashell          # Chromecast built-in
  com.google.android.katniss                  # Assistant / voice search
  com.google.android.apps.tv.dreamx           # Ambient mode (promoted content)
  com.google.android.youtube.tvmusic          # YouTube Music
  com.google.android.play.games
  com.google.android.syncadapters.calendar
  # --- Ad / personalization sandbox ---
  com.android.adservices.api
  com.android.ondevicepersonalization.services
  com.android.federatedcompute.services
)

# TV Home dock (left to right)
DOCK="fr.m6.m6replay,org.smarttube.stable,com.netflix.ninja,com.amazon.amazonvideo.livingroom"

# Firmware entries hidden from TV Home's All apps (still enabled; the remote needs HotKeyService)
HIDDEN="com.mstar.android.tv.disclaimercustomization,com.mediatek.hotkey.dispatcher"

# Remote buttons TV Home takes over (Android keycode=target).
# Targets: panel, allapps, app:<package>, action:<intent action>
#   247 YouTube -> SmartTube        262 Gear -> quick panel (was the Google TV dashboard)
#   288 Person  -> Accounts         211 ⋮⋮⋮  -> All apps (firmware sent it to a factory app)
# Untouched (handled by MediaTek/vendor services, still work): Netflix 265, Prime 101, USB 214,
#   TV 267, Input 178, mic (Bluetooth pairing) 225, INFO 210
KEYMAP="247=app:org.smarttube.stable,262=panel,288=action:android.settings.SYNC_SETTINGS,211=allapps"
