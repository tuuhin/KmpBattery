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

	nativeDistributions {

		val commonProperties = Properties().apply {
			val commons = project.file("packaging.properties")
			commons.inputStream().use(::load)
		}

		// application targets
		targetFormats(TargetFormat.Msi, TargetFormat.Portable, TargetFormat.Deb)

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

		val packagingRoot = project.layout.projectDirectory.dir("packaging")
		windows {
			iconFile.set(packagingRoot.file("windows/kmp_battery.ico"))
			upgradeUuid =
				commonProperties.getProperty("WINDOWS_UPGRADE_UUID", null)?.ifEmpty { null }
			console = false
			perUserInstall = true
			dirChooser = true

			appx {

				applicationId = commonProperties.getProperty("APP_PACKAGE_NAME")
				publisherDisplayName = commonProperties.getProperty("APP_VENDOR")
				displayName = commonProperties.getProperty("APP_NAME")
				publisher = commonProperties.getProperty("WINDOWS_APPX_PUBLISHER")
				identityName = commonProperties.getProperty("APP_PACKAGE_NAME")

				languages = listOf("en-US")
				backgroundColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND")
				showNameOnTiles = true
				addAutoLaunchExtension = false
				setBuildNumber = true

				storeLogo.set(packagingRoot.file("windows/appx/StoreLogo.png"))
				square44x44Logo.set(packagingRoot.file("windows/appx/Square44x44Logo.png"))
				square150x150Logo.set(packagingRoot.file("windows/appx/Square150x150Logo.png"))
				wide310x150Logo.set(packagingRoot.file("windows/appx/Wide310x150Logo.png"))
			}
		}
	}
}

compose.resources {
	publicResClass = false
	packageOfResClass = "com.sam.kmp_battery.resources"
	generateResClass = ResourcesExtension.ResourceClassGeneration.Auto

	customDirectory(
		sourceSetName = "main",
		directoryProvider = provider {
			layout.projectDirectory.dir("src/composeResources")
		},
	)
}

val generateWindowsAppIcon = tasks.register<Exec>("genAppIcon") {
	group = "nucleus packaging"
	description = "Generates clipped, rounded Windows assets from the SVG source icon."

	val icon = project.layout.projectDirectory.file("packaging/kmp_battery.svg").asFile
	val commonProperties = Properties().apply {
		val commons = project.file("packaging.properties")
		commons.inputStream().use(::load)
	}

	val script = project.layout.projectDirectory.file("packaging/windows/app_images.bat").asFile
	val bgColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND", "#C4F18C")
	val outputDir = project.layout.projectDirectory.dir("packaging/windows/appx").asFile

	workingDir(project.layout.projectDirectory.file("packaging"))
	commandLine(
		"cmd", "/c", script.absolutePath, icon.absolutePath, outputDir.absolutePath,
		"-c", bgColor, "-r", "12",
	)
	onlyIf {
		org.gradle.internal.os.OperatingSystem.current().isWindows
	}
}