package com.sam.shared

import com.sam.shared.native.NativeBatteryManager
import com.sam.shared.native.NativeBatteryState
import com.sam.shared.native.NativeBatteryStateCharging
import com.sam.shared.native.NativeBatteryStateDisCharging
import com.sam.shared.native.NativeBatteryStateFull
import com.sam.shared.native.NativeBatteryStateNoBatteryFound
import com.sam.shared.native.NativeBatteryStateUnknown
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRunLoopAddSource
import platform.CoreFoundation.CFRunLoopGetMain
import platform.CoreFoundation.CFRunLoopRemoveSource
import platform.CoreFoundation.CFRunLoopSourceRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSProcessInfo
import platform.Foundation.lowPowerModeEnabled
import platform.IOKit.IOPSCopyPowerSourcesInfo
import platform.IOKit.IOPSCopyPowerSourcesList
import platform.IOKit.IOPSGetPowerSourceDescription
import platform.IOKit.IOPSNotificationCreateRunLoopSource
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private data class BatterySubscriber(
	val onFull: () -> Unit,
	val onCharging: (amount: Float) -> Unit,
	val onDisCharging: (amount: Float) -> Unit,
	val onUnknown: () -> Unit,
	val onBatteryNotFound: () -> Unit
)

@OptIn(ExperimentalAtomicApi::class)
actual class NativeBatteryManagerImpl actual constructor() : NativeBatteryManager {

	actual override fun batteryLevel(): Int = memScoped {
		val snapshot: CFTypeRef = IOPSCopyPowerSourcesInfo() ?: run {
			logger.warn { "UNABLE TO READ BATTERY LEVEL SOURCE INFO CANNOT BE DETERMINED" }
			return@memScoped -1
		}
		val sourcesList: CFArrayRef = IOPSCopyPowerSourcesList(snapshot) ?: run {
			logger.warn { "UNABLE TO READ BATTERY LEVEL SOURCE LIST CANNOT BE DETERMINED" }
			CFRelease(snapshot)
			return@memScoped -1
		}

		try {
			val count = CFArrayGetCount(sourcesList)
			if (count == 0L) return@memScoped -1

			var powerSource: COpaquePointer? = null
			for (idx in 0..<count) {
				val source = CFArrayGetValueAtIndex(sourcesList, idx) ?: continue
				powerSource = source
			}

			if (powerSource == null) {
				logger.warn { "NO POWER SOURCE FOUND" }
				return@memScoped -1
			}

			val description = IOPSGetPowerSourceDescription(snapshot, powerSource) ?: run {
				logger.warn { "FAILED TO READ DESCRIPTION POWER SET" }
				return@memScoped -1
			}

			val transportType = getStringKey("Transport Type", description)
			if (transportType != "Internal") return@memScoped -1


			val currentCapacity = getIntKey("Current Capacity", description)
			logger.info { "CURRENT BATTERY LEVEL: $currentCapacity%" }
			currentCapacity
		} catch (e: Exception) {
			logger.error { "CANNOT READ BATTERY STATE: ${e.message}" }
			-1
		} finally {
			CFRelease(snapshot)
			CFRelease(sourcesList)
		}
	}

	actual override fun batteryState(): NativeBatteryState = readState()

	actual override fun isBatteryInPowerSavingMode(): Boolean {
		val isLowPower = NSProcessInfo.processInfo.lowPowerModeEnabled
		logger.info { "LOW POWER MODE CHECK: Enabled = $isLowPower" }
		return isLowPower
	}

	actual override fun subscribedToBatteryState(
		onFull: () -> Unit,
		onCharging: (amount: Float) -> Unit,
		onDisCharging: (amount: Float) -> Unit,
		onUnknown: () -> Unit,
		onBatteryNotFound: () -> Unit
	): Long {
		logger.info { "REGISTERING TO BATTERY CHANGE CALLBACK" }

		val subscriber =
			BatterySubscriber(onFull, onCharging, onDisCharging, onUnknown, onBatteryNotFound)
		val stableRef = StableRef.create(subscriber)
		_callbackRef = stableRef

		val nativeCallback = staticCFunction { context: COpaquePointer? ->
			if (context == null) return@staticCFunction

			val sub = context.asStableRef<BatterySubscriber>().get()
			println("HARDWARE NOTIFICATION RECEIVED")

			when (val batteryState = readState()) {
				is NativeBatteryStateFull -> sub.onFull()
				is NativeBatteryStateCharging -> sub.onCharging(batteryState.amount)
				is NativeBatteryStateDisCharging -> sub.onDisCharging(batteryState.amount)
				is NativeBatteryStateNoBatteryFound -> sub.onBatteryNotFound()
				else -> sub.onUnknown()
			}
		}

		val runLoopSource: CFRunLoopSourceRef = IOPSNotificationCreateRunLoopSource(
			nativeCallback,
			stableRef.asCPointer()
		) ?: run {
			logger.error { "FAILED TO CREATE A RUN LOOP" }
			_callbackRef?.dispose()
			_callbackRef = null
			return -1L
		}

		val mainLoop = CFRunLoopGetMain()
		CFRunLoopAddSource(mainLoop, runLoopSource, kCFRunLoopDefaultMode)
		return runLoopSource.toLong()
	}

	actual override fun unsubscribeToBatteryState(readHandle: Long) {
		if (readHandle == -1L) {
			logger.warn { "INVALID HANDLE RECEIVED" }
			_callbackRef?.dispose()
			_callbackRef = null
			return
		}

		val runLoopSource: CFRunLoopSourceRef = readHandle.toCPointer<CPointed>()
			?.reinterpret() ?: run {
			_callbackRef?.dispose()
			_callbackRef = null
			return
		}

		val mainRunLoop = CFRunLoopGetMain()
		CFRunLoopRemoveSource(mainRunLoop, runLoopSource, kCFRunLoopDefaultMode)

		CFRelease(runLoopSource)

		_callbackRef?.dispose()
		_callbackRef = null
		logger.info { "CLEARING SUBSCRIBERS" }
	}

	companion object {

		private val logger by lazy {
			KotlinLoggingConfiguration.logStartupMessage = false
			KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
			KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
			KotlinLogging.logger("MacosBatteryLogger")
		}

		private var _callbackRef: StableRef<BatterySubscriber>? = null

		private fun getStringKey(key: String, description: CFDictionaryRef): String? = memScoped {
			val cfKey = CFStringCreateWithCString(null, key, kCFStringEncodingUTF8)
			val cfValue = CFDictionaryGetValue(description, cfKey)
			CFRelease(cfKey)
			if (cfValue == null) return null

			val buffer = allocArray<ByteVar>(256)
			val isStringReadSuccess =
				CFStringGetCString(cfValue.reinterpret(), buffer, 256, kCFStringEncodingUTF8)
			if (!isStringReadSuccess) return@memScoped null
			buffer.toKString()
		}

		private fun getIntKey(key: String, description: CFDictionaryRef): Int = memScoped {
			val cfKey = CFStringCreateWithCString(null, key, kCFStringEncodingUTF8)
			val cfValue = CFDictionaryGetValue(description, cfKey)
			CFRelease(cfKey)

			if (cfValue == null) return -1


			val intVar = alloc<IntVar>()
			val isReadSuccess =
				CFNumberGetValue(cfValue.reinterpret(), kCFNumberIntType, intVar.ptr)

			if (!isReadSuccess) return@memScoped -1
			return intVar.value
		}

		private fun readState() = memScoped {
			val snapshot: CFTypeRef = IOPSCopyPowerSourcesInfo() ?: run {
				logger.warn { "UNABLE TO READ BATTERY LEVEL SOURCE INFO CANNOT BE DETERMINED" }
				return@memScoped NativeBatteryStateNoBatteryFound()
			}
			val sourcesList: CFArrayRef = IOPSCopyPowerSourcesList(snapshot) ?: run {
				logger.warn { "UNABLE TO READ BATTERY LEVEL SOURCE LIST CANNOT BE DETERMINED" }
				CFRelease(snapshot)
				return@memScoped NativeBatteryStateNoBatteryFound()
			}

			try {
				val count = CFArrayGetCount(sourcesList)
				if (count == 0L) return@memScoped NativeBatteryStateNoBatteryFound()

				var powerSource: COpaquePointer? = null
				for (idx in 0..<count) {
					val source = CFArrayGetValueAtIndex(sourcesList, idx) ?: continue
					powerSource = source
				}

				if (powerSource == null) return@memScoped NativeBatteryStateNoBatteryFound()

				val description = IOPSGetPowerSourceDescription(snapshot, powerSource) ?: run {
					return@memScoped NativeBatteryStateNoBatteryFound()
				}

				val transportType = getStringKey("Transport Type", description)
				if (transportType != "Internal") {
					return@memScoped NativeBatteryStateNoBatteryFound()
				}

				val currentCapacity = getIntKey("Current Capacity", description)
				val maxCapacity = getIntKey("Max Capacity", description)
				val sourceState = getStringKey("Power Source State", description)
				val isCharging = getIntKey("Is Charging", description) == 1
				val percentage = if (maxCapacity > 0)
					(currentCapacity.toFloat() * 100) / maxCapacity
				else 0f

				logger.info { "SourceState: '$sourceState', Capacity: $currentCapacity/$maxCapacity ($percentage%), IsCharging flag: $isCharging" }

				when {
					percentage >= 95f -> NativeBatteryStateFull()
					sourceState == "AC Power" || isCharging -> NativeBatteryStateCharging(percentage)
					sourceState == "Battery Power" -> NativeBatteryStateDisCharging(percentage)
					else -> {
						logger.warn { "UNKNOWN/NO_BATTERY" }
						NativeBatteryStateNoBatteryFound()
					}
				}
			} catch (_: Exception) {
				NativeBatteryStateUnknown()
			} finally {
				CFRelease(snapshot)
				CFRelease(sourcesList)
			}
		}
	}
}