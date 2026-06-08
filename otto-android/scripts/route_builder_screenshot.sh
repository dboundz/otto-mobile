#!/usr/bin/env bash
# Boot emulator (if needed), run Route Builder map instrumented tests, capture adb screencap.
# Usage: ./scripts/route_builder_screenshot.sh [AVD_NAME]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${HOME}/Library/Android/sdk}"
EMULATOR="${ANDROID_HOME}/emulator/emulator"
ADB="${ANDROID_HOME}/platform-tools/adb"
AVD="${1:-Pixel_10}"
ARTIFACT_DIR="${ROOT}/app/build/route-builder-test-artifacts"
SCREENSHOT="${ARTIFACT_DIR}/route-builder-emulator-screencap.png"

mkdir -p "${ARTIFACT_DIR}"

boot_emulator() {
  if "${ADB}" devices | grep -q 'device$'; then
    echo "Device/emulator already connected."
    return
  fi
  echo "Starting AVD ${AVD}..."
  "${EMULATOR}" -avd "${AVD}" -no-snapshot-load -gpu swiftshader_indirect >/dev/null 2>&1 &
  "${ADB}" wait-for-device
  until "${ADB}" shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do
    sleep 2
  done
  echo "Emulator booted."
}

MAPBOX_TOKEN="$(grep '^MAPBOX_ACCESS_TOKEN=' "${HOME}/.gradle/gradle.properties" 2>/dev/null | cut -d= -f2- || true)"
GRADLE_MAPBOX_ARGS=()
if [[ -n "${MAPBOX_TOKEN}" ]]; then
  GRADLE_MAPBOX_ARGS=(-PMAPBOX_ACCESS_TOKEN="${MAPBOX_TOKEN}")
fi

boot_emulator

cd "${ROOT}"
./gradlew :app:installDebug :app:connectedDebugAndroidTest \
  "${GRADLE_MAPBOX_ARGS[@]}" \
  -Pandroid.testInstrumentationRunnerArguments.class=to.ottomot.driftd.routebuilder.RouteBuilderMapMarkerInstrumentedTest

"${ADB}" exec-out screencap -p > "${SCREENSHOT}"
echo "Screenshot: ${SCREENSHOT}"

PULLED="$("${ADB}" shell ls /sdcard/Download/otto-route-builder-test/ 2>/dev/null | tr -d '\r' || true)"
if [[ -n "${PULLED}" ]]; then
  for file in ${PULLED}; do
    "${ADB}" pull "/sdcard/Download/otto-route-builder-test/${file}" "${ARTIFACT_DIR}/${file}" || true
  done
fi

echo "Done. Agent can read PNGs under ${ARTIFACT_DIR}"
