package com.sam.shared

import com.sam.bluepad.platform.native.NativeBatteryManagerImpl
import com.sam.bluepad.platform.native.NativeBatteryState
import com.sam.bluepad.platform.native.NativeBatteryStateCharging
import com.sam.bluepad.platform.native.NativeBatteryStateDisCharging
import com.sam.bluepad.platform.native.NativeBatteryStateFull
import com.sam.bluepad.platform.native.NativeBatteryStateNoBatteryFound
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class BatteryManagerFactory {

	actual fun createProvider(): BatteryManager {
		return object : BatteryManager {
			override suspend fun batteryLevel(): Int {
				return NativeBatteryManagerImpl().use { it.batteryLevel() }
			}

			override suspend fun isBatteryInPowerSavingMode(): Boolean {
				return NativeBatteryManagerImpl().use { it.isBatteryInPowerSavingMode() }
			}

			override suspend fun batteryState(): BatteryState {
				return NativeBatteryManagerImpl().use {
					it.batteryState().toBatteryState()
				}
			}

			override val batteryStateFlow: Flow<BatteryState>
				get() {
					val manager = NativeBatteryManagerImpl()
					return callbackFlow {

						trySend(manager.batteryState().toBatteryState())

						val handle = manager.subscribedToBatteryState(
							onFull = { trySend(BatteryState.Full) },
							onCharging = { trySend(BatteryState.Charging(it)) },
							onDisCharging = { trySend(BatteryState.DisCharging(it)) },
							onBatteryNotFound = { trySend(BatteryState.NoBatteryFound) },
							onUnknown = { trySend(BatteryState.Unknown) },
						)
						awaitClose {
							manager.unsubscribeToBatteryState(handle)
							manager.close()
						}
					}
				}
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