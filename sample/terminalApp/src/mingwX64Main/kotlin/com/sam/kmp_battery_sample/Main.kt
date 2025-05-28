package com.sam.kmp_battery_sample

import com.sam.shared.BatteryManagerFactory
import kotlinx.coroutines.runBlocking

fun main() {
	val factory = BatteryManagerFactory()
	val manager = factory.createProvider()
	runBlocking {
		runner(manager)
	}
}