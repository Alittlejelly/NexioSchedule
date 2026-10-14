# Native header smoke tests

This standalone XCTest runner launches the existing app (`com.haooz.iosschedule`). It does not add a test target to the production Xcode project or compile production sources.

Build the simulator app separately, then run:

```sh
bash iosApp/Tests/run-native-header-smoke.sh
```

Defaults:

- App: `iosApp/DerivedData/Build/Products/Debug-iphonesimulator/NexioSchedule.app`
- Device: iPhone 18 Pro simulator `A8E4570F-6755-4BA5-AFEB-665E9FFA478B`
- Default quick coverage: Changelog, License, and Privacy Policy native routes
- Results: a fresh `/tmp/nexio-native-header-ui.*` directory containing logs, the result bundle, and exported screenshots

Override `APP_BUNDLE_PATH`, `SIMULATOR_UDID`, or `NATIVE_HEADER_ARTIFACTS` as needed. `NATIVE_HEADER_TEST_FILTER` selects one test method.

The full expanded-route suite is **opt-in** so the default command remains a core smoke check:

```sh
NATIVE_HEADER_EXPANDED_ROUTES=1 bash iosApp/Tests/run-native-header-smoke.sh
```

They also explicitly skip when invoked directly without the opt-in XCTest runner environment.

Production currently enables 18 native route kinds: Changelog, License, PrivacyPolicy, Communication, AppreciateAuthor, PreferenceSettings, UpdateSettings, LocalBackup, AiImport, WidgetIntro, CourseReminder, HolidaySettings, WebDavSettings, ScheduleExport, ScheduleBackup, ScheduleImport, About, and CourseTimeSettings. CourseManage and EducationalImport retain Compose navigation. CourseTimeSettings retains its route-local Compose card morph with native cancel/save controls. The bar stays visible: entry immediately selects editor controls, and exit immediately restores parent back. A parent-back tap during closing is accepted and deferred until save and disposal finish. UpdateSettings has no normal iOS entry, so this UI suite does not claim coverage of that hidden route.

## System appearance regression

The theme check is opt-in. Use a simulator fixture with automatic theme mode enabled,
no wallpaper, and no schedule-specific theme override. Run it once for each system appearance:

```sh
xcrun simctl ui booted appearance dark
TEST_RUNNER_SYSTEM_THEME_EXPECTED=dark \
  NATIVE_HEADER_TEST_FILTER=testAutomaticThemeColdLaunchAndManualOverride \
  bash iosApp/Tests/run-native-header-smoke.sh
# Repeat with appearance light and TEST_RUNNER_SYSTEM_THEME_EXPECTED=light.
```

The test checks cold launch before navigation, the retained settings tab, native preferences,
manual themes opposite to and matching the system, restoring automatic mode without popping,
and return to the retained root. It restores automatic mode after manual checks. The screenshot
assertion samples an empty page gutter; retained screenshots also permit native chrome inspection.
It does not automatically change system appearance mid-test. To check that path, keep the app
running and switch `xcrun simctl ui booted appearance light/dark` without navigating. Also switch
appearance while the app is backgrounded and activate it again. Wallpaper overrides and physical
hardware are separate checks.

## Native time-editor controls: final verification

- `/tmp/nexio-editor-native-uikit-ui/Smoke.xcresult`: two selected methods passed, zero failures. Verifies native cancel/save, section dialog cancellation, left-slot frame within 1 pt before/after entry and scrolling, keyboard cancellation, parent geometry within 3 pt, unchanged-draft native save, and rapid cancel/parent-back with no explicit test sleeps between those taps. XCTest may still wait for UIKit idle; exact first-frame timing and a continuously sampled button-morph trajectory are not claimed.
- Final inspected screenshots: `Attachments/E1B2EB9B-D83B-4E38-8B32-028C5A2EF945.png` (editor), `Attachments/1D21C435-462F-4E82-BECA-D32F36D51C09.png` (save return), relative to the final run directory.
- Final code uses separate native back/cancel UIBarButtonItems matched by the same iOS 26 identifier and animated navigation-item setters. The earlier SF Symbol-only replacement and animation-progress disabling are removed; content morph timings, geometry and easing remain unchanged.
- 25 iOS tests and seven JVM tests passed; purity, Android/desktop compilation and simulator/device builds passed. Kotlin checks: `/tmp/nexio-editor-native-intent-unit.log`; platform checks: `/tmp/nexio-editor-native-intent-platforms.log`; final builds: `/tmp/nexio-editor-native-uikit-build.log`, `/tmp/nexio-editor-native-uikit-device-build.log`.
- The existing app on physical iPhone 15 was updated and launched, without uninstalling or changing its data: `/tmp/nexio-editor-native-uikit-device-install.log`, `/tmp/nexio-editor-native-uikit-device-launch.log`. UI automation was only on the simulator, not on the user's physical phone.
- Earlier runs remain distinct: initial single editor method passed (`/tmp/nexio-editor-native-ui-pass1/Smoke.xcresult`); neutral-color confirmation passed Backup/WebDAV but failed editor cancellation (`/tmp/nexio-editor-native-ui-confirm/Smoke.xcresult`); destination-intent revision passed both editor methods (`/tmp/nexio-editor-native-intent-ui/Smoke.xcresult`). The first command also named a nonexistent WebDAV test, so it ran only one test; no second-method coverage is claimed for that run.
- Conflict validation is retained in the shared save callback but no new invalid-time UI fixture was created. Full-suite, dark mode, older OS, Reduce Motion and wide-screen native-editor coverage are not claimed.

## About and course-time follow-up (before native editor controls)

