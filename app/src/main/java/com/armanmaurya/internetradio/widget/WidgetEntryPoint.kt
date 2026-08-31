package com.armanmaurya.internetradio.widget

import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recentStationDao(): RecentStationDao
}
