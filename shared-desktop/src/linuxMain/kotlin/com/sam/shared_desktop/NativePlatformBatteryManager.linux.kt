package com.sam.shared_desktop

import com.sam.shared_desktop.linux.GDBusConnection
import com.sam.shared_desktop.linux.GError
import com.sam.shared_desktop.linux.GVariant
import com.sam.shared_desktop.linux.GVariantIter
import com.sam.shared_desktop.linux.G_BUS_TYPE_SYSTEM
import com.sam.shared_desktop.linux.G_DBUS_CALL_FLAGS_NONE
import com.sam.shared_desktop.linux.G_DBUS_PROXY_FLAGS_NONE
import com.sam.shared_desktop.linux.G_DBUS_SIGNAL_FLAGS_NONE
import com.sam.shared_desktop.linux.g_bus_get_sync
import com.sam.shared_desktop.linux.g_dbus_connection_signal_subscribe
import com.sam.shared_desktop.linux.g_dbus_connection_signal_unsubscribe
import com.sam.shared_desktop.linux.g_dbus_proxy_call_sync
import com.sam.shared_desktop.linux.g_dbus_proxy_get_cached_property
import com.sam.shared_desktop.linux.g_dbus_proxy_new_for_bus_sync
import com.sam.shared_desktop.linux.g_error_free
import com.sam.shared_desktop.linux.g_free
import com.sam.shared_desktop.linux.g_object_unref
import com.sam.shared_desktop.linux.g_strcmp0
import com.sam.shared_desktop.linux.g_variant_dup_string
import com.sam.shared_desktop.linux.g_variant_get
import com.sam.shared_desktop.linux.g_variant_get_double
import com.sam.shared_desktop.linux.g_variant_get_uint32
import com.sam.shared_desktop.linux.g_variant_iter_init
import com.sam.shared_desktop.linux.g_variant_iter_next
import com.sam.shared_desktop.linux.g_variant_new
import com.sam.shared_desktop.linux.g_variant_unref
import com.sam.shared_desktop.linux.gcharVar
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val logger = KotlinLogging.logger("LinuxBatteryManager")

private class BatteryCallbacks(
    val onFull: () -> Unit,
    val onCharging: (Float) -> Unit,
    val onDisCharging: (Float) -> Unit,
    val onUnknown: () -> Unit
)

@OptIn(ExperimentalAtomicApi::class)
actual class NativePlatformBatteryManager actual constructor() : NativeBatteryManager {

    init {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
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

    actual override fun isBatteryInPowerSavingMode(): Boolean = memScoped {
        val errorVar = alloc<CPointerVar<GError>>()
        var isPowerSavingMode = false

        val proxy = g_dbus_proxy_new_for_bus_sync(
            G_BUS_TYPE_SYSTEM,
            G_DBUS_PROXY_FLAGS_NONE,
            null,
            "net.hadess.PowerProfiles",
            "/net/hadess/PowerProfiles",
            "net.hadess.PowerProfiles",
            null,
            errorVar.ptr,
        )

        if (errorVar.value != null) {
            val err = errorVar.value?.pointed
            logger.warn { "Failed to connect to D-Bus proxy: ${err?.message?.toKString()}" }
            g_error_free(errorVar.value)
            return false
        }

        var variant: CPointer<GVariant>? = g_dbus_proxy_get_cached_property(proxy, "ActiveProfile")
        if (variant == null) {
            variant = g_dbus_proxy_call_sync(
                proxy,
                "org.freedesktop.DBus.Properties.Get",
                g_variant_new("(ss)", "net.hadess.PowerProfiles".cstr, "ActiveProfile".cstr),
                G_DBUS_CALL_FLAGS_NONE,
                -1,
                null,
                errorVar.ptr,
            )

            if (errorVar.value != null) {
                val err = errorVar.value?.pointed
                logger.warn { "Failed to call D-Bus method: ${err?.message?.toKString()}" }
                g_error_free(errorVar.value)
                g_object_unref(proxy) // Don't forget to unref proxy before early exit
                return false
            }

            val innerVariant = alloc<CPointerVar<GVariant>>()
            g_variant_get(
                variant,
                "(v)",
                innerVariant.ptr
            )
            g_variant_unref(variant)
            variant = innerVariant.value
        }

        val activeProfile: CPointer<gcharVar>? = variant?.let { v ->
            val str = g_variant_dup_string(v, null)
            g_variant_unref(v)
            str
        }

        if (activeProfile != null) {
            if (g_strcmp0(activeProfile, "power-saver") == 0) {
                isPowerSavingMode = true
            }
            g_free(activeProfile)
        }

        g_object_unref(proxy)
        isPowerSavingMode
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    actual override fun batteryState(): NativeBatteryState {

        val battery = FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY)
            ?: return NativeBatteryStateNoBatteryFound()

        val level = FileReadingUtil.readFile(
            "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$battery/capacity",
            3
        )?.trim()?.toIntOrNull()
            ?: return NativeBatteryStateUnknown()

        val status =
            FileReadingUtil.readFile(
                "${FileReadingUtil.POWER_INFO_DIR_LOCATION}/$battery/status",
                32
            )?.trim() ?: return NativeBatteryStateUnknown()

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
        onCharging: (amount: Float) -> Unit,
        onDisCharging: (amount: Float) -> Unit,
        onUnknown: () -> Unit,
        onBatteryNotFound: () -> Unit
    ): Long {

        val battery = FileReadingUtil.findPowerSupplyDevice(LinuxPowerClass.BATTERY)
            ?: run {
                onBatteryNotFound()
                return -1L
            }

        val batteryPath = "/org/freedesktop/UPower/devices/battery_$battery"

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

                    if (changedInterface.value?.toKString() != "org.freedesktop.UPower.Device") {
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

        return subscriptionId.toLong()
    }

    actual override fun unsubscribeToBatteryState(readHandle: Long) {

        val subId = readHandle.toUInt()
        if (readHandle < 1L) return

        val conn = connection ?: return
        g_dbus_connection_signal_unsubscribe(conn, subId)
        g_object_unref(conn)

        callbackRef?.dispose()
        callbackRef = null
    }

    companion object {
        private var callbackRef: StableRef<BatteryCallbacks>? = null
        private val connection: CPointer<GDBusConnection>? = null
    }
}
