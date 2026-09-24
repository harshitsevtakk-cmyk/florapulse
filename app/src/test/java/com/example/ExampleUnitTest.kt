package com.example

import com.example.bluetooth.BluetoothDeviceInfo
import com.example.data.model.PumpStatus
import com.example.data.model.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parsePacket_validWateringPacket_returnsCorrectTelemetry() {
        val line = "26.5,58.0,420,WATERING"
        val data = TelemetryData.parsePacket(line)

        assertNotNull(data)
        assertEquals(26.5f, data!!.temperature, 0.01f)
        assertEquals(58.0f, data.humidity, 0.01f)
        assertEquals(420, data.soilMoisture)
        assertEquals(PumpStatus.WATERING, data.pumpStatus)
        assertTrue(data.isDry)
    }

    @Test
    fun parsePacket_validIdlePacket_returnsCorrectTelemetry() {
        val line = "24.2,65.0,610,IDLE"
        val data = TelemetryData.parsePacket(line)

        assertNotNull(data)
        assertEquals(24.2f, data!!.temperature, 0.01f)
        assertEquals(65.0f, data.humidity, 0.01f)
        assertEquals(610, data.soilMoisture)
        assertEquals(PumpStatus.IDLE, data.pumpStatus)
        assertFalse(data.isDry)
    }

    @Test
    fun parsePacket_malformedPacket_returnsNull() {
        val line = "not,a,valid"
        val data = TelemetryData.parsePacket(line)
        assertNull(data)
    }

    @Test
    fun parsePacket_blankLine_returnsNull() {
        assertNull(TelemetryData.parsePacket("   "))
        assertNull(TelemetryData.parsePacket(""))
    }

    @Test
    fun soilPercentage_calculatesCorrectRatio() {
        val data = TelemetryData(
            temperature = 25f,
            humidity = 50f,
            soilMoisture = 512,
            pumpStatus = PumpStatus.IDLE
        )
        // 512 / 1023 ~= 50%
        assertEquals(50, data.soilPercentage)
    }

    @Test
    fun bluetoothDeviceInfo_identifiesFloraPulseHardware() {
        val hc05 = BluetoothDeviceInfo("HC-05", "98:D3:31:F4:01:22")
        val flora = BluetoothDeviceInfo("FloraPulse-Plant1", "00:11:22:33:44:55")
        val other = BluetoothDeviceInfo("Random-Headphones", "11:22:33:44:55:66")

        assertTrue(hc05.isFloraPulseDevice)
        assertTrue(flora.isFloraPulseDevice)
        assertFalse(other.isFloraPulseDevice)
    }
}
