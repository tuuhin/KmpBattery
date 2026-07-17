package com.sam.shared_desktop

expect class NativePlatformBatteryManager constructor() : NativeBatteryManager {
	override fun batteryLevel(): Int
	override fun batteryState(): NativeBatteryState
	override fun isBatteryInPowerSavingMode(): Boolean

	override fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit,
	): Long

	override fun unsubscribeToBatteryState(readHandle: Long)
}