package com.armanmaurya.internetradio.data.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetStateKeys {
    val TITLE                 = stringPreferencesKey("title")
    val ARTIST                = stringPreferencesKey("artist")
    val ARTWORK_URL           = stringPreferencesKey("artwork_url")
    val IS_PLAYING            = booleanPreferencesKey("is_playing")
    val HAS_NEXT              = booleanPreferencesKey("has_next")
    val HAS_PREV              = booleanPreferencesKey("has_prev")
    val STATION_NAME          = stringPreferencesKey("station_name")
    val STATION_THUMBNAIL_URL = stringPreferencesKey("station_thumbnail_url")
    val IS_COVER_ART_FETCHED  = booleanPreferencesKey("is_cover_art_fetched")
    val BG_COLOR              = intPreferencesKey("bg_color")
    val TITLE_COLOR           = intPreferencesKey("title_color")
    val ARTIST_COLOR          = intPreferencesKey("artist_color")
    val DAY_BG_COLOR          = intPreferencesKey("day_bg_color")
    val DAY_TITLE_COLOR       = intPreferencesKey("day_title_color")
    val DAY_ARTIST_COLOR      = intPreferencesKey("day_artist_color")
    val SEED_COLOR            = intPreferencesKey("seed_color")
    val BG_ALPHA              = floatPreferencesKey("bg_alpha")
}
