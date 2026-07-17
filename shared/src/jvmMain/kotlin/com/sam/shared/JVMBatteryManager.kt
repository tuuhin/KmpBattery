package com.sam.shared

import com.sam.bluepad.platform.native.NativeBatteryState
import com.sam.bluepad.platform.native.NativeBatteryStateCharging
import com.sam.bluepad.platform.native.NativeBatteryStateDisCharging
import com.sam.bluepad.platform.native.NativeBatteryStateFull
import com.sam.bluepad.platform.native.NativeBatteryStateNoBatteryFound
import com.sam.bluepad.platform.native.NativePlatformBatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.thread

class JVMBatteryManager : BatteryManager {

	override suspend fun batteryLevel(): Int {
		return withContext(Dispatchers.IO) {
			NativePlatformBatteryManager().use { it.batteryLevel() }
		}
	}

	override suspend fun isBatteryInPowerSavingMode(): Boolean {
		return withContext(Dispatchers.IO) {
			NativePlatformBatteryManager().use { it.isBatteryInPowerSavingMode() }
		}
	}

	override suspend fun batteryState(): BatteryState {
		return withContext(Dispatchers.IO) {
			NativePlatformBatteryManager().use { it.batteryState()
				.toDomainState() }
		}
	}

	@OptIn(ExperimentalAtomicApi::class)
	override val batteryStateFlow: Flow<BatteryState>
		get() {
			val manager = NativePlatformBatteryManager()

			return callbackFlow {

				launch {
					val state = manager.batteryState()
					send(state.toDomainState())
				}

				val handle = manager.subscribedToBatteryState(
					onFull = { trySend(BatteryState.Full) },
					onCharging = { trySend(BatteryState.Charging(it)) },
					onDisCharging = { trySend(BatteryState.DisCharging(it)) },
					onBatteryNotFound = { trySend(BatteryState.NoBatteryFound) },
					onUnknown = { trySend(BatteryState.Unknown) },
				)

				val cleaned = AtomicBoolean(false)

				// clean up code
				fun cleanup() {
					if (!cleaned.compareAndSet(expectedValue = false, newValue = true))
						return

					manager.unsubscribeToBatteryState(handle)
					manager.close()
				}

				// set the shutdown hook
				val shutdownHook = thread(false) { cleanup() }
				Runtime.getRuntime().addShutdownHook(shutdownHook)

				awaitClose {
					try {
						// remove it as the jvm will handle the cleanup by itself
						Runtime.getRuntime().removeShutdownHook(shutdownHook)
					} catch (_: IllegalStateException) {

					}
					cleanup()
				}
			}
		}

	private fun NativeBatteryState.toDomainState(): BatteryState {
		return when (this) {
			is NativeBatteryStateCharging -> BatteryState.Charging(amount)
			is NativeBatteryStateDisCharging -> BatteryState.DisCharging(amount)
			is NativeBatteryStateFull -> BatteryState.Full
			is NativeBatteryStateNoBatteryFound -> BatteryState.NoBatteryFound
			else -> when (code) {
				0 -> BatteryState.Full
				1 -> BatteryState.Charging(amt)
				2 -> BatteryState.DisCharging(amt)
				3 -> BatteryState.NoBatteryFound
				else -> BatteryState.Unknown
			}
		}
	}
}