package com.sam.kmp_battery

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.success
import com.sam.shared_desktop.NativeBatteryManager
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class TerminalApp : KoinComponent {

	private val batteryManager by inject<NativeBatteryManager>()
	private val terminal = Terminal()

	suspend fun run() = coroutineScope {

		val isInteractive = terminal.terminalInfo.inputInteractive
				&& terminal.terminalInfo.outputInteractive

		if (!isInteractive) {
			terminal.println(TextStyles.bold(TextColors.red("SORRY CANNOT RUN IT ON A NON INTERACTIVE TERMINAL")))
			return@coroutineScope
		}

		terminal.println(TextStyles.bold(TextColors.cyan("\n=== SYSTEM BATTERY UTILITY ===")))

		val options = listOf(
			"Read Battery Level",
			"Read Is Low Power Mode",
			"Exit"
		)

		var isRunning = true

		while (isRunning) {
			val selection = terminal.interactiveSelectList(
				entries = options,
				title = "Use arrow keys to select an option and press Enter:"
			)

			when (selection) {
				"Read Battery Level" -> terminal.success(batteryManager.batteryLevel())
				"Read Is Low Power Mode" -> terminal.success(batteryManager.isBatteryInPowerSavingMode())
				"Exit", null -> {
					isRunning = false
					terminal.danger(TextColors.magenta("\nGoodbye!"))
				}
			}
		}
	}
}