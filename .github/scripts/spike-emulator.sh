#!/usr/bin/env bash
# Screenshot spike (throwaway): installs the debug APK on the running emulator and captures screens.
set -euo pipefail
PKG=com.derekwinters.intervaltrainer
OUT=emu-shots
mkdir -p "$OUT"

tap_node() { # $1 = attribute (text|content-desc), $2 = value
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
  python3 - "$OUT/ui.xml" "$1" "$2" <<'PY' > "$OUT/xy"
import re, sys, xml.etree.ElementTree as ET
path, attr, value = sys.argv[1:]
for node in ET.parse(path).iter("node"):
    if node.get(attr) == value:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
else:
    sys.exit(f"no node with {attr}={value!r}")
PY
  read -r x y < "$OUT/xy"
  echo "tapping $1=$2 at $x,$y"
  adb shell input tap "$x" "$y"
}

adb install -r app/build/outputs/apk/debug/*.apk
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
adb exec-out screencap -p > "$OUT/emu-1.png"

# Past the first-run screen to the home screen, then into the new-preset editor.
if tap_node text "Continue"; then
  sleep 4
  adb exec-out screencap -p > "$OUT/emu-2.png"
  if tap_node content-desc "New preset"; then
    sleep 3
    adb exec-out screencap -p > "$OUT/emu-3.png"
  fi
fi
cat "$OUT/ui.xml" | head -c 4000 || true
echo
ls -la "$OUT"
