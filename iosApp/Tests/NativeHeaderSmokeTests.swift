import XCTest

/// Runs against an already-installed simulator app; the runner does not link production sources.
final class NativeHeaderSmokeTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication(bundleIdentifier: "com.haooz.iosschedule")
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        settle()
    }

    override func tearDownWithError() throws {
        capture("Final state - \(name)")
        XCUIDevice.shared.orientation = .portrait
        app.terminate()
    }

    private func settle() { Thread.sleep(forTimeInterval: 0.8) }

    private func requireExpandedRoutes() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["NATIVE_HEADER_EXPANDED_ROUTES"] == "1",
                          "Expanded native routes are not enabled; opt in explicitly after migration")
    }

    private func stableFrame(of element: XCUIElement) -> CGRect {
        // Compose's fling is not included in XCTest's UIKit-idle detection. Wait for the
        // existing scrolling to stop rather than relaxing any post-navigation assertion.
        var previous = element.frame
        var stableSamples = 0
        for _ in 0..<16 {
            Thread.sleep(forTimeInterval: 0.4)
            let current = element.frame
            if abs(current.minY - previous.minY) < 0.5 {
                stableSamples += 1
                if stableSamples >= 3 { return current }
            } else {
                stableSamples = 0
            }
            previous = current
        }
        XCTFail("Scroll anchor did not stop moving before navigation: \(element.label) \(element.frame)")
        return element.frame
    }

    private func visibleContentAnchor() throws -> XCUIElement {
        // Changelog body text is merged into clickable cards and need not itself be hittable.
        // We only read geometry here; requiring hittability would reject visible static content.
        try XCTUnwrap(app.staticTexts.allElementsBoundByIndex.first {
            $0.exists && $0.label != "更新日志" && $0.label.count > 4 &&
                $0.frame.minY > 180 && $0.frame.maxY < app.frame.height - 100 &&
                $0.frame.height < 100
        }, "No visible content anchor after scroll: \(app.debugDescription)")
    }

    private func item(_ title: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", title)).firstMatch
    }

    @discardableResult
    private func open(_ title: String, file: StaticString = #filePath, line: UInt = #line) -> CGFloat {
        let target = item(title)
        _ = target.waitForExistence(timeout: 5)
        for _ in 0..<8 {
            if target.exists && target.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(target.exists && target.isHittable,
                      "Missing \(title): \(app.debugDescription)", file: file, line: line)
        let frame = stableFrame(of: target)
        XCTAssertTrue(target.exists && target.isHittable,
                      "Target stopped being tappable after scrolling: \(title): \(app.debugDescription)",
                      file: file, line: line)
        XCTAssertTrue(app.frame.contains(frame),
                      "Target is not fully in the visible viewport: \(title) \(frame)",
                      file: file, line: line)
        print("OPEN_DIAGNOSTIC title=\(title.debugDescription) stableFrame=\(frame) " +
              "hittable=\(target.isHittable)")
        print("OPEN_DIAGNOSTIC beforeTapTree=\(app.debugDescription)")
        let y = frame.minY
        target.tap()
        settle()
        print("OPEN_DIAGNOSTIC title=\(title.debugDescription) afterTapTree=\(app.debugDescription)")
        return y
    }

    private func showSettings() {
        if !app.tabBars.buttons["我的"].waitForExistence(timeout: 5), item("完成").exists {
            item("完成").tap()
            settle()
        }
        if app.tabBars.buttons["我的"].exists {
            app.tabBars.buttons["我的"].tap()
        } else if app.tabBars.buttons["设置"].exists {
            app.tabBars.buttons["设置"].tap()
        } else {
            // Expanded width uses the original Compose rail, whose merged label includes its icon.
            let railMy = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "我的")).firstMatch
            if railMy.exists {
                _ = stableFrame(of: railMy)
                XCTAssertTrue(railMy.isHittable && app.frame.contains(railMy.frame), app.debugDescription)
                railMy.tap()
            } else {
                open("设置")
            }
        }
        settle()
    }

    private func requireNativeHeader(_ title: String,
                                     file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(app.navigationBars.firstMatch.waitForExistence(timeout: 10),
                      app.debugDescription, file: file, line: line)
        settle()
        XCTAssertEqual(app.navigationBars.count, 1, file: file, line: line)
        XCTAssertEqual(app.navigationBars.firstMatch.label, title, file: file, line: line)
        XCTAssertFalse(app.navigationBars.buttons["更多文档"].exists,
                       "Prototype document-jump menu must not ship", file: file, line: line)
    }

    private func nativeBack() {
        let back = app.navigationBars.firstMatch.buttons.element(boundBy: 0)
        XCTAssertTrue(back.exists && back.isHittable, app.debugDescription)
        back.tap()
        settle()
    }

    private func capture(_ name: String) {
        guard app != nil else { return }
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testDocumentHeaderScrollRestorationAndEdgeBack() throws {
        showSettings()
        open("关于应用")
        requireNativeHeader("关于应用")
        let aboutChangelogY = open("更新日志")
        requireNativeHeader("更新日志")
        XCTAssertEqual(app.navigationBars.buttons.count, 1, "Document has only native back")
        XCTAssertEqual(app.keyboards.count, 0, "Opening a read-only document raised a keyboard")

        let titles = app.staticTexts.matching(NSPredicate(format: "label == %@", "更新日志"))
            .allElementsBoundByIndex
        let largeTitle = try XCTUnwrap(titles.first { $0.frame.height > 30 },
                                       "Missing original Compose large title: \(app.debugDescription)")
        if app.frame.width < 600 {
            XCTAssertEqual(largeTitle.frame.minX, 28, accuracy: 2,
                           "Original phone title leading padding changed")
        }
        let nativeBackY = app.navigationBars.buttons.element(boundBy: 0).frame.minY
        capture("Document - original title and native back")
        app.swipeUp()
        settle()
        XCTAssertEqual(app.navigationBars.buttons.element(boundBy: 0).frame.minY,
                       nativeBackY, accuracy: 1, "Native back moved with Compose scrolling")
        capture("Document - collapsed title and progressive blur")

        // Use a real visible row as the scroll anchor, not header state or a screenshot approximation.
        let scrollAnchor = try visibleContentAnchor()
        let anchorLabel = scrollAnchor.label
        let diagnosticStart = Date()
        func sample(_ phase: String) {
            print("SCROLL_DIAGNOSTIC phase=\(phase) elapsed=\(Date().timeIntervalSince(diagnosticStart)) " +
                  "anchor=\(anchorLabel.debugDescription) frame=\(item(anchorLabel).frame) " +
                  "nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        }
        sample("afterSwipeUp_0.8s")
        settle()
        sample("afterSwipeUp_1.6s")
        settle()
        sample("afterSwipeUp_2.4s")
        let anchorY = stableFrame(of: item(anchorLabel)).minY
        sample("stableBaselineBeforeCancel")

        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.42))
            .withOffset(CGVector(dx: 2, dy: 0))
        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.18, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.4)
        settle()
        sample("afterCancel_0.8s")
        capture("Document - cancelled edge back before assertion")
        settle()
        sample("afterCancel_1.6s")
        settle()
        sample("afterCancel_2.4s")
        requireNativeHeader("更新日志")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Cancelled edge back changed scroll state")

        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.2)
        settle()
        requireNativeHeader("关于应用")
        XCTAssertEqual(item("更新日志").frame.minY, aboutChangelogY, accuracy: 3,
                       "Retained About layout changed")
        capture("Document - returned to retained About")

        open("更新日志")
        requireNativeHeader("更新日志")
        XCTAssertTrue(item(anchorLabel).exists, "Saved document row disappeared on reopen")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Reopened document did not restore the original scroll position")
        capture("Document - reopened saveable scroll state")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    func testDocumentCompletedEdgeBackAndReopenWithoutCancellation() throws {
        showSettings()
        open("关于应用")
        let aboutChangelogY = open("更新日志")
        requireNativeHeader("更新日志")
        app.swipeUp()
        settle()
        settle()
        settle()
        let scrollAnchor = try visibleContentAnchor()
        let anchorLabel = scrollAnchor.label
        let anchorY = stableFrame(of: item(anchorLabel)).minY
        print("COMPLETED_POP_DIAGNOSTIC before anchor=\(anchorLabel.debugDescription) y=\(anchorY) " +
              "nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        capture("Document - stable scroll before completed pop")
        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0.42))
            .withOffset(CGVector(dx: 2, dy: 0))
        edge.press(forDuration: 0.05,
                   thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.42)),
                   withVelocity: .slow, thenHoldForDuration: 0.2)
        settle()
        requireNativeHeader("关于应用")
        XCTAssertEqual(item("更新日志").frame.minY, aboutChangelogY, accuracy: 3,
                       "Retained About layout changed")
        capture("Document - completed pop without preceding cancellation")
        open("更新日志")
        requireNativeHeader("更新日志")
        print("COMPLETED_POP_DIAGNOSTIC reopen anchor=\(anchorLabel.debugDescription) " +
              "frame=\(item(anchorLabel).frame) nativeBack=\(app.navigationBars.buttons.element(boundBy: 0).frame)")
        capture("Document - reopened scroll before assertion")
        XCTAssertTrue(item(anchorLabel).exists, "Saved document row disappeared on reopen")
        XCTAssertEqual(item(anchorLabel).frame.minY, anchorY, accuracy: 3,
                       "Reopened document did not restore original scroll position")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    func testLicenseAndPrivacyUseOriginalAboutEntries() throws {
        showSettings()
        open("关于应用")
        open("开源协议")
        requireNativeHeader("开源协议")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(app.staticTexts["一、开源协议摘要"].exists)
        XCTAssertEqual(app.keyboards.count, 0)
        capture("License - native back from original About entry")
        nativeBack()
        requireNativeHeader("关于应用")
        open("《隐私政策》")
        requireNativeHeader("隐私政策")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(app.staticTexts["Nexio 课程表 隐私政策"].exists)
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Privacy - native back from original About entry")
        nativeBack()
        requireNativeHeader("关于应用")
    }

    func testPreferenceHeaderPreservesOriginalControlsAndLegacyReturn() throws {
        try requireExpandedRoutes()
        showSettings()
        open("应用偏好设置")
        requireNativeHeader("应用偏好设置")
        XCTAssertTrue(item("自动切换深色模式").exists, "Original theme control missing")
        XCTAssertTrue(item("深色模式").exists, "Original theme selection missing")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Preferences - native back with original Compose controls")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
        XCTAssertTrue(app.tabBars.buttons["我的"].exists || item("设置").exists)
    }

    func testHolidayNativeActionAndRotation() throws {
        try requireExpandedRoutes()
        showSettings()
        open("节假日与调休")
        requireNativeHeader("节假日与调休")
        XCTAssertTrue(app.navigationBars.buttons["更新"].exists,
                      "Original refresh action missing from native header")
        XCTAssertEqual(app.navigationBars.buttons.count, 2)
        capture("Holiday - native update action")
        app.navigationBars.buttons["更新"].tap()
        // A fast response may finish before XCTest snapshots the spinner. Accept either
        // original feedback state; this check deliberately does not require network success.
        let busy = item("更新中")
        let update = app.navigationBars.buttons["更新"]
        XCTAssertTrue(busy.exists || update.exists,
                      "Refresh tap removed both busy and normal action states: \(app.debugDescription)")
        print("HOLIDAY_ACTION_DIAGNOSTIC busy=\(busy.exists) update=\(update.exists) " +
              "tree=\(app.debugDescription)")
        capture("Holiday - original refresh tap feedback")
        if busy.exists {
            _ = update.waitForExistence(timeout: 15)
        }
        XCUIDevice.shared.orientation = .landscapeLeft
        settle()
        requireNativeHeader("节假日与调休")
        XCTAssertTrue(app.navigationBars.buttons["更新"].isHittable || item("更新中").exists,
                      "Neither the update action nor its original busy state is visible")
        capture("Holiday - landscape header")
        XCUIDevice.shared.orientation = .portrait
        settle()
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testNativeBackupNestedLocalBackupWebDavKeyboardAndBottomActions() throws {
        try requireExpandedRoutes()
        showSettings()
        open("课表备份")
        requireNativeHeader("课表备份")
        let localBackupY = open("本地备份")
        requireNativeHeader("本地备份")
        XCTAssertTrue(item("备份模式").exists, "Original local backup mode control missing")
        capture("Local backup - native nested page with original controls")
        nativeBack()
        requireNativeHeader("课表备份")
        XCTAssertEqual(item("本地备份").frame.minY, localBackupY, accuracy: 3,
                       "Nested LocalBackup pop changed retained native Backup state")
        let backupWebDavY = open("WebDAV 云备份")
        requireNativeHeader("WebDAV 云备份")
        XCTAssertTrue(app.navigationBars.buttons["测试连接"].exists,
                      "Connection test action missing from native header")
        XCTAssertTrue(item("备份到云端").exists, "Original bottom backup button missing")
        XCTAssertTrue(item("从云端恢复").exists, "Original bottom restore button missing")
        XCTAssertEqual(app.keyboards.count, 0, "Navigation raised keyboard without user input")
        capture("WebDAV - original inputs and bottom actions")
        app.navigationBars.buttons["测试连接"].tap()
        let feedback = app.staticTexts.matching(NSPredicate(format:
            "label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@ OR label CONTAINS %@",
            "连接成功", "连接失败", "认证失败", "服务器返回", "请先配置服务器信息")).firstMatch
        XCTAssertTrue(feedback.waitForExistence(timeout: 15),
                      "Connection test has no original status/configuration feedback: \(app.debugDescription)")
        print("WEBDAV_ACTION_DIAGNOSTIC feedback=\(feedback.label.debugDescription) " +
              "tree=\(app.debugDescription)")
        capture("WebDAV - original connection test feedback")

        // The existing CMP NativeTextField is a UIKit UITextView on iOS, not UITextField.
        let textField = app.textViews.firstMatch
        XCTAssertTrue(textField.waitForExistence(timeout: 5), app.debugDescription)
        textField.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5),
                      "Explicit input tap did not open the keyboard")
        capture("WebDAV - explicitly opened native keyboard")
        nativeBack()
        requireNativeHeader("课表备份")
        XCTAssertEqual(app.keyboards.count, 0, "Native page keyboard remained after pop")
        XCTAssertEqual(item("WebDAV 云备份").frame.minY, backupWebDavY, accuracy: 3,
                       "Retained native Backup scroll state changed")
        capture("WebDAV - returned to retained native Backup")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0, "Popping Backup did not return to Compose settings")
    }

    func testNativeAboutLeafPagesPreserveOriginalContent() throws {
        try requireExpandedRoutes()
        showSettings()
        open("关于应用")
        open("交流与反馈")
        requireNativeHeader("交流与反馈")
        XCTAssertTrue(item("联系我们").exists)
        XCTAssertTrue(item("加入交流群").exists)
        capture("Communication - native header with original entry actions")
        nativeBack()
        requireNativeHeader("关于应用")
        open("捐赠支持")
        requireNativeHeader("捐赠支持")
        XCTAssertGreaterThan(app.staticTexts.count, 2, "Original donation content missing")
        capture("Donation - native header with original Compose content")
        nativeBack()
        requireNativeHeader("关于应用")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0, "About did not return to settings")
    }

    func testCourseReminderNativeLeafPreservesOriginalControls() throws {
        try requireExpandedRoutes()
        showSettings()
        open("课程提醒")
        requireNativeHeader("课程提醒")
        XCTAssertTrue(item("开启提醒").exists, "Original reminder toggle missing")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Reminder - native header with original controls")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testNativeExportOriginalFileEntriesAndCancelOnly() throws {
        try requireExpandedRoutes()
        showSettings()
        open("课表导出")
        requireNativeHeader("课表导出")
        XCTAssertTrue(item("JSON 格式导出").exists, "Original JSON export entry missing")
        XCTAssertTrue(item("ICS 格式导出").exists, "Original ICS export entry missing")
        capture("Export - original JSON and ICS entries")
        open("JSON 格式导出")
        let emptyFeedback = app.staticTexts.matching(NSPredicate(format:
            "label CONTAINS %@", "课表为空，无法导出")).firstMatch
        if emptyFeedback.exists {
            print("EXPORT_DIAGNOSTIC no populated fixture: \(emptyFeedback.label)")
            capture("Export - original empty-schedule feedback, no user data created")
        } else {
            let cancelEnglish = app.buttons["Cancel"].firstMatch
            let cancelChinese = app.buttons["取消"].firstMatch
            let pickerCancel = cancelEnglish.waitForExistence(timeout: 10) ? cancelEnglish : cancelChinese
            XCTAssertTrue(pickerCancel.exists && pickerCancel.isHittable,
                          "Export did not present a cancellable system file picker: \(app.debugDescription)")
            capture("Export - system file picker before cancellation")
            pickerCancel.tap()
            settle()
            requireNativeHeader("课表导出")
            capture("Export - picker cancelled without saving user data")
        }
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testLegacyImportToNativeAiImportAndBackWithoutImporting() throws {
        try requireExpandedRoutes()
        showSettings()
        open("课表导入")
        XCTAssertEqual(app.navigationBars.count, 0,
                       "Import must retain Compose because it still reaches EducationalImport")
        let aiEntryY = open("AI 文本导入")
        requireNativeHeader("AI 文本导入")
        XCTAssertTrue(item("课表数据").exists, "Original AI input section missing")
        XCTAssertEqual(app.keyboards.count, 0, "AI navigation unexpectedly opened the keyboard")
        capture("AI import - native page reached from legacy Import")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
        XCTAssertEqual(item("AI 文本导入").frame.minY, aiEntryY, accuracy: 3,
                       "Returning from AI changed retained Compose Import state")
        XCTAssertTrue(item("教务系统导入").exists, "Original unsupported entry was silently changed")
        capture("Import - returned to original Compose entries without importing")
    }

    func testTimeSettingsNativeHeaderDialogsAndComposeEditorReturn() throws {
        showSettings()
        open("课表节数与时间")
        requireNativeHeader("课表节数与时间")
        XCTAssertEqual(app.navigationBars.buttons.count, 1)
        XCTAssertTrue(item("作息方案").exists)
        let sectionY = open("课表节数")
        XCTAssertTrue(item("课表节数设置").exists)
        XCTAssertTrue(item("取消").isHittable, "Section dialog is covered by native chrome")
        capture("Time settings - section dialog above native chrome")
        item("取消").tap()
        settle()
        requireNativeHeader("课表节数与时间")
        XCTAssertEqual(item("课表节数").frame.minY, sectionY, accuracy: 3)
        let routineSummary = app.staticTexts.matching(NSPredicate(format:
            "label == %@ OR label ENDSWITH %@", "始终生效", "生效")).firstMatch
        XCTAssertTrue(routineSummary.exists && routineSummary.isHittable, app.debugDescription)
        routineSummary.tap()
        settle()
        XCTAssertTrue(item("编辑作息").waitForExistence(timeout: 5), app.debugDescription)
        XCTAssertEqual(app.navigationBars.count, 0, "Native bar must not cover the existing morph editor")
        XCTAssertTrue(item("取消").isHittable, app.debugDescription)
        capture("Time settings - existing Compose morph editor")
        app.textViews.firstMatch.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        item("取消").tap()
        settle()
        requireNativeHeader("课表节数与时间")
        XCTAssertEqual(app.keyboards.count, 0, "Morph editor keyboard remained after cancellation")
        XCTAssertEqual(item("课表节数").frame.minY, sectionY, accuracy: 3,
                       "Editor exit changed retained settings geometry")
        capture("Time settings - native header restored after morph exit")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }

    func testWidgetGuideDialogAboveNativeChrome() throws {
        try requireExpandedRoutes()
        showSettings()
        open("桌面小部件")
        requireNativeHeader("桌面小部件")
        open("添加到桌面")
        XCTAssertTrue(item("添加桌面小部件").exists, "Original guide dialog missing")
        XCTAssertTrue(item("我知道了").isHittable, "Dialog is covered by native navigation")
        XCTAssertEqual(app.keyboards.count, 0)
        capture("Widget - original guide dialog above native chrome")
        item("我知道了").tap()
        settle()
        requireNativeHeader("桌面小部件")
        nativeBack()
        XCTAssertEqual(app.navigationBars.count, 0)
    }
}
