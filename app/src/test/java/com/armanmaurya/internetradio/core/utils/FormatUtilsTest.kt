package com.armanmaurya.internetradio.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class FormatUtilsTest {

    @Test
    fun getRecordingTimestamp_24HourFormat_formatsWithHHmm() {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            set(2026, Calendar.OCTOBER, 14, 14, 35, 0)
        }

        val result = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = true
        )

        assertTrue("Expected 24-hour time 14-35, but got: $result", result.endsWith("14-35"))
    }

    @Test
    fun getRecordingTimestamp_12HourFormat_formatsWithhhmmAMPM() {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            set(2026, Calendar.OCTOBER, 14, 14, 35, 0)
        }

        val result = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = false
        )

        assertTrue("Expected 12-hour time 02-35 PM, but got: $result", result.endsWith("02-35 PM"))
    }

    @Test
    fun getRecordingTimestamp_morningTimes() {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            set(2026, Calendar.OCTOBER, 14, 9, 5, 0)
        }

        val result24 = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = true
        )
        val result12 = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = false
        )

        assertTrue("Expected 24-hour time 09-05, but got: $result24", result24.endsWith("09-05"))
        assertTrue("Expected 12-hour time 09-05 AM, but got: $result12", result12.endsWith("09-05 AM"))
    }

    @Test
    fun getRecordingTimestamp_midnight() {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            set(2026, Calendar.OCTOBER, 14, 0, 0, 0)
        }

        val result24 = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = true
        )
        val result12 = FormatUtils.getRecordingTimestamp(
            context = null,
            date = calendar.time,
            is24Hour = false
        )

        assertTrue("Expected 24-hour time 00-00, but got: $result24", result24.endsWith("00-00"))
        assertTrue("Expected 12-hour time 12-00 AM, but got: $result12", result12.endsWith("12-00 AM"))
    }
}
