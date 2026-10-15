#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-captures
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell wm size 1220x2712
adb shell wm density 480
chmod +x gradlew
./gradlew --no-daemon connectedDebugAndroidTest
zhivo_result=$?
if [ -d app/build/reports/androidTests ]; then cp -r app/build/reports/androidTests device-captures/normal-reports; fi
if [ "$zhivo_result" -eq 0 ]; then
  adb shell settings put system font_scale 1.5
  ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.zhivo.app.InterfaceTest
  zhivo_result=$?
fi
timeout 20s adb pull /sdcard/Android/data/ru.zhivo.study/files/ device-captures/ || true
timeout 20s adb logcat -d > device-captures/logcat.txt || true
exit "$zhivo_result"
