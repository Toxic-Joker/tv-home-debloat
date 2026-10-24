# Xiaomi Mi TV Stick 4K (Google TV, Android 14)
# Packages disabled by debloat.sh (pm disable-user, reversible)
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

# TV Home dock (left to right) and remote key remaps (none needed on this stick)
DOCK="org.smarttube.stable,com.sportzx.live,com.miruronative,com.apple.atve.androidtv.appletv"
KEYMAP=""
HIDDEN=""
