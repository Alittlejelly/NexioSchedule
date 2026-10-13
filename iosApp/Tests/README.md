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

Production currently enables 15 native route kinds: Changelog, License, PrivacyPolicy, Communication, AppreciateAuthor, PreferenceSettings, UpdateSettings, LocalBackup, AiImport, WidgetIntro, CourseReminder, HolidaySettings, WebDavSettings, ScheduleExport, and ScheduleBackup. About, CourseManage, CourseTimeSettings, ScheduleImport, and EducationalImport retain Compose navigation. UpdateSettings has no normal iOS entry, so this UI suite does not claim coverage of that hidden route.

## Verification results

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
