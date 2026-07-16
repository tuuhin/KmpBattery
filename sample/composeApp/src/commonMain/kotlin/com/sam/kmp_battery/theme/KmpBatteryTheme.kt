package com.sam.kmp_battery.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
expect fun KmpBatteryTheme(
	isDarkTheme: Boolean = isSystemInDarkTheme(),
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit
)