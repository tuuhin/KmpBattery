package com.sam.kmp_battery

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import java.util.Locale

fun main(args: Array<String>) = nucleusApplication(
	args = args,
	defaultLocale = Locale.ENGLISH,
) {
	// compose stack trace.
	Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)

	DecoratedWindow(
		onCloseRequest = ::exitApplication,
		title = "Kmp Battery App",
	) {
		TitleBar(modifier = Modifier.macOSLargeCornerRadius()) { _ ->
			Text(title, modifier = Modifier.padding(8.dp))
		}
		App {
			printLogger()
		}
	}
}