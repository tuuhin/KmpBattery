package com.sam.kmp_battery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sam.kmp_battery.di.KoinApp
import org.koin.compose.KoinApplication
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.includes
import org.koin.plugin.module.dsl.koinConfiguration

@Composable
fun App(configuration: KoinAppDeclaration? = null) {
	KoinApplication(
		configuration = koinConfiguration<KoinApp> {
			includes(configuration)
		},
	) {
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			BatteryScreen()
		}
	}
}