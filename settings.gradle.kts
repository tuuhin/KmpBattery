pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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