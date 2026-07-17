package com.sam.kmp_battery.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun KmpBatteryTheme(
	isDarkTheme: Boolean,
	dynamicColor: Boolean,
	content: @Composable (() -> Unit)
) {

	val dynamicThemeState = rememberDynamicMaterialThemeState(
		isDark = isDarkTheme,
		style = PaletteStyle.TonalSpot,
		specVersion = ColorSpec.SpecVersion.SPEC_2025,
		seedColor = SeedColor,
	)

	DynamicMaterialExpressiveTheme(
		state = dynamicThemeState,
		motionScheme = MotionScheme.expressive(),
		animate = true,
		content = content,
	)
}