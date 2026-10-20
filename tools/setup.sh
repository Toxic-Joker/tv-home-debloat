#!/usr/bin/env bash
# Download adb + the Android SDK pieces needed to build the launcher (into tools/, no root needed).
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -x platform-tools/adb ]; then
  curl -sSL -o pt.zip https://dl.google.com/android/repository/platform-tools-latest-linux.zip
  unzip -qo pt.zip && rm pt.zip
fi
if [ ! -d sdk/build-tools/36.0.0 ]; then
  mkdir -p sdk && cd sdk
  curl -sSL -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -qo clt.zip && rm clt.zip
  mkdir -p cmdline-tools/latest && mv cmdline-tools/{bin,lib,NOTICE.txt,source.properties} cmdline-tools/latest/ 2>/dev/null || true
  yes | cmdline-tools/latest/bin/sdkmanager --sdk_root="$PWD" "platforms;android-34" "build-tools;36.0.0" >/dev/null
fi
echo "Ready: tools/platform-tools/adb and tools/sdk"
