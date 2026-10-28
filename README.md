# Google TV debloat + TV Home launcher

Debloat Google TV devices over ADB and replace the ad-filled Google TV home with a fast, minimal
Apple TV-style launcher. Profiles included for a Xiaomi Mi TV Stick 4K and a Continental Edison
CDI Smart TV (MediaTek); other Google TVs need a profile of their own. Inspired by
[mihirgupta0900/tv-home](https://github.com/mihirgupta0900/tv-home).

| Before | After |
|---|---|
| ![Stock Google TV](docs/before-google-tv.png) | ![TV Home](docs/home.png) |
| ![All apps](docs/all-apps.png) | ![Hold-Home quick panel](docs/quick-panel.png) |

## Setup
```bash
tools/setup.sh                      # downloads adb + Android SDK build tools into tools/
echo "<tv-ip>:5555" > .tv           # default TV's address (git-ignored); TV=<ip>:5555 ./script for another
tools/platform-tools/adb connect "$(cat .tv)"   # accept the prompt on the TV
```
Enable *Developer options → USB/Network debugging* on the TV first.

Everything here is reversible: apps are only *disabled* (`pm disable-user`), never uninstalled.

| File | What it does |
|---|---|
| `profiles/<device>.sh` | Per-TV settings, picked by the TV's `ro.product.device`: packages to disable, dock, hidden apps, remote button remaps |
| `backup.sh` | Run first: snapshots package states, settings and the default launcher |
| `backup/<device>/` | That TV's snapshots and log of what was disabled, read by `restore.sh` (git-ignored) |
| `debloat.sh` | Disables the profile's packages (ads, tracking, unused features); animations set to 0.5x |
| `launcher/` | TV Home source (plain Java, no Gradle) + `build-and-install.sh` |
| `set-launcher.sh` | Makes TV Home the Home button launcher and Nature Slideshow the screensaver, enables the key service and applies the profile's dock / remaps |
| `restore.sh` | Undo everything (`--remove-launcher` also uninstalls TV Home) |
| `tools/setup.sh` | Fetches adb and the SDK build tools locally |

## Using the home screen
- **Left/Right** move along the dock; the app name shows above it. **OK** opens the app.
- **Down** goes to *All apps*. **Back** or **Home** returns to the dock.
- **Long-press OK** (or Menu) on any app: add to dock / move left / move right / remove / app info.
- **Hold Home** anywhere (even inside an app) for the quick panel: Network, Display & sound, Storage,
  Apps, Screensaver, Power (sleep / restart) and All settings. Back closes it and returns to the app.
  It works through a small accessibility service ("TV Home quick panel") that only watches the Home key.
- Three looks, cycled from the quick panel (**Home style** tile) or set with
  `--es style cinema|glass|shelf` on the CONFIGURE broadcast:
  **Cinema** (wide artwork, big title of the focused app, colour glow), **Glass**
  (glossy rounded squares with the logo auto-cropped from the app's banner, floor reflection,
  frosted app library) and **Top Shelf** (Apple TV-like: full-bleed wallpaper shelf over one grid,
  favourites first, name shown only on the focused tile).
- The quick panel is a control centre: round quick actions (Wi-Fi, screensaver, power, settings)
  above pill controls (display & sound, storage with a usage bar, apps, home style).
- While another app is open, TV Home releases its wallpaper bitmaps (about 40 MB on a Mi TV Stick).
- English and French UI (follows the TV's language).
- Wallpapers crossfade every 10 minutes (screensaver: every minute). Add or replace JPEGs in
  `launcher/assets/wallpapers/` (1920x1080) and add a caption line to `credits.txt`, then rebuild.

## Remote buttons
Google TV routes some buttons to its own launcher, which gets disabled. TV Home's key service can take any
button over; the profile's `KEYMAP` lists `keycode=target` pairs, where target is `panel`, `allapps`,
`app:<package>` or `action:<intent action>`. Find a button's keycode by turning on key logging:
```bash
adb shell am broadcast -a com.tvhome.launcher.CONFIGURE -n com.tvhome.launcher/.ConfigReceiver --ez debug_keys true
adb logcat -s TvHomeKey   # press buttons; turn off again with --ez debug_keys false
```
The CONFIGURE receiver needs the DUMP permission, so only ADB can change the dock or remaps.

On the Mi TV Stick: YouTube → SmartTube, Prime Video → NetMirrorTV, Xiaomi TV+ → Apple TV, APPS → All apps
(their stock apps are disabled); Netflix unchanged.

On the Continental Edison TV: YouTube → SmartTube, Gear → quick panel, Person → Accounts, ⋮⋮⋮ → All apps.
Netflix, Prime Video, USB, TV, Input and the mic (Bluetooth pairing) stay with the TV's own services,
so their apps (and `com.mediatek.*`, `com.google.android.tv.inputplayer`) are never disabled.

## Adding another TV
1. `TV=<ip>:5555 ./backup.sh` (prints the device name, e.g. `[nagata]`).
2. Copy a profile to `profiles/<device>.sh` and edit `PKGS`, `DOCK`, `HIDDEN`, `KEYMAP`, `KEEP_ADB_WIFI`.
   Keep anything a remote button launches (check `dumpsys window`'s global keys and test each button).
3. `TV=<ip>:5555 ./debloat.sh && TV=<ip>:5555 launcher/build-and-install.sh && TV=<ip>:5555 ./set-launcher.sh`

## Common tasks
```bash
./backup.sh && ./debloat.sh && launcher/build-and-install.sh && ./set-launcher.sh   # first-time setup
./launcher/build-and-install.sh     # rebuild + reinstall after editing the launcher
./debloat.sh && ./set-launcher.sh   # re-apply after a firmware update re-enables things
./restore.sh                        # back to stock Google TV
```
To test the hold-Home panel or remaps over ADB, use `adb shell uinput /data/local/tmp/hold-home.json`
(push `tools/hold-home.json` first; `tools/key-f1..f3.json` press F1–F3 for temporary test remaps).
`adb shell input keyevent` bypasses accessibility services, so it can't trigger them.

Android switches Wireless debugging off at every boot. With `KEEP_ADB_WIFI=1` in a profile (set on the
Mi TV Stick), `set-launcher.sh` grants TV Home `WRITE_SECURE_SETTINGS` and its key service turns
Wireless debugging back on after boot and on Wi-Fi reconnects. New computers still need approval on the TV;
`restore.sh` revokes the permission. Otherwise, re-enable it in Settings -> System -> Developer options,
then `tools/platform-tools/adb connect "$(cat .tv)"`.

Wallpapers: Wikimedia Commons Featured Pictures, cropped and resized; authors and licenses in
[launcher/assets/wallpapers/ATTRIBUTION.md](launcher/assets/wallpapers/ATTRIBUTION.md).
