plugins {
	alias(libs.plugins.composeMultiplatform) apply false
	alias(libs.plugins.jetbrains.kotlin.jvm) apply false
	alias(libs.plugins.android.kotlin.multiplatform) apply false
	alias(libs.plugins.nucleus.framework) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.androidApplication) apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.composeHotReload) apply false
	alias(libs.plugins.catelog.update) apply false
	alias(libs.plugins.koin.compiler) apply false
}