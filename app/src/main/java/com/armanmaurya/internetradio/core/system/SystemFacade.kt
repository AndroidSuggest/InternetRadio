package com.armanmaurya.internetradio.core.system

interface SystemFacade {
    fun getAppVersionName(): String
    fun getAppVersionCode(): Long
}
