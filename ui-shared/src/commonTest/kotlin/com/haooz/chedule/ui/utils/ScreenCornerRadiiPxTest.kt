package com.haooz.chedule.ui.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenCornerRadiiPxTest {
    @Test
    fun maxRadiusIncludesEveryPhysicalCorner() {
        val corners = listOf(
            ScreenCornerRadiiPx(topLeft = 64f, topRight = 12f, bottomRight = 24f, bottomLeft = 36f),
            ScreenCornerRadiiPx(topLeft = 12f, topRight = 64f, bottomRight = 24f, bottomLeft = 36f),
            ScreenCornerRadiiPx(topLeft = 12f, topRight = 24f, bottomRight = 64f, bottomLeft = 36f),
            ScreenCornerRadiiPx(topLeft = 12f, topRight = 24f, bottomRight = 36f, bottomLeft = 64f),
        )

        for (radii in corners) {
            assertEquals(64f, radii.maxRadius, "Maximum must include all four corners: $radii")
        }
        assertEquals(0f, ScreenCornerRadiiPx.Zero.maxRadius)
        assertEquals(20f, ScreenCornerRadiiPx.uniform(20f).maxRadius)
    }

    @Test
    fun interpolationStartsAtTheCardAndEndsAtEachScreenCorner() {
        val screen = ScreenCornerRadiiPx(topLeft = 0f, topRight = 24f, bottomRight = 48f, bottomLeft = 72f)

        assertEquals(
            ScreenCornerRadiiPx(topLeft = 16f, topRight = 16f, bottomRight = 16f, bottomLeft = 16f),
            screen.interpolateFrom(startRadius = 16f, fraction = 0f),
        )
        assertEquals(screen, screen.interpolateFrom(startRadius = 16f, fraction = 1f))
    }

    @Test
    fun interpolationMovesEachCornerIndependentlyIncludingTowardZero() {
        val screen = ScreenCornerRadiiPx(topLeft = 0f, topRight = 24f, bottomRight = 48f, bottomLeft = 72f)

        assertEquals(
            ScreenCornerRadiiPx(topLeft = 12f, topRight = 18f, bottomRight = 24f, bottomLeft = 30f),
            screen.interpolateFrom(startRadius = 16f, fraction = 0.25f),
        )
        assertEquals(
            ScreenCornerRadiiPx(topLeft = 0f, topRight = 12f, bottomRight = 24f, bottomLeft = 36f),
            screen.interpolateFrom(startRadius = 0f, fraction = 0.5f),
        )
        assertEquals(
            ScreenCornerRadiiPx(topLeft = 8f, topRight = 8f, bottomRight = 8f, bottomLeft = 8f),
            ScreenCornerRadiiPx.Zero.interpolateFrom(startRadius = 16f, fraction = 0.5f),
        )
    }

    @Test
    fun scalingPreservesAsymmetryAndSquareCorners() {
        val screen = ScreenCornerRadiiPx(topLeft = 0f, topRight = 24f, bottomRight = 48f, bottomLeft = 72f)

        assertEquals(
            ScreenCornerRadiiPx(topLeft = 0f, topRight = 12f, bottomRight = 24f, bottomLeft = 36f),
            screen.scaled(0.5f),
        )
        assertEquals(
            ScreenCornerRadiiPx(topLeft = 0f, topRight = 48f, bottomRight = 96f, bottomLeft = 144f),
            screen.scaled(2f),
        )
        assertEquals(screen, screen.scaled(1f))
        assertEquals(ScreenCornerRadiiPx.Zero, screen.scaled(0f))
        assertEquals(ScreenCornerRadiiPx.Zero, ScreenCornerRadiiPx.Zero.scaled(2f))
    }
}
