package com.sam.shared

actual class BatteryManagerFactory {

	actual fun createProvider(): BatteryManager = MingwBatteryManager()
}