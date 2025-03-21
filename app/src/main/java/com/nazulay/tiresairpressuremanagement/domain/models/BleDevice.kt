package com.nazulay.tiresairpressuremanagement.domain.models

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
) 
