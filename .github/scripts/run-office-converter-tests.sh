#!/bin/sh
set -eu

chmod +x gradlew
adb wait-for-device

boot_deadline=$(($(date +%s) + 600))
while :; do
  boot_state=$(timeout 20s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot_state" = "1" ]; then break; fi
  if [ "$(date +%s)" -ge "$boot_deadline" ]; then
    echo "Emulator did not finish Android boot within 600 seconds."
    timeout 30s adb shell getprop || true
    timeout 30s adb logcat -d -t 1200 > office-converter-logcat.txt 2>&1 || true
    exit 1
  fi
  sleep 5
done

# Android can report boot_completed before PackageManager and the property
# service are responsive under software CPU emulation. Require repeated,
# bounded successful shell/property/package-manager probes before Gradle
# asks ddmlib to enumerate the device and determine its API level.
services_deadline=$(($(date +%s) + 600))
stable_probes=0
while :; do
  api_level=$(timeout 30s adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)
  abi=$(timeout 30s adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' || true)
  package_status=$(timeout 30s adb shell service check package 2>/dev/null | tr -d '\r' || true)
  settings_status=$(timeout 30s adb shell service check settings 2>/dev/null | tr -d '\r' || true)
  package_probe=1
  timeout 30s adb shell cmd package list packages >/dev/null 2>&1 || package_probe=0

  if [ "$api_level" = "30" ] &&
     [ -n "$abi" ] &&
     echo "$package_status" | grep -q found &&
     echo "$settings_status" | grep -q found &&
     [ "$package_probe" = "1" ] &&
     [ "$(adb get-state 2>/dev/null || true)" = "device" ]; then
    stable_probes=$((stable_probes + 1))
    echo "Responsive emulator probe $stable_probes/6: API $api_level, ABI $abi"
    if [ "$stable_probes" -ge 6 ]; then break; fi
  else
    stable_probes=0
    echo "Emulator services not yet responsive: API='$api_level', ABI='$abi', package='$package_status', settings='$settings_status', package_probe=$package_probe"
  fi

  if [ "$(date +%s)" -ge "$services_deadline" ]; then
    echo "Emulator did not provide six consecutive healthy service probes within 600 seconds."
    timeout 30s adb logcat -d -t 3000 > office-converter-logcat.txt 2>&1 || true
    exit 1
  fi
  sleep 10
done

echo "Emulator boot and required system services are stable."
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
    timeout 30s adb shell getprop || true
    echo "===== package service ====="
    timeout 30s adb shell service check package || true
    echo "===== running processes ====="
    timeout 30s adb shell ps -A || true
    echo "===== instrumentation status ====="
    timeout 30s adb shell dumpsys activity instrumentation || true
    echo "===== package manager status ====="
    timeout 30s adb shell dumpsys package || true
  } > office-converter-device-state.txt 2>&1
  exit "$TEST_STATUS"
fi

echo "OfficeConverter instrumentation tests completed successfully."
