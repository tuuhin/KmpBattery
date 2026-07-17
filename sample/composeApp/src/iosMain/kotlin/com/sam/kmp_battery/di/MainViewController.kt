package com.sam.kmp_battery.di

import androidx.compose.ui.window.ComposeUIViewController
import com.sam.kmp_battery.App
import com.sam.kmp_battery.theme.KmpBatteryTheme

fun MainViewController() = ComposeUIViewController {
	KmpBatteryTheme(dynamicColor = true) {
		App()
	}
}