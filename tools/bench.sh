#!/bin/bash
# Frame stats (gfxinfo) for the Library tabs, Now Playing and tab switches on a connected phone: tools/bench.sh <adb serial>
# Judge performance on the RELEASE build (./gradlew :app:assembleRelease, signed with the debug key): the debug build runs Compose
# several times slower. Coordinates are for a 1080x2400 screen with a song loaded; a 'Janky' frame missed its deadline.
D="adb -s ${1:?adb serial}"
P=com.mrgnstr.harmony
grab() { $D shell dumpsys gfxinfo $P | grep -E "Total frames|Janky frames:|50th percentile|90th percentile|99th percentile|50th gpu" | tr '\n' ' '; echo; }
$D shell am force-stop $P; $D shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
$D shell input tap 634 2238; sleep 2
for tab in "recently-added 200" "albums 540"; do
  set -- $tab; $D shell input tap $2 450; sleep 2; $D shell dumpsys gfxinfo $P reset >/dev/null
  for i in 1 2 3 4 5 6; do $D shell input swipe 500 1700 500 500 220; sleep 0.9; done
  echo "$1: $(grab)"
done
$D shell dumpsys gfxinfo $P reset >/dev/null
for i in 1 2 3 4 5; do $D shell input tap 400 2040; sleep 1.5; $D shell input swipe 540 600 540 2100 250; sleep 1.2; done
echo "now-playing open/close: $(grab)"
