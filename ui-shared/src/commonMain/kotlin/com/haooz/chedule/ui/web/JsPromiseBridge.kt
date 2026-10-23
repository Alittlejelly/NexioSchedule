package com.haooz.chedule.ui.web

/**
 * 注入到 WebView 的 Promise 桥接基础设施。脚本通过 `window.AndroidBridgePromise` 调用 Native 方法并等待 Promise 回调。
 *
 * 2026-10-13 从 androidApp `WebCompatDelegate.kt` 上提到此，让 iOS 的 WakeUp 无头导入复用同一份桥（否则两端各写一份 JS，语义必然漂移）。
 *
 * ⚠ 与云端脚本仓库是契约：`window.AndroidBridge` / `window.shiguangBridge` 的名、方法名、参数顺序都是脚本侧写死的，改一个字 WakeUp / 教务脚本就整体失效。名字里带 "Android" 是历史遗留，别顺手改成中性名。
 */
val JS_PROMISE_BRIDGE = """
    (function() {
        if (window._resolveAndroidPromise) return;
        window._androidPromiseResolvers = {};
        window._androidPromiseRejectors = {};

        window._resolveAndroidPromise = function(promiseId, result) {
            if (window._androidPromiseResolvers[promiseId]) {
                window._androidPromiseResolvers[promiseId](result);
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window._rejectAndroidPromise = function(promiseId, error) {
            if (window._androidPromiseRejectors[promiseId]) {
                window._androidPromiseRejectors[promiseId](new Error(error));
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window.AndroidBridgePromise = {
            showAlert: function(title, content, confirmText) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'alert_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showAlert(title, content, confirmText, promiseId);
                });
            },
            showPrompt: function(title, tip, defaultText, validatorJsFunction) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'prompt_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showPrompt(title, tip, defaultText, validatorJsFunction, promiseId);
                });
            },
            showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'singleSelect_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showSingleSelection(title, itemsJsonString, defaultSelectedIndex, promiseId);
                });
            },
            saveImportedCourses: function(coursesJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCourses_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveImportedCourses(coursesJsonString, promiseId);
                });
            },
            saveCourseConfig: function(configJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveConfig_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveCourseConfig(configJsonString, promiseId);
                });
            },
            savePresetTimeSlots: function(timeSlotsJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveTimeSlots_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.savePresetTimeSlots(timeSlotsJsonString, promiseId);
                });
            },
            saveComboSchedule: function(comboJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCombo_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveComboSchedule(comboJsonString, promiseId);
                });
            }
        };

        // V2 桥接对象别名（shiguang Bridge）：业务接口完全不变，仅对象改名
        // window.shiguangBridge          -> AndroidBridge（同步：showToast、notifyTaskCompletion 等）
        // window.shiguangBridgePromise   -> AndroidBridgePromise（异步：showAlert、saveImportedCourses 等）
        window.shiguangBridge = window.shiguangBridge || window.AndroidBridge;
        window.shiguangBridgePromise = window.shiguangBridgePromise || window.AndroidBridgePromise;
    })();
""".trimIndent()
