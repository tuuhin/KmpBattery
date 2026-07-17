package com.sam.kmp_battery

import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.sam.kmp_battery.resources.Res
import com.sam.kmp_battery.resources.app_title
import com.sam.kmp_battery.resources.kmp_battery
import com.sam.kmp_battery.theme.KmpBatteryTheme
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.material.MaterialTitleBar
import dev.nucleusframework.window.material.rememberMaterialTitleBarStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

fun main(args: Array<String>) = nucleusApplication(
	args = args,
	defaultLocale = Locale.ENGLISH,
) {

	// compose stack trace.
	Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
	val windowState = rememberWindowState(position = WindowPosition(Alignment.Center))

	KmpBatteryTheme(dynamicColor = true) {

		val colorScheme = MaterialTheme.colorScheme
		val style = rememberMaterialTitleBarStyle(colorScheme = colorScheme)

		MaterialDecoratedWindow(
			onCloseRequest = ::exitApplication,
			title = stringResource(Res.string.app_title),
			icon = painterResource(Res.drawable.kmp_battery),
			minimumSize = DpSize(640.dp, 480.dp),
			state = windowState,
		) {
			MaterialTitleBar(
				modifier = Modifier.macOSLargeCornerRadius(),
				style = style
			) {
				Text(
					text = title,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.align(Alignment.CenterHorizontally),
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
			App {
				printLogger()
			}
		}
	}
}