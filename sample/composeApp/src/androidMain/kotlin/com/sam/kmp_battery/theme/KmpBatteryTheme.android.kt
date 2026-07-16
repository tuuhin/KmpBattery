package com.sam.kmp_battery.theme

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

	val context = LocalContext.current

	val seedColor = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			if (isDarkTheme) dynamicDarkColorScheme(context).primary
			else dynamicLightColorScheme(context).primary
		}

		else -> SeedColor
	}

	val dynamicThemeState = rememberDynamicMaterialThemeState(
		isDark = isDarkTheme,
		style = PaletteStyle.TonalSpot,
		specVersion = ColorSpec.SpecVersion.SPEC_2025,
		seedColor = seedColor,
	)

	DynamicMaterialExpressiveTheme(
		state = dynamicThemeState,
		motionScheme = MotionScheme.expressive(),
		animate = true,
		content = content,
	)
}