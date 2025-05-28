plugins {
	alias(libs.plugins.kotlinMultiplatform)
}
kotlin {

	val targets = listOf(linuxX64(), mingwX64())

	targets.forEach {
		it.binaries {
			executable {
				entryPoint = "com.sam.kmp_battery_sample.main"
			}
		}
	}


	sourceSets {
		commonMain.dependencies {
			implementation(project(":shared"))
		}
	}
}
