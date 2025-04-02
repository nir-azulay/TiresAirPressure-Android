package com.nazulay.tiresairpressuremanagement.utils

import android.content.Context
import com.nazulay.tiresairpressuremanagement.BuildConfig
import com.nazulay.tiresairpressuremanagement.R
import java.text.SimpleDateFormat
import java.util.*

object Version {
    const val VERSION_NAME = BuildConfig.VERSION_NAME
    const val VERSION_CODE = BuildConfig.VERSION_CODE
    const val BUILD_TYPE = BuildConfig.BUILD_TYPE
    const val MIN_SDK_VERSION = BuildConfig.MIN_SDK_VERSION
    const val TARGET_SDK_VERSION = BuildConfig.TARGET_SDK_VERSION
    const val BUILD_TIME = BuildConfig.BUILD_TIME
    const val DEVELOPER = "Nir Azulay"

    fun getVersionInfo(context: Context): String {
        val buildTime = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(BUILD_TIME)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            BUILD_TIME // Fallback to raw string if parsing fails
        }
        return context.getString(R.string.version_info_format,
            VERSION_NAME,
            VERSION_CODE,
            BUILD_TYPE,
            buildTime,
            MIN_SDK_VERSION,
            TARGET_SDK_VERSION,
            DEVELOPER
        )
    }
} 
