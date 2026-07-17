package com.sam.shared_desktop

import kotlinx.cinterop.*
import platform.linux.*
import platform.posix.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi

private val logger = KotlinLogging.logger("LinuxBatteryManager")

private class BatteryCallbacks(
	val onFull: () -> Unit,
	val onCharging: (amount: Float) -> Unit,
	val onDisCharging: (amount: Float) -> Unit,
	val onUnknown: () -> Unit
)

actual class NativePlatformBatteryManager actual constructor() : NativeBatteryManager {

	actual override fun batteryLevel(): Int {
		val powerSupplyType =
			FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY) ?: return 0
		val levelFileName = "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$powerSupplyType/capacity"
		val levelAsString = FileReadingUtil.readFile(levelFileName, 3) ?: return 0
		return levelAsString.trim().toIntOrNull() ?: 0
	}

	actual override fun isBatteryInPowerSavingMode(): Boolean {
		logger.info { "NO DIRECT API IS READY" }
		return false
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	actual override fun batteryState(): NativeBatteryState {
		val powerSupplyType = FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY)
			?: return NativeBatteryStateNoBatteryFound()

		val fileName = "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$powerSupplyType/capacity"
		val levelAsString =
			FileReadingUtil.readFile(fileName, 3) ?: return NativeBatteryStateUnknown()

		val batteryLevel = levelAsString.trim().toIntOrNull() ?: 0

		val statusFile = "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$powerSupplyType/status"
		val statusAsString = FileReadingUtil.readFile(statusFile, 3)?.trim()
			?: return NativeBatteryStateUnknown()
		val batteryStatus = when (statusAsString) {
			"Discharging" -> PowerStatus.DISCHARGING
			"Charging" -> PowerStatus.CHARGING
			else -> PowerStatus.UNKNOWN
		}

		if (batteryLevel == 100) return NativeBatteryStateFull()
		return when (batteryStatus) {
			PowerStatus.CHARGING -> NativeBatteryStateCharging(batteryLevel.toFloat())
			PowerStatus.DISCHARGING -> NativeBatteryStateDisCharging(batteryLevel.toFloat())
			PowerStatus.UNKNOWN -> NativeBatteryStateUnknown()
		}
	}

	actual override fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit
	): Long {
		val batteryPath = "/org/freedesktop/UPower/devices/battery_BAT0"
		if (access("/sys/class/power_supply/BAT0", F_OK) != 0) {
			onBatteryNotFound()
			return 0L
		}

		val connection = g_bus_get_sync(G_BUS_TYPE_SYSTEM, null, null) ?: run {
			onBatteryNotFound()
			return 0L
		}

		val callbacks = BatteryCallbacks(onFull, onCharging, onDisCharging, onUnknown)
		val stableRef = StableRef.create(callbacks)

		val subscriptionId = g_dbus_connection_signal_subscribe(
			connection,
			"org.freedesktop.UPower",
			"org.freedesktop.DBus.Properties",
			"PropertiesChanged",
			batteryPath,
			null,
			G_DBUS_SIGNAL_FLAGS_NONE,
			staticCFunction { _, _, _, _, _, parameters, userData ->
				if (parameters == null || userData == null) return@staticCFunction

				val actualCallbacks = userData.asCPointer<gpointer>()!!.asStableRef<BatteryCallbacks>().get()

				memScoped {
					val changedInterface = alloc<CPointerVar<ByteVar>>()
					val changedProperties = alloc<CPointerVar<GVariant>>()

					// Unpack parameters tuple safely
					g_variant_get(parameters, "(&s@a{sv}^as)", changedInterface.ptr, changedProperties.ptr, null)

					if (g_strcmp0(changedInterface.value, "org.freedesktop.UPower.Device") == 0) {
						val iter = alloc<GVariantIter>()
						val key = alloc<CPointerVar<ByteVar>>()
						val value = alloc<CPointerVar<GVariant>>()

						g_variant_iter_init(iter.ptr, changedProperties.value)

						var currentPercentage = -1f
						var currentState = -1

						while (g_variant_iter_next(iter.ptr, "{&sv}", key.ptr, value.ptr) != 0) {
							val currentKey = key.value?.toKString()

							if (currentKey == "State") {
								currentState = g_variant_get_uint32(value.value).toInt()
							} else if (currentKey == "Percentage") {
								currentPercentage = g_variant_get_double(value.value).toFloat()
							}
							g_variant_unref(value.value)
						}

						if (currentState != -1 || currentPercentage != -1f) {
							val finalPercent = if (currentPercentage >= 0f) currentPercentage else 0f

							when (currentState) {
								1 -> actualCallbacks.onCharging(finalPercent)
								2 -> actualCallbacks.onDisCharging(finalPercent)
								4 -> actualCallbacks.onFull()
								else -> actualCallbacks.onUnknown()
							}
						}
					}
					g_variant_unref(changedProperties.value)
				}
			},
			stableRef.asCPointer(),
			null
		)

		g_object_unref(connection)

		val nativePointerLong = stableRef.asCPointer().toLong()
		return (subscriptionId.toLong() shl 32) or (nativePointerLong and 0xFFFFFFFFL)
	}

	actual override fun unsubscribeToBatteryState(readHandle: Long) {
		if (readHandle == 0L) return

		val subscriptionId = (readHandle ushr 32).toUInt()
		val pointerLong = readHandle and 0xFFFFFFFFL
		val stableRefPointer = pointerLong.toCPointer<COpaque>()

		val connection = g_bus_get_sync(G_BUS_TYPE_SYSTEM, null, null)
		if (connection != null) {
			g_dbus_connection_signal_unsubscribe(connection, subscriptionId)
			g_object_unref(connection)
		}

		stableRefPointer?.asStableRef<BatteryCallbacks>()?.dispose()
	}
}