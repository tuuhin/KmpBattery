package com.sam.shared_desktop

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.sam.shared_desktop.linux.*
import platform.linux.*
import platform.posix.*

private val logger = KotlinLogging.logger("LinuxBatteryManager")

private class BatteryCallbacks(
    val onFull: () -> Unit,
    val onCharging: (Float) -> Unit,
    val onDisCharging: (Float) -> Unit,
    val onUnknown: () -> Unit
)

private data class SubscriptionHandle(
    val connection: CPointer<GDBusConnection>,
    val subscriptionId: UInt
)

actual class NativePlatformBatteryManager actual constructor() : NativeBatteryManager {

    companion object {
        private const val HANDLE = 1L

        private var callbackRef: StableRef<BatteryCallbacks>? = null
        private var subscription: SubscriptionHandle? = null
    }

    actual override fun batteryLevel(): Int {
        val battery =
            FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY) ?: return 0

        val levelFile =
            "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$battery/capacity"

        return FileReadingUtil.readFile(levelFile, 3)
            ?.trim()
            ?.toIntOrNull()
            ?: 0
    }

    actual override fun isBatteryInPowerSavingMode(): Boolean {
        logger.info { "NO DIRECT API IS READY" }
        return false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    actual override fun batteryState(): NativeBatteryState {

        val battery =
            FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY)
                ?: return NativeBatteryStateNoBatteryFound()

        val level =
            FileReadingUtil.readFile(
                "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$battery/capacity",
                3
            )?.trim()?.toIntOrNull()
                ?: return NativeBatteryStateUnknown()

        val status =
            FileReadingUtil.readFile(
                "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$battery/status",
                32
            )?.trim()
                ?: return NativeBatteryStateUnknown()

        if (level == 100)
            return NativeBatteryStateFull()

        return when (status) {
            "Charging" -> NativeBatteryStateCharging(level.toFloat())
            "Discharging" -> NativeBatteryStateDisCharging(level.toFloat())
            else -> NativeBatteryStateUnknown()
        }
    }

    actual override fun subscribedToBatteryState(
        onFull: () -> Unit,
        onCharging: (amount:Float) -> Unit,
        onDisCharging: (amount:Float) -> Unit,
        onUnknown: () -> Unit,
        onBatteryNotFound: () -> Unit
    ): Long {

        // Remove any previous subscription.
        unsubscribeToBatteryState(HANDLE)

        val battery =
            FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY)
                ?: run {
                    onBatteryNotFound()
                    return -1L
                }

        val batteryPath =
            "/org/freedesktop/UPower/devices/battery_$battery"

        val connection =
            g_bus_get_sync(G_BUS_TYPE_SYSTEM, null, null)
                ?: run {
                    onBatteryNotFound()
                    return -1L
                }

        callbackRef = StableRef.create(
            BatteryCallbacks(
                onFull = onFull,
                onCharging = onCharging,
                onDisCharging = onDisCharging,
                onUnknown = onUnknown
            )
        )

        val subscriptionId = g_dbus_connection_signal_subscribe(
            connection,
            "org.freedesktop.UPower",
            "org.freedesktop.DBus.Properties",
            "PropertiesChanged",
            batteryPath,
            null,
            G_DBUS_SIGNAL_FLAGS_NONE,
            staticCFunction { _, _, _, _, _, parameters, userData ->

                if (parameters == null || userData == null)
                    return@staticCFunction

                val callbacks = userData
                    .asStableRef<BatteryCallbacks>()
                    .get()

                memScoped {

                    val changedInterface = alloc<CPointerVar<ByteVar>>()
                    val changedProperties = alloc<CPointerVar<GVariant>>()

                    g_variant_get(
                        parameters,
                        "(&s@a{sv}^as)",
                        changedInterface.ptr,
                        changedProperties.ptr,
                        null
                    )

                    if (changedInterface.value?.toKString() !=
                        "org.freedesktop.UPower.Device"
                    ) {
                        g_variant_unref(changedProperties.value)
                        return@memScoped
                    }

                    val iter = alloc<GVariantIter>()
                    g_variant_iter_init(iter.ptr, changedProperties.value)

                    val key = alloc<CPointerVar<ByteVar>>()
                    val value = alloc<CPointerVar<GVariant>>()

                    var state = -1
                    var percentage = -1f

                    while (
                        g_variant_iter_next(
                            iter.ptr,
                            "{&sv}",
                            key.ptr,
                            value.ptr
                        ) != 0
                    ) {

                        when (key.value?.toKString()) {
                            "State" ->
                                state = g_variant_get_uint32(value.value).toInt()

                            "Percentage" ->
                                percentage = g_variant_get_double(value.value).toFloat()
                        }

                        g_variant_unref(value.value)
                    }

                    g_variant_unref(changedProperties.value)

                    when (state) {
                        1 -> callbacks.onCharging(percentage.coerceAtLeast(0f))
                        2 -> callbacks.onDisCharging(percentage.coerceAtLeast(0f))
                        4 -> callbacks.onFull()
                        else -> callbacks.onUnknown()
                    }
                }
            },
            callbackRef!!.asCPointer(),
            null
        )

        subscription = SubscriptionHandle(
            connection = connection,
            subscriptionId = subscriptionId
        )

        return HANDLE
    }

    actual override fun unsubscribeToBatteryState(readHandle: Long) {

        if (readHandle != HANDLE)
            return

        subscription?.let {
            g_dbus_connection_signal_unsubscribe(
                it.connection,
                it.subscriptionId
            )

            g_object_unref(it.connection)
        }

        subscription = null

        callbackRef?.dispose()
        callbackRef = null
    }
}
