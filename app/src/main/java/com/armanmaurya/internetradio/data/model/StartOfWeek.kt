package com.armanmaurya.internetradio.data.model

import java.util.Calendar

enum class StartOfWeek(val calendarValue: Int) {
    SUNDAY(Calendar.SUNDAY),
    MONDAY(Calendar.MONDAY),
    FRIDAY(Calendar.FRIDAY),
    SATURDAY(Calendar.SATURDAY);

    fun getDaysOrder(): List<Int> {
        val days = mutableListOf<Int>()
        var currentDay = calendarValue
        for (i in 0 until 7) {
            days.add(currentDay)
            currentDay++
            if (currentDay > 7) currentDay = 1
        }
        return days
    }
}
