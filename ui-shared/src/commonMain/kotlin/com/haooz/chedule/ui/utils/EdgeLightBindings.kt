package com.haooz.chedule.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.edgelight.EdgeLight
import com.kyant.backdrop.edgelight.rememberCardEdgeLight as backdropRememberCardEdgeLight
import com.kyant.backdrop.edgelight.rememberCourseCardEdgeLight as backdropRememberCourseCardEdgeLight
import com.kyant.backdrop.edgelight.rememberDefaultEdgeLight as backdropRememberDefaultEdgeLight

/**
 * 描边光在本项目里的接线点：backdrop 里的同名函数是纯 UI 的、不知道 App 当前深浅色与
 * 性能档策略，这里把「是否浅色」与「材质档降级」以参数注入补上。
 */

@Composable
fun rememberDefaultEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberDefaultEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )

@Composable
fun rememberCourseCardEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberCourseCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )

@Composable
fun rememberCardEdgeLight(baseColor: Color? = null): EdgeLight =
    backdropRememberCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = AppMaterialSettings::resolveEdgeLight,
    )
