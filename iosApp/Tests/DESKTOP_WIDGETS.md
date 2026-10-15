# Desktop course widgets

The existing `CourseWidget` extension contains the Live Activity and one desktop widget, displayed as Course Schedule. It retains the `NexioTodayCourseWidget` kind and supports small, medium and large families. Small shows the current/next-course summary; medium and large show two and five course rows. Resizing switches layouts within the same widget.

The Kotlin `IosWidgetSnapshot` exporter resolves yesterday through day +30 using the shared teaching-week, holiday and swap-day rules. Swift writes `widget-snapshot.json` atomically in `group.com.haooz.iosschedule`. The extension does not load Kotlin or open the app database. The final exported day supports tomorrow preview; the snapshot expires at that day's midnight.

Course changes, schedule effects, holiday changes, reminder settings, and foreground entry refresh the snapshot. Identical display payloads do not request another WidgetKit reload. Existing timelines use course starts/ends, midnight, and tomorrow-preview boundaries, rather than minute-by-minute reload requests. WidgetKit controls actual scheduling and can delay updates.

## Signing and installation

Enable the `group.com.haooz.iosschedule` App Group for both `com.haooz.iosschedule` and `com.haooz.iosschedule.CourseWidget` in the Apple developer account. Both targets already declare the entitlement and Xcode capability. Regenerate provisioning profiles when necessary.

Simulator builds made with `CODE_SIGNING_ALLOWED=NO` do not carry App Group entitlements. For a local simulator install, ad-hoc sign the extension with `CourseWidget/CourseWidget.entitlements`, then the app with `iosApp/iosApp.entitlements`, before installing. Do not use these ad-hoc simulator signatures for device distribution.

Launch the app once to create the snapshot. Long-press the Home Screen, choose Edit > Add Widget, then search for Nexio. Add Course Schedule, then resize it between small, medium and large. Instances of the former separate Course Reminder widget need to be removed and replaced with Course Schedule.

## Lock Screen widget

`NexioLockScreenCourseWidget` is a separate gallery entry named Next Class (下一节课), supporting only `accessoryRectangular`. It reuses the shared snapshot/provider and receives its own reload request after snapshot writes. The desktop widget's kind and supported families are unchanged.

The three-line accessory shows status and the course time range, course name, then a full-width classroom line. Long classroom names truncate in the middle to preserve room numbers. Tomorrow previews use an explicit Tomorrow First Class label. Empty, holiday, not-started and stale states use the same resolved presentation as the desktop widget. Semantic foreground styles and a transparent container let WidgetKit compose the accessory with the Lock Screen wallpaper. There is no custom opaque card background in the production view.

To add it, long-press the Lock Screen, choose Customize > Lock Screen, tap the widget area below the clock, select Nexio, and add Next Class. Tap the accessory to open the app and synchronize the snapshot.

Preview galleries in `.pi/widget-previews/nexio-lockscreen-{dark,light}.png` render the production SwiftUI view at 160×72 points with sample data. The surrounding rounded tiles are gallery framing only. Environment-injected vibrant rendering checks layout; final Lock Screen wallpaper blending and system coloring still require an installed-device check.

## Timeline regression checks

With an iOS simulator already booted, run:

```sh
bash iosApp/Tests/run-desktop-widget-checks.sh
```

Set `SIMULATOR_UDID` to choose a specific booted device. The script compiles the production `DesktopCourseWidgets.swift` with the standalone `DesktopWidgetTimelineChecks.swift` runner, executes it in the simulator, and removes temporary build files. It does not install or modify the application, alter simulator appearance, or add a project target.

The 30 deterministic assertions cover the independently authored JSON contract, calendar weekday, inclusive starts/exclusive ends, remaining courses, tomorrow-preview gating and week changes, midnight carryover, missing/expired/unsupported snapshots, empty states, sorted and deduplicated event timelines, the 96-entry cap and renewal, and DST wall-clock time. They do not verify WidgetKit's actual delivery time, App Group signing, URL handling, or Home Screen tint compositing.

## Visual and behavior acceptance

Compare the Android baseline with the iOS widget at each supported size. Check light, dark, tinted, and clear Home Screen appearances. In accented rendering, the layout remains stable while the system owns the final color treatment; course bars/dots use accent grouping and active courses also have a textual indicator. A manually injected SwiftUI rendering environment is a layout preview, not evidence of final SpringBoard tint compositing.

Verify these flows with actual app data:

1. Edit/import/delete a course and switch schedules; return Home and confirm the displayed snapshot changes.
2. Turn notifications off; widgets must still show courses.
3. Enable tomorrow preview, then disable the reminder master switch; tomorrow preview must stop.
4. Check course start, course end, midnight, a cross-midnight course, and the configured tomorrow-preview time.
5. Check holiday/swap-day and teaching-week boundaries.
6. Check no courses, all courses finished, semester not started, and expired/missing snapshot recovery.
7. Check long course names, missing classrooms/teachers, large text, and iPad widget sizes.

Tap a widget to open the app and refresh the shared snapshot. Existing file-import URLs retain their previous handling.
