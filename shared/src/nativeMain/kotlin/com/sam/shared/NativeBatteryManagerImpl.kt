package com.sam.shared

import com.sam.shared.native.NativeBatteryManager
import com.sam.shared.native.NativeBatteryState

expect class NativeBatteryManagerImpl constructor() : NativeBatteryManager {
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