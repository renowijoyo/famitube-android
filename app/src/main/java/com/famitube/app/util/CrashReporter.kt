package com.famitube.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.famitube.app.data.local.ParentalPreferences
import com.famitube.app.data.model.ClientCrashReportDto
import com.famitube.app.data.repository.FamiTubeRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val CRASH_FILE_NAME = "pending_crash_report.json"
    private val gson = Gson()

    @Volatile
    var currentRoute: String = "AppStart"

    private var preferencesRef: ParentalPreferences? = null

    fun init(context: Context, preferences: ParentalPreferences? = null) {
        preferencesRef = preferences
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to record uncaught exception", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val appVersion = pInfo?.versionName ?: "1.0.0"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val errorType = throwable.javaClass.name
        val message = throwable.message ?: "No error message"
        val stackTrace = throwable.stackTraceToString()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())

        val userEmail = preferencesRef?.loggedInEmail?.ifBlank { null }
        val userRole = preferencesRef?.loggedInRole?.ifBlank { null }

        val report = ClientCrashReportDto(
            appVersion = appVersion,
            deviceModel = deviceModel,
            osVersion = osVersion,
            currentRoute = currentRoute,
            errorType = errorType,
            message = message,
            stackTrace = stackTrace,
            userId = userEmail,
            userRole = userRole,
            timestamp = timestamp
        )

        val file = File(context.filesDir, CRASH_FILE_NAME)
        file.writeText(gson.toJson(report))
        Log.i(TAG, "Saved uncaught crash report to ${file.absolutePath}")
    }

    fun dispatchPendingCrashReports(context: Context, repository: FamiTubeRepository) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (!file.exists()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = file.readText()
                val report = gson.fromJson(json, ClientCrashReportDto::class.java)
                if (report != null && report.message.isNotBlank()) {
                    val res = repository.sendCrashReport(report)
                    if (res.isSuccess) {
                        Log.i(TAG, "Successfully dispatched pending crash report to backend")
                        file.delete()
                    } else {
                        Log.w(TAG, "Failed to send crash report to backend: ${res.exceptionOrNull()?.message}")
                    }
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching pending crash report", e)
            }
        }
    }

    fun reportNonFatal(
        context: Context,
        repository: FamiTubeRepository,
        throwable: Throwable,
        route: String? = null
    ) {
        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val appVersion = pInfo?.versionName ?: "1.0.0"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val errorType = throwable.javaClass.name
        val message = throwable.message ?: "Non-fatal error"
        val stackTrace = throwable.stackTraceToString()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())

        val userEmail = preferencesRef?.loggedInEmail?.ifBlank { null }
        val userRole = preferencesRef?.loggedInRole?.ifBlank { null }

        val report = ClientCrashReportDto(
            appVersion = appVersion,
            deviceModel = deviceModel,
            osVersion = osVersion,
            currentRoute = route ?: currentRoute,
            errorType = errorType,
            message = message,
            stackTrace = stackTrace,
            userId = userEmail,
            userRole = userRole,
            timestamp = timestamp
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.sendCrashReport(report)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send non-fatal error report", e)
            }
        }
    }
}
