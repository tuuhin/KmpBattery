package com.sam.shared

import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.lowPowerModeEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt


@OptIn(ExperimentalAtomicApi::class)
class IOSNativeBatteryManager : BatteryManager {

	private val logger by lazy {
		KotlinLoggingConfiguration.logStartupMessage = false
		KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
		KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
		KotlinLogging.logger("MacosBatteryLogger")
	}

	private fun isRunningInSimulator(): Boolean {
		return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
	}

	override suspend fun batteryLevel(): Int {
		return withContext(Dispatchers.IO) {
			if (isRunningInSimulator()) return@withContext -1

			val device = UIDevice.currentDevice
			val isEnabled = device.isBatteryMonitoringEnabled()
			if (!isEnabled) device.batteryMonitoringEnabled = true
			val rawLevel = device.batteryLevel

			if (!isEnabled) device.batteryMonitoringEnabled = false

			if (rawLevel < 0f) 0 else (rawLevel * 100f).roundToInt()
		}
	}

	override suspend fun batteryState(): BatteryState {
		return withContext(Dispatchers.IO) {
			if (isRunningInSimulator()) return@withContext BatteryState.NoBatteryFound

			val device = UIDevice.currentDevice
			val isEnabled = device.isBatteryMonitoringEnabled()
			if (!isEnabled) device.batteryMonitoringEnabled = true
			val rawLevel = device.batteryLevel
			val batteryState = device.batteryState

			if (!isEnabled) device.batteryMonitoringEnabled = false

			val percentage = if (rawLevel < 0f) 0f else rawLevel * 100f

			when {
				percentage >= 95f || batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull -> BatteryState.Full
				batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ->
					BatteryState.Charging(percentage)

				batteryState == UIDeviceBatteryState.UIDeviceBatteryStateUnplugged ->
					BatteryState.DisCharging(percentage)

				else -> BatteryState.Unknown
			}
		}
	}

	override suspend fun isBatteryInPowerSavingMode(): Boolean {
		return NSProcessInfo.processInfo.lowPowerModeEnabled
	}

	override val batteryStateFlow: Flow<BatteryState>
		get() = callbackFlow {

			val device = UIDevice.currentDevice
			// enable battery monitoring
			if (!device.isBatteryMonitoringEnabled()) device.batteryMonitoringEnabled = true

			val notificationCenter = NSNotificationCenter.defaultCenter

			// emit the first state
			launch {
				val state = batteryState()
				trySend(state)
			}

			val stateObserver = notificationCenter.addObserverForName(
				UIDeviceBatteryStateDidChangeNotification, null, null
			) {
				val state = runBlocking { batteryState() }
				trySend(state)
			}

			val levelObserver = notificationCenter.addObserverForName(
				UIDeviceBatteryLevelDidChangeNotification, null, null
			) {
				val state = runBlocking { batteryState() }
				trySend(state)
			}

			logger.info { "OBSERVER FOR BATTERY LEVEL AND STATE ADDED" }

			awaitClose {
				logger.info { "REMOVING OBSERVER FOR BATTERY LEVEL AND STATE" }
				notificationCenter.removeObserver(levelObserver)
				notificationCenter.removeObserver(stateObserver)
				device.batteryMonitoringEnabled = false
			}
		}
}