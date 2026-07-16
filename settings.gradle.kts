pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "KmpBattery"
include(":shared")
include(":sample:terminalApp")
include(":sample:desktopApp")
include(":sample:androidApp")
include(":sample:composeApp")