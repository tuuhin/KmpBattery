package com.sam.shared

import android.content.Intent
import android.os.BatteryManager

typealias AndroidOSBatteryManager = BatteryManager

fun Intent.toBatteryState(): BatteryState? {

	val batteryActions = arrayOf(
		Intent.ACTION_POWER_CONNECTED,
		Intent.ACTION_POWER_DISCONNECTED,
		Intent.ACTION_BATTERY_CHANGED,
		Intent.ACTION_BATTERY_LOW
	)

	if (action !in batteryActions) return null

	val status: Int = getIntExtra(AndroidOSBatteryManager.EXTRA_STATUS, -1)
	val level: Int = getIntExtra(AndroidOSBatteryManager.EXTRA_LEVEL, -1)
	val scale: Int = getIntExtra(AndroidOSBatteryManager.EXTRA_SCALE, -1)

	val batteryLevel = level * 100f / scale

	return when (status) {
		AndroidOSBatteryManager.BATTERY_STATUS_FULL -> BatteryState.Full
		AndroidOSBatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.Charging(batteryLevel)
		AndroidOSBatteryManager.BATTERY_STATUS_DISCHARGING ->
			BatteryState.DisCharging(batteryLevel)

		AndroidOSBatteryManager.BATTERY_STATUS_UNKNOWN -> BatteryState.Unknown
		else -> BatteryState.NoBatteryFound
	}
}