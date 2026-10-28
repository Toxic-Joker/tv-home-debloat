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
  com.google.android.tts                      # Text-to-speech (only for TalkBack); voices ~200 MB
  # --- Bundled streaming apps (not used) ---
  com.amazon.amazonvideo.livingroom           # Prime Video
  # --- Ad / personalization sandbox (may refuse: mainline module) ---
  com.android.adservices.api
  com.android.ondevicepersonalization.services
  com.android.federatedcompute.services
)

# TV Home dock (left to right)
DOCK="org.smarttube.stable,com.sportzx.live,com.miruronative,com.apple.atve.androidtv.appletv"
HIDDEN=""

# Xiaomi remote buttons TV Home takes over (Android keycode=target).
# Targets: panel, allapps, app:<package>, action:<intent action>
# Their stock apps are disabled above, so the buttons were dead:
#   190 YouTube -> SmartTube          194 Prime Video -> NetMirrorTV
#   192 Xiaomi TV+ -> Apple TV        284 APPS -> All apps
# Untouched: Netflix 193 (still opens Netflix), mic 186 (Assistant disabled; left unbound)
KEYMAP="190=app:org.smarttube.stable,194=app:app.netmirror.newtv,192=app:com.apple.atve.androidtv.appletv,284=allapps"

# Android switches Wireless debugging off at every boot; TV Home turns it back on so the
# scripts can always reach the stick (new computers still need approval on screen)
KEEP_ADB_WIFI=1

# free-space.sh: keep the Google TV launcher's update so restore.sh brings back today's Google TV,
# and drop the downloaded text-to-speech voices
KEEP_UPDATES="com.google.android.apps.tv.launcherx"
CLEAR_DATA="com.google.android.tts"
