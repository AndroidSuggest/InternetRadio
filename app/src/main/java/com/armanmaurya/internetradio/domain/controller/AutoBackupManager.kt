package com.armanmaurya.internetradio.domain.controller

interface AutoBackupManager {
    fun initialize()
    suspend fun performBackup(): Result<Unit>
    suspend fun performRestore(): Result<Int>
    suspend fun performSmartSync(): Result<String>
}
