#!/bin/bash
# Build production separately, then test the installed app without changing its sources or target.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEVICE="${SIMULATOR_UDID:-A8E4570F-6755-4BA5-AFEB-665E9FFA478B}"
APP="${APP_BUNDLE_PATH:-$ROOT/iosApp/DerivedData/Build/Products/Debug-iphonesimulator/NexioSchedule.app}"
ARTIFACTS="${NATIVE_HEADER_ARTIFACTS:-$(mktemp -d /tmp/nexio-native-header-ui.XXXXXX)}"
mkdir -p "$ARTIFACTS"
if [[ ! -d "$APP" ]]; then
  echo "Missing simulator app: $APP. Build iosApp first, or set APP_BUNDLE_PATH." >&2
  exit 1
fi
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl install "$DEVICE" "$APP"
FILTER=()
# Xcode forwards TEST_RUNNER_* variables to the XCTest runner without this prefix.
export TEST_RUNNER_NATIVE_HEADER_EXPANDED_ROUTES="${NATIVE_HEADER_EXPANDED_ROUTES:-0}"
if [[ -n "${NATIVE_HEADER_TEST_FILTER:-}" ]]; then
  FILTER+=("-only-testing:NativeHeaderSmokeTests/NativeHeaderSmokeTests/$NATIVE_HEADER_TEST_FILTER")
elif [[ "${NATIVE_HEADER_EXPANDED_ROUTES:-0}" != "1" ]]; then
  # Keep the default run focused on the three original documents; expanded acceptance is opt-in.
  for TEST in testDocumentHeaderScrollRestorationAndEdgeBack \
              testDocumentCompletedEdgeBackAndReopenWithoutCancellation \
              testLicenseAndPrivacyUseOriginalAboutEntries; do
    FILTER+=("-only-testing:NativeHeaderSmokeTests/NativeHeaderSmokeTests/$TEST")
  done
fi
printf 'Artifacts: %s\n' "$ARTIFACTS"
set +e
xcodebuild -project "$ROOT/iosApp/Tests/NativeHeaderSmoke.xcodeproj" \
  -scheme NativeHeaderSmokeTests -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE" \
  -derivedDataPath "$ARTIFACTS/DerivedData" \
  -resultBundlePath "$ARTIFACTS/Smoke.xcresult" \
  -parallel-testing-enabled NO -collect-test-diagnostics never CODE_SIGNING_ALLOWED=NO \
  ${FILTER[@]+"${FILTER[@]}"} test 2>&1 | tee "$ARTIFACTS/xcodebuild.log"
STATUS=${PIPESTATUS[0]}
set -e
if [[ -f "$ARTIFACTS/Smoke.xcresult/Info.plist" ]]; then
  xcrun xcresulttool export attachments --path "$ARTIFACTS/Smoke.xcresult" \
    --output-path "$ARTIFACTS/Attachments" || echo "Attachment export failed; inspect the result bundle." >&2
elif [[ -d "$ARTIFACTS/Smoke.xcresult" ]]; then
  echo "Incomplete xcresult (missing Info.plist); do not treat this as finalized test evidence." >&2
fi
printf 'Artifacts: %s\n' "$ARTIFACTS"
exit "$STATUS"
