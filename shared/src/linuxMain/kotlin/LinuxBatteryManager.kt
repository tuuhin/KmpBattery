package com.sam.kmp_battery

import com.sam.shared.BatteryManager
import com.sam.shared.BatteryState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow


private val logger = KotlinLogging.logger("LinuxBatteryManager")


class LinuxBatteryManager : BatteryManager {

	override suspend fun batteryLevel(): Int {
		val powerSupplyType = findPowerSupplyDevice(LinuxPowerClass.BATTERY) ?: return 0
		val levelFileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/capacity"
		val levelAsString = readFile(levelFileName, 3) ?: return 0
		return levelAsString.trim().toIntOrNull() ?: 0
	}

	override suspend fun isBatteryInPowerSavingMode(): Boolean {
		logger.info { "NO DIRECT API IS READY" }
		return false
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	override suspend fun batteryState(): BatteryState = coroutineScope {
		val powerSupplyType = findPowerSupplyDevice(LinuxPowerClass.BATTERY)
			?: return@coroutineScope BatteryState.NoBatteryFound

		val batteryLevelDeferred = async(Dispatchers.IO) {
			val fileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/capacity"
			val levelAsString = readFile(fileName, 3) ?: return@async 0
			levelAsString.trim().toIntOrNull() ?: 0
		}
		val batteryStatusDeferred = async(Dispatchers.IO) {
			val fileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/status"
			val statusAsString = readFile(fileName, 3)?.trim() ?: return@async PowerStatus.UNKNOWN
			when (statusAsString) {
				"Discharging" -> PowerStatus.DISCHARGING
				"Charging" -> PowerStatus.CHARING
				else -> PowerStatus.UNKNOWN
			}
		}
		awaitAll(batteryLevelDeferred, batteryStatusDeferred)

		val batteryLevel = batteryLevelDeferred.getCompleted()
		val batteryStatus = batteryStatusDeferred.getCompleted()

		if (batteryLevel == 100) return@coroutineScope BatteryState.Full
		when (batteryStatus) {
			PowerStatus.CHARING -> BatteryState.Charging(batteryLevel.toFloat())
			PowerStatus.DISCHARGING -> BatteryState.DisCharging(batteryLevel.toFloat())
			PowerStatus.UNKNOWN -> BatteryState.Unknown
		}
	}

	override val batteryStateFlow: Flow<BatteryState>
		// TODO: Implement this properly
		get() = emptyFlow()
}