package com.sam.shared

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sam.shared.exceptions.CannotCreateStickyReceiver
import com.sam.shared.exceptions.InvalidBatteryAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val logger = KotlinLogging.logger { "AndroidBatteryManager" }

class AndroidBatteryManager(private val context: Context) : BatteryManager {

	private val filter: IntentFilter
		get() = IntentFilter().apply {
			addAction(Intent.ACTION_POWER_CONNECTED)
			addAction(Intent.ACTION_POWER_DISCONNECTED)
			addAction(Intent.ACTION_BATTERY_CHANGED)
			addAction(Intent.ACTION_BATTERY_LOW)
		}

	override suspend fun batteryLevel(): Int = suspendCoroutine { cont ->
		try {
			val intent = ContextCompat.registerReceiver(
				context,
				null,
				filter,
				ContextCompat.RECEIVER_EXPORTED
			) ?: run {
				cont.resumeWithException(CannotCreateStickyReceiver())
				return@suspendCoroutine
			}

			val level: Int = intent.getIntExtra(AndroidOSBatteryManager.EXTRA_LEVEL, -1)
			val scale: Int = intent.getIntExtra(AndroidOSBatteryManager.EXTRA_SCALE, -1)

			val batteryLevel = (level * 100f / scale).coerceIn(0f..100f).toInt()
			logger.debug { "BATTERY LEVEL FOUND :$batteryLevel" }

			cont.resumeWith(Result.success(batteryLevel))
		} catch (e: Exception) {
			cont.resumeWithException(e)
		}
	}


	override suspend fun isBatteryInPowerSavingMode(): Boolean {
		val powerManager = context.getSystemService<PowerManager>() ?: return false
		return powerManager.isPowerSaveMode
	}

	override suspend fun batteryState(): BatteryState {
		return suspendCoroutine { cont ->
			try {
				val intent = ContextCompat.registerReceiver(
					context,
					null,
					filter,
					ContextCompat.RECEIVER_EXPORTED
				) ?: run {
					cont.resumeWithException(CannotCreateStickyReceiver())
					return@suspendCoroutine
				}

				val state = intent.toBatteryState() ?: run {
					cont.resumeWithException(InvalidBatteryAction())
					return@suspendCoroutine
				}
				cont.resumeWith(Result.success(state))
			} catch (e: Exception) {
				cont.resumeWithException(e)
			}
		}
	}

	override val batteryStateFlow: Flow<BatteryState>
		get() = callbackFlow {
			// a initial data launch
			launch { batteryState() }

			val receiver = object : BroadcastReceiver() {
				override fun onReceive(p0: Context?, p1: Intent?) {
					val batteryState = p1?.toBatteryState() ?: return
					trySend(batteryState)
				}
			}

			ContextCompat.registerReceiver(
				context,
				receiver,
				filter,
				ContextCompat.RECEIVER_EXPORTED
			)

			awaitClose {
				context.unregisterReceiver(receiver)
			}
		}
}