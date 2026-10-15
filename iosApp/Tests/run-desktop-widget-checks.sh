#!/usr/bin/env bash
# Runs deterministic snapshot/timeline regression checks against the production
# widget source. Requires Xcode and a booted iOS simulator; no app, signing,
# dependency installation, or project modification is needed.
#
# Usage:
#   bash iosApp/Tests/run-desktop-widget-checks.sh
#   SIMULATOR_UDID=<booted-device-uuid> bash iosApp/Tests/run-desktop-widget-checks.sh
set -euo pipefail

widget_test_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
widget_repo_root="$(cd -- "$widget_test_dir/../.." && pwd)"
widget_sdk_path="$(xcrun --sdk iphonesimulator --show-sdk-path)"
widget_test_device="${SIMULATOR_UDID:-booted}"
widget_test_arch="$(uname -m)"
widget_test_output="$(mktemp -d /tmp/nexio-desktop-widget-checks.XXXXXX)"
trap 'rm -rf -- "$widget_test_output"' EXIT

# Check access before compiling, and leave the simulator's boot/app state alone.
if ! xcrun simctl spawn "$widget_test_device" /usr/bin/true; then
    echo "Boot an iOS simulator first, or set SIMULATOR_UDID to a booted device." >&2
    exit 1
fi

xcrun --sdk iphonesimulator swiftc \
    -sdk "$widget_sdk_path" \
    -target "$widget_test_arch-apple-ios26.1-simulator" \
    "$widget_repo_root/iosApp/CourseWidget/DesktopCourseWidgets.swift" \
    "$widget_test_dir/DesktopWidgetTimelineChecks.swift" \
    -o "$widget_test_output/DesktopWidgetTimelineChecks"

xcrun simctl spawn "$widget_test_device" "$widget_test_output/DesktopWidgetTimelineChecks"
