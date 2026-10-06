#!/usr/bin/env bash
# Capture the six screenshots on a running emulator (docs/spec/build.md BUILD-085).
#
# Called from screenshots.yml as the emulator runner's one-line `script:`. The runner runs each
# `script:` line in its own shell, so everything that has to happen in order lives here.
#
# Usage: capture_screenshots.sh <output_dir>
# Expects the debug APK and the debug androidTest APK to be built already.
set -euo pipefail

out="$1"
pkg="com.derekwinters.intervaltrainer"
mkdir -p "$out"

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
  sleep 2
done
# The spike for #161 found the first capture unreliable until the system had settled after boot.
sleep 30

# Keep the screen on and unlocked for the whole run.
adb shell svc power stayon true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82 || true

# The determinism invariant: no animation frame is ever captured.
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$scale" 0
done

# The determinism invariant: System UI demo mode fixes everything in the status bar that would
# otherwise differ between runs.
adb shell settings put global sysui_demo_allowed 1
demo() {
  adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null
}
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false -e powersave false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e datatype none -e level 4 -e fully true
demo notifications -e visible false

# `-g` grants every runtime permission the manifest asks for, POST_NOTIFICATIONS among them, so
# first run's Continue goes straight to home and settings reads the same every run (BUILD-081).
for apk in app/build/outputs/apk/debug/*.apk; do
  adb install -r -g "$apk"
done
for apk in app/build/outputs/apk/androidTest/debug/*.apk; do
  adb install -r -g "$apk"
done

adb shell am instrument -w \
  -e class "$pkg.screenshots.ScreenshotTourTest" \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out/instrument.txt"
if ! grep -q "^OK (" "$out/instrument.txt"; then
  echo "::error::ScreenshotTourTest did not pass; see instrument.txt above"
  exit 1
fi

# The test wrote into the app's private files/; the debug APK is debuggable, so run-as reads it.
for name in $(adb shell run-as "$pkg" ls files/screenshots | tr -d '\r'); do
  adb exec-out run-as "$pkg" cat "files/screenshots/$name" > "$out/$name"
done
ls -l "$out"
