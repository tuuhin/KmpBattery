package com.sam.kmp_battery

import android.content.Intent

typealias AndroidBatteryManager = android.os.BatteryManager

fun Intent.toBatteryState(): BatteryState? {

	val batteryActions = arrayOf(
		Intent.ACTION_POWER_CONNECTED,
		Intent.ACTION_POWER_DISCONNECTED,
		Intent.ACTION_BATTERY_CHANGED,
		Intent.ACTION_BATTERY_LOW
	)

	if (action !in batteryActions) return null

	val status: Int = getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
	val level: Int = getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
	val scale: Int = getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)

	val batteryLevel = level * 100f / scale

	return when (status) {
		AndroidBatteryManager.BATTERY_STATUS_FULL -> BatteryState.Full
		AndroidBatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.Charging(batteryLevel)
		AndroidBatteryManager.BATTERY_STATUS_DISCHARGING ->
			BatteryState.DisCharging(batteryLevel)

		AndroidBatteryManager.BATTERY_STATUS_UNKNOWN -> BatteryState.Unknown
		else -> BatteryState.NoBatteryFound
	}
}