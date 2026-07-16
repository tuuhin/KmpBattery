plugins {
	alias(libs.plugins.kotlin.multiplatform)
}


val hostOs: String = System.getProperty("os.name")
val hostTarget = when {
	hostOs == "Linux" -> "linuxX64"
	hostOs == "Mac OS X" -> "macosArm64"
	hostOs.startsWith("Windows") -> "mingwX64"
	else -> error("Unsupported host OS: $hostOs")
}

kotlin {

	jvmToolchain(25)

	val target = when (hostTarget) {
		"mingwX64" -> mingwX64()
		"macosArm64" -> macosArm64()
		"linuxX64" -> linuxX64()
		else -> throw GradleException("Cannot run the script")
	}

	target.binaries {
		executable {
			entryPoint = "com.sam.kmp_battery_sample.main"
		}
	}

	sourceSets {
		commonMain.dependencies {
			implementation(project(":shared"))
		}
	}
}
