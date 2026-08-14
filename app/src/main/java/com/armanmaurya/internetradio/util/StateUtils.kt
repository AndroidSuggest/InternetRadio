package com.armanmaurya.internetradio.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

import androidx.annotation.Keep

@Keep
data class State(
    val code: String,
    val name: String,
    val translations: Map<String, String>? = null
) {
    fun getDisplayName(languageCode: String): String {
        return translations?.get(languageCode) ?: name
    }
}

object StateUtils {
    private var cachedStates: Map<String, List<State>>? = null

    fun getStatesForCountry(context: Context, countryCode: String): List<State> {
        if (cachedStates == null) {
            try {
                val inputStream = context.assets.open("states.json")
                val reader = InputStreamReader(inputStream)
                val type = object : TypeToken<Map<String, List<State>>>() {}.type
                cachedStates = Gson().fromJson(reader, type)
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
                cachedStates = emptyMap()
            }
        }
        return cachedStates?.get(countryCode) ?: emptyList()
    }

    fun getStateNameByCode(context: Context, countryCode: String, stateCode: String): String? {
        val states = getStatesForCountry(context, countryCode)
        return states.find { it.code == stateCode }?.name
    }
}
