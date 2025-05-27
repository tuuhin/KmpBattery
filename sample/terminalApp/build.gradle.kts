plugins {
	alias(libs.plugins.kotlinMultiplatform)
}
kotlin {

	linuxX64()
	mingwX64()

	sourceSets {
		commonMain.dependencies {
			implementation(project(":shared"))
		}
	}
}
