plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.nucleus.nna)
}

val cLibName = "kmpBattery"

kotlin {

	jvmToolchain(25)


	val os = org.gradle.internal.os.OperatingSystem.current()

	when {
		os.isWindows -> mingwX64()
		os.isMacOsX -> macosArm64()
		os.isLinux -> linuxX64()
		else -> throw GradleException("Unkown desktop target only windows, macos and linux are supported")
	}

	// jvm
	jvm()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.coroutines.core)
			api(libs.kotlin.logging)
		}

		jvmTest.dependencies {
			implementation(libs.kotlin.test)
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
