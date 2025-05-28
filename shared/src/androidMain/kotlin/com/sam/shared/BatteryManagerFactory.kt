package com.sam.shared

import android.content.Context

actual class BatteryManagerFactory(private val context: Context) {

	actual fun createProvider(): BatteryManager = AndroidBatteryManager(context)
}