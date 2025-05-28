package com.sam.shared

expect class BatteryManagerFactory {

	fun createProvider(): BatteryManager
}