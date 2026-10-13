# iOS native document headers

## Contract

UIKit owns the transparent navigation bar, back button, page actions and leading-edge pop gesture. Compose still owns the original titles, collapse, progressive blur, body, bottom actions and dialogs. This is not a native-large-title or native-scroll migration.

`IosAppEntry.NexioIosRouteContent` is the single route assembly for both retained Compose navigation and native documents. Existing clipboard, file-controller, version and platform capability injections are reused. Native pages do not reimplement Screens or get separate ViewModel stores; the app's existing iOS ViewModel helpers remain app-scoped.

The trial ellipsis menu is removed. The three trial Screens have their original signatures and list-state ownership again. Page-scoped `LocalNativeDocumentChrome` lets `DocumentPageScaffold` hide its own navigation actions and consume only the extra UIKit bar overlap; Screens retain their original padding and scrolling logic.

## Ownership and state

- The native host consumes eligible pushes without changing the retained Compose stack. Every push has a unique page ID, including duplicate route kinds.
- `AppRouter.nativeBackHandler` consumes programmatic completion/back first when a native page exists. In particular, AI import completion cannot accidentally pop its retained Compose parent.
- A programmatic pop changes the requested depth, not the live page list. UIKit confirms the actual depth after successful completion. A cancelled gesture does not dispose a page or save/reset its state.
- Each native composition has its own `SaveableStateRegistry`. Completed pops snapshot the registered saveable values into route/occurrence keys (`license`, `license#2`, etc.), matching the retained Compose host's key policy. Reopening restores all registered saveable state, not only one list's index/offset.
- Popped controllers are not cached. Non-saveable `remember` state is fresh on reentry, and disposal remains a page-exit event. In-memory restoration does not claim process-death persistence.
- `NativeDocumentActionEffect` publishes the existing Holiday refresh and WebDAV test callbacks/state to UIKit. Their Compose actions still render normally when no native chrome is provided. Bottom backup/restore buttons remain in the original route.
- The floating Compose controller remains above native chrome for active dialogs/popups. Inactive retained root chrome stays composed without being drawn above documents.

## Enabled routes and rollout status

Seventeen route kinds are enabled; About and CourseTimeSettings were added after the original fifteen-route rollout:

- Changelog, License, PrivacyPolicy, Communication, AppreciateAuthor
- PreferenceSettings, UpdateSettings, LocalBackup, AiImport
- WidgetIntro, CourseReminder, HolidaySettings, WebDavSettings
- ScheduleExport, ScheduleBackup
- About, CourseTimeSettings

The full shared Screens and route callbacks are reused; only chrome is native. Expanded UI acceptance is run separately from the initial three-page gate. The first cancelled-pop anchor failure and the initial About footer tap failure were test synchronization issues: Compose fling motion is not included in XCTest's UIKit-idle detection. Tests now wait for stable geometry before sampling or tapping; no production safe-area workaround or relaxed assertion tolerance was applied.

## Migration boundary

CourseManage and EducationalImport remain in the retained Compose navigation stack. Main headers (including the schedule week header), course detail/edit morphs, wallpaper cutout and schedule-switch animations are not migrated.

About publishes page chrome directly and retains its original background, scroll state and Compose titles. Its five document children stay in the same native navigation stack. Embedded tablet About does not consume ancestor native chrome.

CourseTimeSettings reuses DocumentPageScaffold. Its existing TimeConfigEditScreen remains a route-local Compose morph, not a native push. NativeDocumentOverlayEffect suppresses native bar drawing, accessibility, edge-pop, page actions and outer programmatic pops until the editor finishes exiting. The native bar remains in the safe-area geometry (alpha zero, not setNavigationBarHidden), so the parent card anchors do not relayout. The editor consumes the extra bar overlap and also subtracts it from ProgressiveBlurTopBar's raw status-bar height. Dialogs continue to use the floating Compose surface. Android/desktop and the existing wide-screen embedded settings paths are unchanged.

The Import variant of BackupAndMigration has a live EducationalImport entry, but iOS has no WebHost and currently renders that route as empty Compose content. Migrating ScheduleImport would push this legacy page behind the native Import page. Therefore **ScheduleImport must remain Compose** until that boundary is explicitly resolved. The entry is not hidden and no new blank native page is introduced.

Export has no such outgoing route. Backup's LocalBackup and WebDAV children must be native before Backup is enabled. Eligibility is intentionally explicit; review outgoing route edges before adding a new native kind.

## Motion

The content animator samples the existing Kotlin enter/exit tokens: 600/320 ms, parallax 0.24, dim 0.42 and the original easing functions. UIKit transitions navigation items independently. Reduced Motion crossfades instead of translating.

Accepted difference: interactive dragging is linear; finish/cancel uses UIKit easing. New input is gated during native transitions. Interrupted Compose push/reverse continuation and full-content swipe-back are not claimed. Complex Compose transitions remain on their original paths.

## Validation

