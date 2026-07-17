package com.sam.shared

import com.sam.shared.native.NativeBatteryManager
import com.sam.shared.native.NativeBatteryState
import com.sam.shared.native.NativeBatteryStateCharging
import com.sam.shared.native.NativeBatteryStateDisCharging
import com.sam.shared.native.NativeBatteryStateFull
import com.sam.shared.native.NativeBatteryStateNoBatteryFound
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.lowPowerModeEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.darwin.NSObjectProtocol
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextLong

private data class IosObserverPair(
	val levelObserver: NSObjectProtocol,
	val stateObserver: NSObjectProtocol
)

@OptIn(ExperimentalAtomicApi::class)
actual class NativeBatteryManagerImpl actual constructor() : NativeBatteryManager {

	private val _handleId = AtomicLong(-1L)
	private val _registry = HashMap<Long, IosObserverPair>()

	private fun isRunningInSimulator(): Boolean {
		return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
	}

	actual override fun batteryLevel(): Int {
		if (isRunningInSimulator()) return -1

		val device = UIDevice.currentDevice
		val isEnabled = device.isBatteryMonitoringEnabled()
		if (!isEnabled) device.batteryMonitoringEnabled = true
		val rawLevel = device.batteryLevel

		if (!isEnabled) device.batteryMonitoringEnabled = false

		return if (rawLevel < 0f) 0 else (rawLevel * 100f).roundToInt()
	}

	actual override fun batteryState(): NativeBatteryState {
		if (isRunningInSimulator()) return NativeBatteryStateNoBatteryFound()

		val device = UIDevice.currentDevice
		val isEnabled = device.isBatteryMonitoringEnabled()
		if (!isEnabled) device.batteryMonitoringEnabled = true
		val rawLevel = device.batteryLevel
		val batteryState = device.batteryState

		if (!isEnabled) device.batteryMonitoringEnabled = false

		val percentage = if (rawLevel < 0f) 0f else rawLevel * 100f

		println("$percentage $batteryState")
		return when {
			percentage >= 95f -> NativeBatteryStateFull()

			batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ->
				NativeBatteryStateCharging(percentage)

			batteryState == UIDeviceBatteryState.UIDeviceBatteryStateUnplugged ->
				NativeBatteryStateDisCharging(percentage)

			batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull ->
				NativeBatteryStateCharging(percentage)

			else -> NativeBatteryStateNoBatteryFound()
		}
	}

	actual override fun isBatteryInPowerSavingMode(): Boolean {
		return NSProcessInfo.processInfo.lowPowerModeEnabled
	}

	actual override fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit
	): Long {

		val handle = _handleId.load()
		if (handle != -1L) {
			_handleId.store(-1L)
			_registry.keys.forEach(::unsubscribeToBatteryState)
		}

		val device = UIDevice.currentDevice
		// enable battery monitoring
		if (!device.isBatteryMonitoringEnabled()) device.batteryMonitoringEnabled = true

		val notificationCenter = NSNotificationCenter.defaultCenter

		val stateObserver = notificationCenter.addObserverForName(
			UIDeviceBatteryStateDidChangeNotification, null, null
		) {
			when (val batteryState = batteryState()) {
				is NativeBatteryStateFull -> onFull()
				is NativeBatteryStateCharging -> onCharging(batteryState.amount)
				is NativeBatteryStateDisCharging -> onDisCharging(batteryState.amount)
				is NativeBatteryStateNoBatteryFound -> onBatteryNotFound()
				else -> onUnknown()
			}
		}

		val levelObserver = notificationCenter.addObserverForName(
			UIDeviceBatteryLevelDidChangeNotification, null, null
		) {
			when (val batteryState = batteryState()) {
				is NativeBatteryStateFull -> onFull()
				is NativeBatteryStateCharging -> onCharging(batteryState.amount)
				is NativeBatteryStateDisCharging -> onDisCharging(batteryState.amount)
				is NativeBatteryStateNoBatteryFound -> onBatteryNotFound()
				else -> onUnknown()
			}
		}

		val id = _handleId.fetchAndIncrement()
		val observerPair = IosObserverPair(levelObserver, stateObserver)
		_registry[id] = observerPair

		return id
	}


	actual override fun unsubscribeToBatteryState(readHandle: Long) {

		if (readHandle == -1L) return

		val observerPair = _registry.remove(readHandle) ?: return
		val notificationCenter = NSNotificationCenter.defaultCenter

		notificationCenter.removeObserver(observerPair.levelObserver)
		notificationCenter.removeObserver(observerPair.stateObserver)

		if (_registry.isEmpty()) {
			// stop all observers
			val device = UIDevice.currentDevice
			device.batteryMonitoringEnabled = false
		}
	}
}