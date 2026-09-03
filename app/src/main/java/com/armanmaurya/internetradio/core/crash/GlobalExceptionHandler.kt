package com.armanmaurya.internetradio.core.crash

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    private val applicationContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val stringWriter = StringWriter()
        exception.printStackTrace(PrintWriter(stringWriter))
        val stackTrace = stringWriter.toString()

        val intent = Intent(applicationContext, CrashActivity::class.java).apply {
            putExtra(CrashActivity.EXTRA_CRASH_LOG, stackTrace)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        
        applicationContext.startActivity(intent)
        
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(2)
    }
}
