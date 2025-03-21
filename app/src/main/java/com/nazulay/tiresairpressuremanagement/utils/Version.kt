package com.nazulay.tiresairpressuremanagement.utils

import com.nazulay.tiresairpressuremanagement.BuildConfig

object Version {
    val VERSION_NAME = BuildConfig.VERSION_NAME
    val VERSION_CODE = BuildConfig.VERSION_CODE
    val BUILD_TYPE = when(BuildConfig.BUILD_TYPE) {
        "DEBUG" -> BuildType.DEBUG
        "BETA" -> BuildType.BETA
        else -> BuildType.RELEASE
    }
    const val MIN_SDK_VERSION = 24
    const val TARGET_SDK_VERSION = 34
    
    val BUILD_TIME = BuildConfig.BUILD_TIME
    const val DEVELOPER = "ניר אזולאי"
    
    enum class BuildType {
        DEBUG, RELEASE, BETA
    }
    
    fun getFullVersionInfo(): String {
        return """
            גרסה: $VERSION_NAME
            קוד גרסה: $VERSION_CODE
            סוג בנייה: $BUILD_TYPE
            זמן בנייה: $BUILD_TIME
            גרסת SDK מינימלית: $MIN_SDK_VERSION
            גרסת SDK יעד: $TARGET_SDK_VERSION
            מפתח: $DEVELOPER
        """.trimIndent()
    }
    
    fun getShortVersionInfo(): String {
        return "v$VERSION_NAME ($BUILD_TYPE)"
    }
} 
