package com.sam.kmp_battery_sample

import com.sam.shared.BatteryManager

suspend fun runner(manager: BatteryManager) {
	println("----------------------------------------------")
	println("\t\t BATTERY MANAGER \t\t")
	println("This is an experimental battery preview terminal app, Select an option to continue")
	do {
		println(
			"""
		1. Battery State (Charging,Discharging,Full or Not Found)
		2. Battery Level (Values between 0-100)
		3. Is Power Saver Mode
		4 or Other to Quit
	""".trimIndent()
		)
		var option = readln()

		val validOption = option.trim().toIntOrNull() ?: 4
		when (validOption) {
			1 -> println("BATTERY STATE :${manager.batteryState()}")
			2 -> println("BATTERY LEVEL: ${manager.batteryLevel()}")
			3 -> println("BATTERY LEVEL: ${manager.isBatteryInPowerSavingMode()}")
			else -> {}
		}
	} while (validOption <= 3)

	println("-----------------------------------------------")
	println("\t\tTHANK YOU \t\t")
}