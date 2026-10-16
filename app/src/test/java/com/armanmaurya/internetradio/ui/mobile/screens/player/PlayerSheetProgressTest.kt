package com.armanmaurya.internetradio.ui.mobile.screens.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PlayerSheetProgressTest {

    @Test
    fun `expanded state returns 1f regardless of offset`() {
        val progress = calculatePlayerSheetProgress(
            currentValue = SheetValue.Expanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 0f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progress, 0.0001f)
    }

    @Test
    fun `partially expanded state returns 0f regardless of offset`() {
        val progress = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.PartiallyExpanded,
            currentOffset = 1800f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(0f, progress, 0.0001f)
    }

    @Test
    fun `offset at or below 0 returns 1f even during transition`() {
        val progressAtZero = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 0f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progressAtZero, 0.0001f)

        val progressNegative = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = -10f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progressNegative, 0.0001f)
    }

    @Test
    fun `null offset falls back to targetValue expected progress`() {
        val progressExpanded = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = null,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progressExpanded, 0.0001f)

        val progressCollapsed = calculatePlayerSheetProgress(
            currentValue = SheetValue.Expanded,
            targetValue = SheetValue.PartiallyExpanded,
            currentOffset = null,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(0f, progressCollapsed, 0.0001f)
    }

    @Test
    fun `halfway transition calculates 0_5f progress`() {
        // totalRange = 2000 - 200 = 1800. Halfway offset = 900.
        val progress = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 900f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(0.5f, progress, 0.0001f)
    }

    @Test
    fun `early drag progression before halfway threshold transitions smoothly`() {
        // totalRange = 2000 - 200 = 1800.
        // Even when targetValue is still PartiallyExpanded, moving 180px gives 0.1f progress
        val progress10Percent = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.PartiallyExpanded,
            currentOffset = 1620f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(0.1f, progress10Percent, 0.0001f)

        // Moving 360px gives 0.2f progress smoothly
        val progress20Percent = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.PartiallyExpanded,
            currentOffset = 1440f,
            screenHeightPx = 2000f,
            peekHeightPx = 200f
        )
        assertEquals(0.2f, progress20Percent, 0.0001f)
    }

    @Test
    fun `keyboard resize scenario does not crash or collapse progress when expanded`() {
        // Under adjustResize, screenHeight may be significantly reduced (e.g. 500px height with 200px peek)
        val progressExpanded = calculatePlayerSheetProgress(
            currentValue = SheetValue.Expanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 0f,
            screenHeightPx = 500f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progressExpanded, 0.0001f)

        // Even during active drag where offset reached top (0f), progress is 1f
        val progressAtTop = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 0f,
            screenHeightPx = 500f,
            peekHeightPx = 200f
        )
        assertEquals(1f, progressAtTop, 0.0001f)
    }

    @Test
    fun `floating miniplayer with keyboard insets maintains accurate progress`() {
        // screenHeight = 2400px. Peek without keyboard = 200px. Keyboard adds 800px -> peekHeight = 1000px.
        // totalRange = 2400 - 1000 = 1400px.
        // When collapsed at 1400px, progress is 0f
        val progressCollapsedAboveKeyboard = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.PartiallyExpanded,
            currentOffset = 1400f,
            screenHeightPx = 2400f,
            peekHeightPx = 1000f
        )
        assertEquals(0f, progressCollapsedAboveKeyboard, 0.0001f)

        // Dragging halfway (offset = 700px) gives 0.5f progress smoothly
        val progressHalfway = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 700f,
            screenHeightPx = 2400f,
            peekHeightPx = 1000f
        )
        assertEquals(0.5f, progressHalfway, 0.0001f)

        // Dragging to top (offset = 0px) gives 1f progress
        val progressTop = calculatePlayerSheetProgress(
            currentValue = SheetValue.PartiallyExpanded,
            targetValue = SheetValue.Expanded,
            currentOffset = 0f,
            screenHeightPx = 2400f,
            peekHeightPx = 1000f
        )
        assertEquals(1f, progressTop, 0.0001f)
    }
}
