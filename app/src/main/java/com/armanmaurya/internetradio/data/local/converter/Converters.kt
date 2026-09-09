package com.armanmaurya.internetradio.data.local.converter

import androidx.room.TypeConverter
import com.armanmaurya.internetradio.data.local.entity.ScheduleType

class Converters {
    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toList(data: String?): List<String> {
        return data?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    @TypeConverter
    fun fromScheduleType(type: ScheduleType): String {
        return type.name
    }

    @TypeConverter
    fun toScheduleType(name: String): ScheduleType {
        return ScheduleType.valueOf(name)
    }
}