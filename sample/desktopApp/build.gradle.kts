import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.resources.ResourcesExtension
import java.util.Properties

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
	implementation(libs.nucleus.decorated.window.material3)

	// shared data
	implementation(project(":sample:composeApp"))
}

nucleus.application {
	mainClass = "com.sam.kmp_battery.MainKt"

	jvmArgs += listOf(
		"--enable-native-access=ALL-UNNAMED",
		"--add-opens=java.base/java.nio=ALL-UNNAMED",
		"-Dsun.misc.unsafe.allow=true",
	)

	buildTypes {
		release {
			proguard {
				version = "7.9.1"
				isEnabled = false
				optimize = true
				obfuscate = false
				joinOutputJars = true
				configurationFiles.from(project.file("proguard-rules.pro"))
			}
		}
	}

	nativeDistributions {

		val commonProperties = Properties().apply {
			val commons = project.file("packaging.properties")
			commons.inputStream().use(::load)
		}

		// application targets
		targetFormats(TargetFormat.Msi, TargetFormat.Portable, TargetFormat.Nsis, TargetFormat.Deb)

		// target base config
		appName = commonProperties.getProperty("APP_NAME")
		packageName = commonProperties.getProperty("APP_PACKAGE_NAME")
		packageVersion = commonProperties.getProperty("APP_PACKAGE_VERSION")
		description = commonProperties.getProperty("APP_DESCRIPTION")
		vendor = commonProperties.getProperty("APP_VENDOR")
		copyright = commonProperties.getProperty("APP_COPYRIGHT")
		licenseFile.set(rootProject.file("LICENCE"))

		// java modules
		modules("java.instrument", "jdk.unsupported", "java.management")

		// target common connfiguration
		outputBaseDir.set(project.layout.buildDirectory.dir("desktop"))
		appResourcesRootDir.set(project.layout.projectDirectory.dir("desktopResources"))
		compressionLevel = CompressionLevel.Normal
		cleanupNativeLibs = true
	}
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