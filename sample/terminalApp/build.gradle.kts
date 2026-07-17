plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.koin.compiler)
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
			entryPoint = "com.sam.kmp_battery.main"
		}
	}

	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}

	sourceSets {
		commonMain.dependencies {

			// koin
			implementation(libs.koin.core)
			implementation(libs.koin.annotations)

			// terminal ui
			implementation(libs.mordant)
			implementation(libs.mordant.coroutines)
			implementation(project(":shared"))
		}
	}
}
