package com.sam.shared

import com.sam.shared.native.NativeBatteryState
import com.sam.shared.native.NativeBatteryStateCharging
import com.sam.shared.native.NativeBatteryStateDisCharging
import com.sam.shared.native.NativeBatteryStateFull
import com.sam.shared.native.NativeBatteryStateNoBatteryFound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

actual class BatteryManagerFactory {

	actual fun createProvider(): BatteryManager {

		val native = NativeBatteryManagerImpl()

		return object : BatteryManager {
			override suspend fun batteryLevel(): Int =
				withContext(Dispatchers.IO) { native.batteryLevel() }

			override suspend fun isBatteryInPowerSavingMode(): Boolean =
				withContext(Dispatchers.IO) { native.isBatteryInPowerSavingMode() }

			override suspend fun batteryState(): BatteryState =
				withContext(Dispatchers.IO) { native.batteryState().toBatteryState() }

			override val batteryStateFlow: Flow<BatteryState> = callbackFlow {

				trySend(batteryState())

				val handle = native.subscribedToBatteryState(
					onFull = { trySend(BatteryState.Full) },
					onCharging = { trySend(BatteryState.Charging(it)) },
					onDisCharging = { trySend(BatteryState.DisCharging(it)) },
					onBatteryNotFound = { trySend(BatteryState.NoBatteryFound) },
					onUnknown = { trySend(BatteryState.Unknown) },
				)
				awaitClose {
					native.unsubscribeToBatteryState(handle)
				}
			}.flowOn(Dispatchers.IO)
		}
	}

	private fun NativeBatteryState.toBatteryState(): BatteryState {
		return when (this) {
			is NativeBatteryStateCharging -> BatteryState.Charging(amount)
			is NativeBatteryStateDisCharging -> BatteryState.DisCharging(amount)
			is NativeBatteryStateFull -> BatteryState.Full
			is NativeBatteryStateNoBatteryFound -> BatteryState.NoBatteryFound
			else -> BatteryState.Unknown
		}
	}

}