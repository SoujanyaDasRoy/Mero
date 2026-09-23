package com.mero.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** How far the photo may be dragged before an edge of the icon would show empty. */
class CustomIconCropTest {

    @Test
    fun `a photo that exactly fills the circle cannot move`() {
        assertEquals(0f to 0f, maxOffset(100, 100, viewport = 200f, userScale = 1f))
    }

    @Test
    fun `zooming in gives room to move`() {
        assertEquals(100f to 100f, maxOffset(100, 100, viewport = 200f, userScale = 2f))
    }

    /** A wide photo fills the height, so it slides sideways only. */
    @Test
    fun `a wide photo slides sideways`() {
        assertEquals(50f to 0f, maxOffset(200, 100, viewport = 100f, userScale = 1f))
    }
}
