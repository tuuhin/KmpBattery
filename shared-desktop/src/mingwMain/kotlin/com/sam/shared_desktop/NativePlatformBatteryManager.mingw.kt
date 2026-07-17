package com.sam.shared_desktop

import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import platform.windows.BATTERY_FLAG_CHARGING
import platform.windows.BATTERY_FLAG_NO_BATTERY
import platform.windows.BATTERY_FLAG_UNKNOWN
import platform.windows.BATTERY_LIFE_UNKNOWN
import platform.windows.GetSystemPowerStatus
import platform.windows.LPSYSTEM_POWER_STATUS
import platform.windows.SYSTEM_POWER_STATUS

internal val logger = KotlinLogging.logger("WindowsBatteryLogger")

actual class NativePlatformBatteryManager actual constructor() : NativeBatteryManager {

	init {
		KotlinLoggingConfiguration.logStartupMessage = false
		KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
		KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
	}

	private fun canCheckBatteryStatus(lpStatus: LPSYSTEM_POWER_STATUS) =
		GetSystemPowerStatus(lpStatus) != 0

	private fun isBatteryStatusOk(lpStatus: LPSYSTEM_POWER_STATUS): Boolean {
		val batteryFlag = lpStatus.pointed.BatteryFlag.toInt()
		val lifePercentage = lpStatus.pointed.BatteryLifePercent.toUInt()
		return batteryFlag != BATTERY_FLAG_UNKNOWN && lifePercentage != BATTERY_LIFE_UNKNOWN
	}

	actual override fun batteryLevel(): Int {
		return memScoped {
			val status = alloc<SYSTEM_POWER_STATUS>()
			if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr)) {
				return@memScoped -1
			}
			if (status.BatteryLifePercent.toInt() !in 0..100) return -1
			status.BatteryLifePercent.toInt()
		}
	}

	actual override fun isBatteryInPowerSavingMode(): Boolean {
		return memScoped {
			val status = alloc<SYSTEM_POWER_STATUS>()
			if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr)) {
				return@memScoped false
			}
			status.Reserved1.toInt() == 1
		}
	}

	actual override fun batteryState(): NativeBatteryState {
		return memScoped {
			val status = alloc<SYSTEM_POWER_STATUS>()
			// if any status is not found, then nothing
			if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr))
				return@memScoped NativeBatteryStateUnknown()
			// there may not be any battery at all in the system
			if (status.BatteryFlag.toInt() and BATTERY_FLAG_NO_BATTERY != 0)
				return@memScoped NativeBatteryStateNoBatteryFound()

			val amount = status.BatteryLifePercent.toFloat()
			if (amount == 100f) return@memScoped NativeBatteryStateFull()

			if (status.ACLineStatus.toInt() != 1)
				return@memScoped NativeBatteryStateDisCharging(amount)

			if (status.BatteryFlag.toInt() and BATTERY_FLAG_CHARGING != 0)
				return@memScoped NativeBatteryStateCharging(amount)
			NativeBatteryStateUnknown()
		}
	}

	actual override fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit,
	): Long {
		val handle = createNewThreadAndStartObserver {
			when (val newValue = batteryState()) {
				is NativeBatteryStateFull -> onFull()
				is NativeBatteryStateCharging -> onCharging(newValue.amount)
				is NativeBatteryStateDisCharging -> onDisCharging(newValue.amount)
				is NativeBatteryStateNoBatteryFound -> onBatteryNotFound()
				else -> onUnknown()
			}
		}
		logger.debug { "THREAD HANDLE CREATED" }
		return handle?.toLong() ?: -1L
	}

	actual override fun unsubscribeToBatteryState(readHandle: Long) {
		if (readHandle == -1L) {
			logger.warn { "INVALID HANDLE TO WORK WITH" }
			return
		}
		val handle: CPointer<IntVar>? = readHandle.toCPointer()
		logger.debug { "THREAD HANDLE DISPOSED" }
		stopObserverAndCloseThread(handle)
	}
}