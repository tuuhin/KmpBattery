package com.sam.shared

import com.sam.shared_desktop.LinuxBatteryManager

actual class BatteryManagerFactory {

	actual fun createProvider(): BatteryManager = LinuxBatteryManager()
}