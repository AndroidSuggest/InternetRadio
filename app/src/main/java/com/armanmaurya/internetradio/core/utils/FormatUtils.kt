package com.armanmaurya.internetradio.core.utils

import android.content.Context
import java.text.DateFormat
import java.text.SimpleDateFormat

object FormatUtils {
    /**
     * Returns a time format that respects the user's 12/24 hour preference,
     * but enforces English "AM" and "PM" strings when 12-hour format is used.
     */
    fun getTimeFormat(context: Context): DateFormat {
        val format = android.text.format.DateFormat.getTimeFormat(context)
        if (format is SimpleDateFormat && !android.text.format.DateFormat.is24HourFormat(context)) {
            val symbols = format.dateFormatSymbols
            symbols.amPmStrings = arrayOf("AM", "PM")
            format.dateFormatSymbols = symbols
        }
        return format
    }
}
