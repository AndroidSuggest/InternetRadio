package com.armanmaurya.internetradio.domain.model

data class CastDevice(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 0
)
