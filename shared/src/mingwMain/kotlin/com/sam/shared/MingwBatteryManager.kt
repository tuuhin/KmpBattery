package com.sam.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.windows.BATTERY_FLAG_CHARGING
import platform.windows.BATTERY_FLAG_NO_BATTERY
import platform.windows.BATTERY_FLAG_UNKNOWN
import platform.windows.BATTERY_LIFE_UNKNOWN
import platform.windows.GetSystemPowerStatus
import platform.windows.LPSYSTEM_POWER_STATUS
import platform.windows.SYSTEM_POWER_STATUS

@OptIn(ExperimentalForeignApi::class)
class MingwBatteryManager : BatteryManager {

	private fun canCheckBatteryStatus(lpStatus: LPSYSTEM_POWER_STATUS) =
		GetSystemPowerStatus(lpStatus) != 0

	private fun isBatteryStatusOk(lpStatus: LPSYSTEM_POWER_STATUS): Boolean {
		val batteryFlag = lpStatus.pointed.BatteryFlag.toInt()
		val lifePercentage = lpStatus.pointed.BatteryLifePercent.toUInt()
		return batteryFlag != BATTERY_FLAG_UNKNOWN && lifePercentage != BATTERY_LIFE_UNKNOWN
	}

	override suspend fun batteryLevel(): Int {
		return withContext(Dispatchers.IO) {
			memScoped {
				val status = alloc<SYSTEM_POWER_STATUS>()
				if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr)) {
					return@memScoped -1
				}
				if (status.BatteryLifePercent.toInt() !in 0..100) return@withContext -1
				status.BatteryLifePercent.toInt()
			}
		}
	}

	override suspend fun isBatteryInPowerSavingMode(): Boolean {
		return withContext(Dispatchers.IO) {
			memScoped {
				val status = alloc<SYSTEM_POWER_STATUS>()
				if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr)) {
					return@memScoped false
				}
				status.Reserved1.toInt() == 1
			}
		}
	}

	override suspend fun batteryState(): BatteryState {
		return withContext(Dispatchers.IO) {
			memScoped {
				val status = alloc<SYSTEM_POWER_STATUS>()
				// if any status is not found, then nothing
				if (!canCheckBatteryStatus(status.ptr) || !isBatteryStatusOk(status.ptr)) return@memScoped BatteryState.Unknown
				// there may not be any battery at all in the system
				if (status.BatteryFlag.toInt() and BATTERY_FLAG_NO_BATTERY != 0) return@memScoped BatteryState.NoBatteryFound

				val amount = status.BatteryLifePercent.toFloat()
				if (amount == 100f) return@memScoped BatteryState.Full

				if (status.ACLineStatus.toInt() != 1)
					return@memScoped BatteryState.DisCharging(amount)

				if (status.BatteryFlag.toInt() and BATTERY_FLAG_CHARGING != 0)
					return@memScoped BatteryState.Charging(amount)
				//
				BatteryState.Unknown
			}
		}
	}

	override val batteryStateFlow: Flow<BatteryState>
		get() = callbackFlow {
			val handle = createNewThreadAndStartObserver {
				launch {
					val newValue = batteryState()
					send(newValue)
				}
			}
			awaitClose {
				stopObserverAndCloseThread(handle)
			}
		}.flowOn(Dispatchers.IO)
			.distinctUntilChanged()
}