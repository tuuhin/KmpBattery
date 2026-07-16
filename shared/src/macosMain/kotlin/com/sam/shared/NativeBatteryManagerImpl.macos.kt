package com.sam.shared

import com.sam.shared.native.NativeBatteryManager
import com.sam.shared.native.NativeBatteryState
import com.sam.shared.native.NativeBatteryStateCharging
import com.sam.shared.native.NativeBatteryStateDisCharging
import com.sam.shared.native.NativeBatteryStateFull
import com.sam.shared.native.NativeBatteryStateNoBatteryFound
import com.sam.shared.native.NativeBatteryStateUnknown
import io.github.oshai.kotlinlogging.KotlinLogging
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
		val snapshot: CFTypeRef = IOPSCopyPowerSourcesInfo() ?: return@memScoped -1
		val sourcesList: CFArrayRef = IOPSCopyPowerSourcesList(snapshot) ?: return@memScoped -1

		try {
			val count = CFArrayGetCount(sourcesList)
			if (count == 0L) return@memScoped -1

			// first power handle
			var powerSource: COpaquePointer? = null
			for (idx in 0..<count) {
				val source = CFArrayGetValueAtIndex(sourcesList, idx) ?: continue
				powerSource = source
			}

			if (powerSource == null) return@memScoped -1

			val description = IOPSGetPowerSourceDescription(snapshot, powerSource)
				?: return@memScoped -1

			val transportType = getStringKey("Transport Type", description)
			if (transportType != "Internal") return@memScoped -1

			getIntKey("Current Capacity", description)
		} finally {
			CFRelease(snapshot)
			CFRelease(sourcesList)
		}
	}

	actual override fun batteryState(): NativeBatteryState = readState()

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
		val subscriber =
			BatterySubscriber(onFull, onCharging, onDisCharging, onUnknown, onBatteryNotFound)
		val stableRef = StableRef.create(subscriber)
		_callbackRef = stableRef

		val nativeCallback = staticCFunction { context: COpaquePointer? ->
			if (context == null) return@staticCFunction
			val sub = context.asStableRef<BatterySubscriber>().get()
			when (val batteryState = readState()) {
				is NativeBatteryStateFull -> sub.onFull()
				is NativeBatteryStateCharging -> sub.onCharging(batteryState.amount)
				is NativeBatteryStateDisCharging -> sub.onDisCharging(batteryState.amount)
				is NativeBatteryStateNoBatteryFound -> sub.onBatteryNotFound()
				else -> sub.onUnknown()
			}
		}

		// run loop to the os
		val runLoopSource: CFRunLoopSourceRef = IOPSNotificationCreateRunLoopSource(
			nativeCallback,
			stableRef.asCPointer()
		) ?: run {
			_callbackRef?.dispose()
			return -1L
		}

		val mainLoop = CFRunLoopGetMain()
		CFRunLoopAddSource(mainLoop, runLoopSource, kCFRunLoopDefaultMode)

		return runLoopSource.toLong()
	}

	actual override fun unsubscribeToBatteryState(readHandle: Long) {
		if (readHandle == -1L) {
			_callbackRef?.dispose()
			return
		}

		val runLoopSource: CFRunLoopSourceRef =
			readHandle.toCPointer<CPointed>()?.reinterpret() ?: run {
				_callbackRef?.dispose()
				return
			}

		val mainRunLoop = CFRunLoopGetMain()
		CFRunLoopRemoveSource(mainRunLoop, runLoopSource, kCFRunLoopDefaultMode)

		CFRelease(runLoopSource)
		_callbackRef?.dispose()
	}

	companion object {

		private var _callbackRef: StableRef<BatterySubscriber>? = null
		private val logger = KotlinLogging.logger("MacosBatteryLogger")

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
			val snapshot: CFTypeRef =
				IOPSCopyPowerSourcesInfo() ?: return@memScoped NativeBatteryStateUnknown()

			val sourcesList: CFArrayRef =
				IOPSCopyPowerSourcesList(snapshot) ?: return@memScoped NativeBatteryStateUnknown()

			try {
				val count = CFArrayGetCount(sourcesList)
				if (count == 0L) return@memScoped NativeBatteryStateNoBatteryFound()

				// first power handle
				var powerSource: COpaquePointer? = null
				for (idx in 0..<count) {
					val source = CFArrayGetValueAtIndex(sourcesList, idx) ?: continue
					powerSource = source
				}

				if (powerSource == null) return@memScoped NativeBatteryStateNoBatteryFound()

				val description = IOPSGetPowerSourceDescription(snapshot, powerSource)
					?: return@memScoped NativeBatteryStateUnknown()

				val transportType = getStringKey("Transport Type", description)
				if (transportType != "Internal") return@memScoped NativeBatteryStateNoBatteryFound()

				val currentCapacity = getIntKey("Current Capacity", description)
				val maxCapacity = getIntKey("Max Capacity", description)
				val sourceState = getStringKey("Power Source State", description)
				val isCharging = getIntKey("Is Charging", description) == 1
				val percentage =
					if (maxCapacity > 0) (currentCapacity.toFloat() * 100) / maxCapacity else 0f

				logger.info { "SOURCE STATE: $sourceState PERCENTAGE:$percentage IS_CHARGING:$isCharging" }

				when {
					percentage >= 95 -> NativeBatteryStateFull()
					sourceState == "AC Power" || isCharging -> NativeBatteryStateCharging(percentage)
					sourceState == "Battery Power" -> NativeBatteryStateDisCharging(percentage)
					else -> NativeBatteryStateNoBatteryFound()
				}
			} finally {
				CFRelease(snapshot)
				CFRelease(sourcesList)
			}
		}
	}
}