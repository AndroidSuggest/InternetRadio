package com.armanmaurya.internetradio.data.local.database

import androidx.room.TypeConverter

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
    fun fromScheduleType(type: com.armanmaurya.internetradio.data.local.entity.ScheduleType): String {
        return type.name
    }

    @TypeConverter
    fun toScheduleType(name: String): com.armanmaurya.internetradio.data.local.entity.ScheduleType {
        return com.armanmaurya.internetradio.data.local.entity.ScheduleType.valueOf(name)
    }
}
