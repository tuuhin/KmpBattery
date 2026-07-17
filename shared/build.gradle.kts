plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kotlin.multiplatform)
	alias(libs.plugins.nucleus.nna)
}

val cLibName = "kmpBattery"

kotlin {

	jvmToolchain(25)

	// android
	android {
		namespace = "com.sam.shared"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	iosArm64()
	iosSimulatorArm64()

	// jvm
	jvm()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.coroutines.core)
			api(libs.kotlin.logging)
		}

		jvmMain.dependencies {
			implementation(project(":shared-desktop"))
		}

		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.turbine)
		}
		androidMain.dependencies {
			implementation(libs.androidx.core.ktx)
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