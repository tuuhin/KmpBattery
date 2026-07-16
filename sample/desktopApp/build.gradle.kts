import org.jetbrains.compose.resources.ResourcesExtension

plugins {
	alias(libs.plugins.jetbrains.kotlin.jvm)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeHotReload)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.nucleus.framework)
}

kotlin {
	jvmToolchain(25)
}

dependencies {
	// compose
	implementation(libs.cmp.runtime)
	implementation(libs.cmp.foundation)
	implementation(libs.cmp.ui)
	implementation(libs.cmp.material3)
	implementation(libs.cmp.components.resources)
	implementation(compose.desktop.currentOs)
	// koin
	implementation(libs.koin.core)

	// nucleus
	implementation(libs.nucleus.core.application)
	implementation(libs.nucleus.decorated.window.tao)

	// shared data
	implementation(project(":sample:composeApp"))
}


compose.resources {
	publicResClass = false
	packageOfResClass = "com.sam.kmp_battery.resources"
	generateResClass = ResourcesExtension.ResourceClassGeneration.Auto

	customDirectory(
		sourceSetName = "main",
		directoryProvider = provider {
			layout.projectDirectory.dir("src/main/resources/composeResources")
		},
	)
}