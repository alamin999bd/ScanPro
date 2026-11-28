#!/bin/sh
set -eu

chmod +x gradlew
adb wait-for-device

boot_deadline=$(($(date +%s) + 600))
while :; do
  boot_state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [ "$boot_state" = "1" ]; then
    break
  fi
  if [ "$(date +%s)" -ge "$boot_deadline" ]; then
    echo "Emulator did not finish Android boot within 600 seconds."
    adb shell getprop || true
    exit 1
  fi
  sleep 5
done

services_deadline=$(($(date +%s) + 300))
while :; do
  package_status=$(adb shell service check package 2>/dev/null | tr -d '\r' || true)
  settings_status=$(adb shell service check settings 2>/dev/null | tr -d '\r' || true)
  if echo "$package_status" | grep -q found &&
     echo "$settings_status" | grep -q found &&
     adb shell cmd package list packages >/dev/null 2>&1; then
    break
  fi
  if [ "$(date +%s)" -ge "$services_deadline" ]; then
    echo "Android boot completed, but PackageManager/Settings did not become ready."
    echo "Package service: $package_status"
    echo "Settings service: $settings_status"
    adb logcat -d -t 1200 || true
    exit 1
  fi
  sleep 5
done

echo "Android boot and required system services are ready."
./gradlew --no-daemon --console=plain connectedDebugAndroidTest