- `/tmp/nexio-about-time-phone-pass1/Smoke.xcresult`: four selected methods, three passed. The new time-settings test failed only at a nonexistent “返回” selector after entering the original editor; its close control is “取消”.
- `/tmp/nexio-about-time-phone-confirm/Smoke.xcresult`: two selected methods, both passed. About -> Communication/Donation -> About -> settings; time-settings native header -> section dialog cancellation -> original editor -> explicit keyboard -> cancel -> keyboard dismissed -> native parent restored within 3 pt -> settings. No configuration was edited or saved.
- The first batch additionally passed Changelog scroll restoration and cancelled/completed edge-pop, plus License/Privacy from native About. Assertions now expect native About on child return, not disappearance of all native bars.
- Actual screenshots inspected: first batch `Attachments/9B4C48F8-A287-4648-BE0B-6C7AF1873053.png` (native About); confirmation `Attachments/93CC23DB-32C5-405A-94C2-05927E1416D9.png` (restored time settings), `Attachments/D373BCC1-C654-4BCB-9E82-7D785FFF38E1.png` (Compose editor), `Attachments/FBDDC7A7-7BED-4A95-BD27-FFAF4CB32206.png` (section dialog).
- Final review then corrected the editor's raw status-bar blur-height calculation with native `topInsetAdjustment`. Compilation/unit tests are rerun for the final fix; these UI screenshots precede that adjustment and no third UI pass is claimed.
- The full 12-method suite was not rerun. The original iPad embedded-pane limitation below remains; wide-screen settings do not push these routes. No new dark-mode, Dynamic Type or hardware verification is claimed.

## Original fifteen-route verification results

All runs below used the real simulator app, not a mocked page. Result bundles contain `Info.plist`, logs, and exported `Attachments/manifest.json` unless explicitly noted.

| Run | Result | Finalized evidence |
| --- | --- | --- |
| iPhone full expanded suite | 11 tests: 10 passed, 1 failed | `/tmp/nexio-native-header-expanded-phone.hx8YRK/Smoke.xcresult` |
| iPhone final nested Backup/WebDAV confirmation | 1 test passed, 0 failures | `/tmp/nexio-native-header-webdav-final.7PsARR/Smoke.xcresult` |
| iPad final original-document entry confirmation | 1 test failed before entering a native document | `/tmp/nexio-native-header-ipad-final.I6zR02/Smoke.xcresult` |

All 11 iPhone test methods now have passing evidence across the full run and the final targeted confirmation. The suite has **not** been rerun together after the last test-only selector fix.

Passing coverage: original three documents and original About entries; one native back/no trial menu; original phone title padding; fixed header during scrolling; cancelled/completed edge back and reopened scroll state; Preferences/Reminder controls; About -> Communication/Donation; Widget dialog above native chrome; Holiday actual update tap (`更新中` spinner) and rotation; original Compose Import -> native AI Import -> retained Import; native Export entries/empty-schedule feedback; native Backup -> LocalBackup -> Backup; and native Backup -> WebDAV -> explicitly tapped `UITextView` -> keyboard exists -> native pop clears keyboard -> retained native Backup -> Compose settings.

The full run's sole failure was the test querying `TextField` instead of the existing UIKit `TextView`. The final targeted confirmation passed after correcting that selector. Earlier fling-timing failures were fixed by waiting for three stable frame samples before taking scroll baselines or tapping. **All original assertions and the 3-point scroll tolerance remain unchanged.** No production source was changed for these test corrections.

### iPad blocker and unverified limits

The corrected rail selector entered `我的` and selected `关于应用`, but its existing tablet detail pane stayed empty. At 834×1210 points, the right pane was `(421.5, 0, 412.5, 1210)` with no License/Privacy entries. `TabletSettingsScreen.kt` currently renders About through `androidPane?.invoke(selected) ?: Box(...)`; the iOS host does not supply that pane. This preserves the existing platform gap and blocks original document-entry verification. No third retry or production fix was performed.

System appearance was set to dark for that attempt and restored to its captured pre-run value afterward (already dark). The app screenshot was light, so **native iPad document geometry and dark theme are not verified**. Reduced Motion, Dynamic Type, real hardware, and hidden UpdateSettings coverage also remain unverified.

The Export fixture was empty; JSON export produced the existing `课表为空，无法导出` feedback. No courses were added/imported to force a chooser. Actual file-picker presentation/cancellation remains unverified.

### Inspected screenshots

Paths are relative to each run's directory:

- Phone full: `Attachments/17176A38-33FC-40C3-BE6E-3360959A0FF4.png` — Holiday busy state
- Phone full: `Attachments/F4D81240-648E-477F-8103-138AFFF65C0C.png` — Widget dialog above native chrome
- Phone full: `Attachments/7871A5FF-B4B9-4059-8325-55C6A5F3CECE.png` — Export's original file entries
- Phone final: `Attachments/A39B9FEC-9CCB-45AE-B879-7642150A0B68.png` — explicitly focused WebDAV input; keyboard existence was verified by XCTest, but this app-only screenshot does not visibly show the keyboard
- Phone final: `Attachments/BA03A89D-2E10-4D1C-9B63-59521EBF436F.png` — restored native Backup with no keyboard
- iPad final: `Attachments/35086872-ED63-4E87-AB53-2874FDAACF5C.png` — selected About and empty retained tablet detail pane

Earlier core diagnostics at `/tmp/nexio-native-header-core-diagnostic.dOsHYJ/Smoke.xcresult` were interrupted during Xcode failure-diagnostic finalization and lack `Info.plist`; they are **not finalized test evidence**. The runner now uses `-collect-test-diagnostics never` and warns about incomplete bundles.
