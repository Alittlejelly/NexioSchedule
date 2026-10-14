# iOS native navigation prototype

> Historical validation notes. The formalized host no longer ships the trial menu or
> per-Screen scroll/inset parameters. See [IOS_NATIVE_DOCUMENT_HEADERS.md](IOS_NATIVE_DOCUMENT_HEADERS.md)
> for the current contract, rollout boundary and integration results.

## Scope

Only `changelog`, `license`, and `privacy_policy` use the new native document stack.
The timetable header, About page, other Compose routes, course detail/edit morphs,
wallpaper customization, and schedule-switch transitions are not migrated.

Entry: **My → About → Changelog**. The native ellipsis menu can push the other two
trial documents, including another instance of the same document. This menu is
an explicit prototype affordance, not the final product navigation.

```text
NativeSessionController
├── UINavigationController
│   ├── Transparent UINavigationBar (system buttons and UIMenu; no title)
│   └── Content transition container
│       ├── Retained AppHostController
│       │   ├── Main Compose controller + native tab bar
│       │   └── Legacy Compose navigation controller
│       └── Native document wrapper → retained Compose page controller
│           └── Original DocumentPageScaffold (title, collapse, progressive blur; no actions)
└── Floating Compose controller (hosted overlays and popups)
```

## Ownership

- `AppRouter.nativeRouteHandler` consumes only the three trial route kinds. The
  underlying Compose route stack does not change, so About remains alive below.
- `IosAppHost.nativeRouteIds` uses unique page-instance IDs, not route-kind IDs.
  Each page owns its list state. Completed pops save the last visible index/offset
  per document kind so reopening restores scroll without retaining popped controllers.
- UIKit pushes one instance at a time, retains its content controller, and reports
  the actual native stack depth only after a completed pop. A cancelled edge swipe
  must not remove a Kotlin entry or reset page state.
- Inactive retained root overlay chrome is kept composed but not drawn above a
  legacy secondary page or native document. Active popups remain above the native bar.
- Compose retains the original `DocumentPageScaffold` title, typography, 28 dp title
  padding, theme surface, scroll behavior, and progressive blur. Only its navigation
  actions are disabled (`showNavigationActions = false`).
- UIKit renders no title and uses a transparent, shadow-free bar. It owns only the
  native back button and menu; a page's title is exposed as an accessibility label.
- The child Compose controller removes the additional native bar safe-area overlap,
  retaining the actual window status-bar/notch inset. No second header padding is added.
- The native large-title/UIScrollView experiment was removed. Document scrolling
  remains in Compose, so existing collapse and blur behavior do not need a bridge.

## Motion

`NativeDocumentMotion` exports the existing Compose navigation tokens: enter
600 ms, exit 320 ms, parallax 0.24, dim 0.42, and the original easing functions.
The Swift animator samples the curves rather than maintaining copies. The Compose
page, including its original text header, is translated; the transparent native
navigation bar stays outside that content container and transitions its actions
independently. Reduced Motion uses a short crossfade.

The moving page is also clipped to the screen corner radius for the duration of
the transition, matching `PageLayer.clipDuringTransition` on the Compose side:
only the upper (incoming, or outgoing on pop) page is rounded — the revealed page
below must keep a square outline — and the clip is removed on completion so the
system's own screen rounding is not doubled. The radius comes from the same
`rememberScreenCornerRadiusPx` probe the Compose path uses; because Swift cannot
read Compose snapshot state, `IosAppHost.screenCornerRadiusPoints` carries it
across, and `0` (not yet resolved) simply means "do not clip".

The custom edge-swipe driver is linear while dragging. **Its finish/cancel settle
uses UIKit easing**, not Compose's remaining-global-curve continuation. New gestures
and menu pushes are gated while a navigation transition is in flight. Therefore
this prototype does not yet preserve interrupted push/reverse continuity, and it
is not a full replacement for `AppNavHost`. The stock UIKit pop drivers are disabled
to prevent two competing gesture owners; this trial implements an edge swipe, not
full-content swipe-back.

## Validation

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew -Pnexio.ios=true :ui-shared:iosSimulatorArm64Test checkKmpPurity
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO build
```

The nine Kotlin tests cover consumed/unconsumed routes, retained legacy metadata,
unique native instance IDs, successful native pop depth, invalid route filtering,
and the shared curve endpoints/monotonicity. These do not replace gesture UI tests.
A real-app XCTest smoke passed on iPhone 18 Pro: My → About → Changelog,
menu pushes to Privacy Policy and License, cancelled/completed edge swipes, native
back buttons, and return to the retained About layout without a native bar.
Latest evidence: `/tmp/native-doc-ui.b9kXL6/LatestSmoke.xcresult` (one test, zero
failures). This external harness is not an added project target. Reopened document
scroll restoration, dark mode, rotation, Reduce Motion, and intermediate-frame
navigation-bar motion still need dedicated UI checks.

The JVM test command was also attempted, but dependency resolution was blocked by
an HTTPS/TLS handshake failure downloading `lifecycle-runtime-compose-desktop`;
it is not recorded as a passing JVM validation.

Manual acceptance checks:

1. Enter Changelog from About; verify the original large title, theme surface, and
   28 dp leading padding, with one native back button and one native menu only.
2. Scroll to collapse the original title and reveal the original progressive blur.
   Native actions must stay fixed. Push License from the ellipsis menu and verify
   there is no duplicate native title or opaque native background.
3. Scroll License, push Privacy Policy, then return; the License position must remain.
4. Drag from the leading edge partially and cancel; title, menu, content, and depth
   must all return to the original state. Repeat with a completed swipe.
5. Return to About and the timetable; existing Compose state and morphs must remain.
6. Repeat in dark mode, landscape, and with Reduce Motion enabled.

Do not migrate the timetable header or complex full-screen overlays until these
checks pass and the custom settle/interruption policy is decided.
