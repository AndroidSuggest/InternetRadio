package com.armanmaurya.internetradio.utils

import java.util.Locale

object LanguageMapper {
    private val nameToCodeMap by lazy {
        Locale.getAvailableLocales()
            .filter { it.language.isNotEmpty() }
            .associateBy { it.getDisplayLanguage(Locale.ENGLISH).lowercase() }
            .mapValues { it.value.language }
    }

    fun getCodesFromNameString(languages: String): List<String> {
        return languages.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .mapNotNull { name ->
                if (name.length == 2) name // Already a code
                else nameToCodeMap[name]
            }
            .distinct()
    }
}
