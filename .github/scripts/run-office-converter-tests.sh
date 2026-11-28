#!/bin/sh
set -eu

chmod +x gradlew
adb wait-for-device

boot_deadline=$(($(date +%s) + 600))
while :; do
  boot_state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot_state" = "1" ]; then break; fi
  if [ "$(date +%s)" -ge "$boot_deadline" ]; then
    echo "Emulator did not finish Android boot within 600 seconds."
    adb shell getprop || true
    adb logcat -d -t 1200 > office-converter-logcat.txt 2>&1 || true
    exit 1
  fi
  sleep 5
done

services_deadline=$(($(date +%s) + 300))
while :; do
  package_status=$(adb shell service check package 2>/dev/null | tr -d '\r' || true)
  settings_status=$(adb shell service check settings 2>/dev/null | tr -d '\r' || true)
  api_level=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
  if echo "$package_status" | grep -q found &&
     echo "$settings_status" | grep -q found &&
     [ -n "$api_level" ] &&
     adb shell cmd package list packages >/dev/null 2>&1; then
    echo "Detected Android API level: $api_level"
    break
  fi
  if [ "$(date +%s)" -ge "$services_deadline" ]; then
    echo "Android boot completed, but PackageManager/Settings did not become ready."
    echo "Package service: $package_status"
    echo "Settings service: $settings_status"
    adb logcat -d -t 3000 > office-converter-logcat.txt 2>&1 || true
    exit 1
  fi
  sleep 5
done

echo "Android boot and required system services are ready."
echo "Emulator ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"

# Keep a device-side trace while instrumentation runs. A bounded test window
# prevents a stalled emulator/test runner from consuming the entire CI job.
adb logcat -c
adb logcat -v threadtime > office-converter-logcat.txt 2>&1 &
LOGCAT_PID=$!
cleanup() {
  kill "$LOGCAT_PID" 2>/dev/null || true
  wait "$LOGCAT_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

set +e
timeout --signal=TERM --kill-after=30s 12m ./gradlew --no-daemon --console=plain --info --stacktrace -PciEmulator=true connectedDebugAndroidTest > office-converter-gradle.log 2>&1
TEST_STATUS=$?
set -e
cat office-converter-gradle.log

if [ "$TEST_STATUS" -ne 0 ]; then
  echo "Instrumentation command exited with status $TEST_STATUS."
  {
    echo "===== emulator properties ====="
    adb shell getprop || true
    echo "===== package service ====="
    adb shell service check package || true
    echo "===== running processes ====="
    adb shell ps -A || true
    echo "===== instrumentation status ====="
    adb shell dumpsys activity instrumentation || true
    echo "===== package manager status ====="
    adb shell dumpsys package || true
  } > office-converter-device-state.txt 2>&1
  exit "$TEST_STATUS"
fi

echo "OfficeConverter instrumentation tests completed successfully."
