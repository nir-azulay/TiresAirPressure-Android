package com.nazulay.tiresairpressuremanagement

import com.nazulay.tiresairpressuremanagement.data.ota.FirmwareUpgradeManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun firmwareVersionComparison_isCorrect() {
        assertTrue(
            FirmwareUpgradeManager.isBundledVersionNewer(
                bundled = "v1.0.261105_270",
                device = "v1.0.261102_266"
            )
        )
        assertFalse(
            FirmwareUpgradeManager.isBundledVersionNewer(
                bundled = "v1.0.261105_270",
                device = "v1.0.261105_270"
            )
        )
        assertFalse(
            FirmwareUpgradeManager.isBundledVersionNewer(
                bundled = "v1.0.261101_256",
                device = "v1.0.261105_270"
            )
        )
        assertTrue(
            FirmwareUpgradeManager.isBundledVersionNewer(
                bundled = "v1.0.261105_270",
                device = "2.00"
            )
        )
    }

    @Test
    fun settingsJsonMigration_preservesValuesAndFixesSchema() {
        val defaultSchema = """
            {
              "DeviceName": "esp32-AirUpDownController",
              "wifissid": "",
              "wifipassword": "",
              "RelayCompressorPin": 16,
              "RelayFreePin": 17,
              "SensorPin": 34,
              "MaxSecForStatus": 10,
              "SensorValueAt0PSI": 410,
              "SensorValueAt50PSI": 3400,
              "AccessCode": "",
              "MessureWait": 1,
              "Precision": 0.5,
              "SimulationMode": false,
              "PressureCalcMethod": 1,
              "MaxPressurePSI": 50,
              "MaxValveOpenSec": 120,
              "SensorFullScalePSI": 50,
              "AccessPointPassword": "12345678"
            }
        """.trimIndent()

        val legacyDeviceJson = """
            {
              "DeviceName": "Raptor-Air-MNG",
              "wifissid": "TrailNet",
              "wifipassword": "secretPassword",
              "RelayCompressorPin": 17,
              "RelayFreePin": 16,
              "SensorPin": 32,
              "MaxSecForStatus": 25,
              "SensorValueAt0PSI": 0.321,
              "SensorValueAt50PSI": 3.14,
              "MessureWait": 1.5,
              "Precision": 0.3,
              "SimulationMode": false,
              "PressureCalcMethod": 2,
              "MaxPressurePSI": 60,
              "BTPinCode": "9988",
              "BLESecurityEnabled": true,
              "AutoStart": true
            }
        """.trimIndent()

        val result = FirmwareUpgradeManager.migrateSettingsJson(
            currentJsonString = legacyDeviceJson,
            fallbackDeviceSettings = null,
            defaultSchemaString = defaultSchema
        )

        assertEquals(listOf("AutoStart", "BLESecurityEnabled", "BTPinCode"), result.removedKeys)
        assertEquals(listOf("AccessPointPassword", "MaxValveOpenSec", "SensorFullScalePSI"), result.addedKeys)

        val migratedObj = JSONObject(result.migratedJsonString)
        assertEquals("Raptor-Air-MNG", migratedObj.getString("DeviceName"))
        assertEquals("TrailNet", migratedObj.getString("wifissid"))
        assertEquals(17, migratedObj.getInt("RelayCompressorPin"))
        assertEquals(32, migratedObj.getInt("SensorPin"))
        assertEquals(2, migratedObj.getInt("PressureCalcMethod"))
        assertEquals("9988", migratedObj.getString("AccessCode"))
        assertEquals(120, migratedObj.getInt("MaxValveOpenSec"))
        assertEquals(50, migratedObj.getInt("SensorFullScalePSI"))
        assertEquals("12345678", migratedObj.getString("AccessPointPassword"))
        assertFalse(migratedObj.has("BTPinCode"))
        assertFalse(migratedObj.has("BLESecurityEnabled"))
        assertFalse(migratedObj.has("AutoStart"))
    }
}
