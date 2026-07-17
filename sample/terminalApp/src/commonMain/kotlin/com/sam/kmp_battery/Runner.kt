package com.sam.kmp_battery

import com.sam.kmp_battery.di.KoinApp
import org.koin.plugin.module.dsl.startKoin

suspend fun platformRunner() {
	// start koin
	startKoin<KoinApp>()
	// run the app
	TerminalApp().run()
}