package com.example.tiresairpressuremanagement.domain.models

data class DeviceSettings(
    val deviceName: String,
    val wifiSsid: String,
    val wifiPassword: String,
    val relayCompressorPin: Int,
    val relayFreePin: Int,
    val sensorPin: Int,
    val autoStart: Boolean,
    val maxSecForStatus: Int,
    val sensorValueAt0PSI: Int,
    val sensorValueAt50PSI: Int,
    val measureWait: Int,
    val precision: Int,
    val btPinCode: String
) 