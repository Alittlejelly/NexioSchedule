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

Fifteen ordinary document routes are enabled after core acceptance:

- Changelog, License, PrivacyPolicy, Communication, AppreciateAuthor
- PreferenceSettings, UpdateSettings, LocalBackup, AiImport
- WidgetIntro, CourseReminder, HolidaySettings, WebDavSettings
- ScheduleExport, ScheduleBackup

The full shared Screens and route callbacks are reused; only chrome is native. Expanded UI acceptance is run separately from the initial three-page gate. The first cancelled-pop anchor failure and the initial About footer tap failure were test synchronization issues: Compose fling motion is not included in XCTest's UIKit-idle detection. Tests now wait for stable geometry before sampling or tapping; no production safe-area workaround or relaxed assertion tolerance was applied.

## Migration boundary

About, CourseManage, CourseTimeSettings and EducationalImport remain in the retained Compose navigation stack. Main headers, course detail/edit morphs, wallpaper cutout and schedule-switch animations are not migrated.

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

### Recorded results

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
