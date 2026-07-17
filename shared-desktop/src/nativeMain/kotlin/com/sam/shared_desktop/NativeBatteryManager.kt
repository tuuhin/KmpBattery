package com.sam.shared_desktop

interface NativeBatteryManager {

	fun batteryLevel(): Int
	fun batteryState(): NativeBatteryState
	fun isBatteryInPowerSavingMode(): Boolean

	fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit,
	): Long

	fun unsubscribeToBatteryState(readHandle: Long)
}
