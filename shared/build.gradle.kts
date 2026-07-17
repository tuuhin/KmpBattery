plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kotlin.multiplatform)
	alias(libs.plugins.nucleus.nna)
}

val hostOs: String = System.getProperty("os.name")
val hostTarget = when {
	hostOs == "Linux" -> "linuxX64"
	hostOs == "Mac OS X" -> "macosArm64"
	hostOs.startsWith("Windows") -> "mingwX64"
	else -> error("Unsupported host OS: $hostOs")
}

val cLibName = "kmpBattery"

kotlin {

	jvmToolchain(25)
	applyDefaultHierarchyTemplate()

	// android
	android {
		namespace = "com.sam.shared"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	// ios
	iosArm64()
	iosSimulatorArm64()

	// native desktop
	when (hostTarget) {
		"mingwX64" -> mingwX64()
		"macosArm64" -> macosArm64()
		"linuxX64" -> linuxX64()
		else -> throw GradleException("Desktop target not valid")
	}

	// jvm
	jvm()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.coroutines.core)
				api(libs.kotlin.logging)
			}
		}

		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.turbine)
			}
		}

		androidMain {
			dependencies {
				implementation(libs.androidx.core.ktx)
			}
		}
	}

	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
		optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
	}
}

kotlinNativeExport {
	nativeLibName = "kmpBattery"
	nativePackage = "com.sam.bluepad.platform.native"
	buildType = "debug"
}