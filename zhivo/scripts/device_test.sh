#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-captures
adb shell svc wifi disable || true
adb shell svc data disable || true
chmod +x gradlew
./gradlew --no-daemon connectedDebugAndroidTest
zhivo_result=$?
adb pull /sdcard/Android/data/ru.zhivo.study/files/ device-captures/ || true
adb logcat -d > device-captures/logcat.txt || true
exit "$zhivo_result"