Use JDK 21:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew -Pnexio.ios=true :ui-shared:iosSimulatorArm64Test :ui-shared:jvmTest checkKmpPurity
./gradlew :ui-shared:compileAndroidMain :desktopApp:compileKotlinJvm
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO build
```

Unit checks cover unhosted semantics, native dispatch, native programmatic completion, reconciliation of an undisplayed push/pop, duplicate page identities/keys, saveable restoration, invalid depths, transition gating, action ownership, and shared motion curves. No real-device verification is implied.

The repository includes a test-only XCTest project in `iosApp/Tests/NativeHeaderSmoke.xcodeproj`; it drives an independently built/installed application and does not link production Swift or Kotlin sources. `iosApp/Tests/run-native-header-smoke.sh` installs the simulator build and records screenshots/results under a fresh temporary artifacts directory. Core acceptance must include original 28 pt title padding, one native back button with no trial menu, fixed chrome during scroll, cancelled/completed edge back, retained About layout, and saveable scroll restoration on reentry.

### About and course-time follow-up

- `iosSimulatorArm64Test`: 22 tests, zero failures; `jvmTest`: seven tests, zero failures (`/tmp/nexio-about-time-tests.log`). The tests now use retained Import as the legacy parent and separately verify nested native About and editor pop/action gating.
- Integrated simulator build and Android/desktop compilation passed (`/tmp/nexio-about-time-build.log`, `/tmp/nexio-about-time-platforms.log`).
- First phone batch: four methods, three passed; the time-settings method reached the editor but failed because the test looked for “返回” instead of its existing “取消” control (`/tmp/nexio-about-time-phone-pass1/Smoke.xcresult`). About -> Changelog cancelled/completed pop, restored scrolling, License/Privacy and Communication/Donation passed.
- Bounded phone confirmation: two methods, zero failures (`/tmp/nexio-about-time-phone-confirm/Smoke.xcresult`). Verified About -> leaf -> About -> settings, section dialog cancellation, existing time-editor morph, explicit keyboard focus, cancellation dismissing the keyboard, restored native header and parent coordinates within 3 pt, then settings return. No user data was edited or saved.
- A subsequent read-only review found that the editor's raw status-bar blur height still counted the hidden native bar. The final fix passes `topInsetAdjustment` to that editor; final compilation/unit checks cover the fix (`/tmp/nexio-about-time-tests-final.log`, `/tmp/nexio-about-time-platforms-final.log`, `/tmp/nexio-about-time-build-final.log`), but the recorded UI screenshots above precede it. No further UI rerun is claimed.
- Existing iPad wide-screen settings dispatch remains embedded rather than routed; its About pane is still unavailable without platform injection. This follow-up does not claim native headers for those embedded panes, iPad UI acceptance, dark mode, Dynamic Type or real hardware.

### Original fifteen-route recorded results

- Expanded `iosSimulatorArm64Test`: 20 tests, zero failures; `jvmTest`: seven tests, zero failures (`/tmp/nexio-native-header-expanded-tests.log`).
- `checkKmpPurity`, Android shared-UI compilation and desktop compilation passed.
- Expanded integrated Xcode Simulator app build passed (`/tmp/nexio-native-header-expanded-xcode.log`).
- Core Changelog tests passed against the integrated app, including cancelled/completed edge back, retained About layout and reopened scroll position with the unchanged 3 pt tolerance. A finalized result bundle is `/tmp/nexio-native-header-core-corrected.xu9NVa/Smoke.xcresult`; the independent completed-pop anchor restored to exactly 361.667 pt. That bundle also contains the initial License footer synchronization failure, so it is not described as an all-passing suite.
- The targeted License/Privacy test passed after waiting for stable geometry before tapping the existing About links. Test-side evidence paths are recorded in `iosApp/Tests/README.md`.
- Earlier failing diagnostic runs and incomplete result bundles remain documented as such, not counted as passes.
- Expanded iPhone suite: 11 tests, ten passed and one failed solely because the test queried `TextField` instead of the existing `UITextView` (`/tmp/nexio-native-header-expanded-phone.hx8YRK/Smoke.xcresult`). The corrected nested Backup/WebDAV test passed in a finalized targeted run (`/tmp/nexio-native-header-webdav-final.7PsARR/Smoke.xcresult`): input tap opened the keyboard, native pop dismissed it, the retained native Backup geometry matched within 3 pt, and another pop returned to Compose settings. All eleven phone test methods have passing evidence across those two runs; no single all-passing rerun is claimed.
- Phone UI also verified Holiday refresh/busy feedback and rotation, Widget dialog layering, original document/Preferences/Reminder content, Communication/Donation, LocalBackup nesting, Compose Import -> native AI -> retained Import, and Export's existing empty-schedule feedback. The fixture was empty, so populated export file-picker presentation/cancellation is not verified.
- iPad acceptance stopped at the existing empty About detail pane: `TabletSettingsScreen` falls back to an empty Box for About when `androidPane` is not supplied, and the iOS host has no such pane. The final original-entry test failed before entering a native document (`/tmp/nexio-native-header-ipad-final.I6zR02/Smoke.xcresult`). That unrelated platform gap was not changed to force a pass.
- Native iPad document geometry, dark theme, real hardware, Dynamic Type, hidden UpdateSettings UI and Reduce Motion UI acceptance are not claimed. The attempted iPad system-dark setting did not change the app's light screenshot. See `iosApp/Tests/README.md` for exact evidence and limitations.
