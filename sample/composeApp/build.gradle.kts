plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kotlin.multiplatform)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.koin.compiler)
}

kotlin {
	jvmToolchain(25)
	android {
		namespace = "com.sam.kmp_battery_common_ui"
		minSdk = libs.versions.android.minSdk.get().toInt()
		compileSdk = libs.versions.android.compileSdk.get().toInt()

		withHostTest {
			isIncludeAndroidResources = true
		}

		withDeviceTestBuilder {
			sourceSetTreeName = "test"
		}.configure {
			instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
			execution = "HOST"
		}

		androidResources {
			enable = true
		}
	}

	jvm()

	sourceSets {
		commonMain {
			dependencies {
				// compose
				implementation(libs.cmp.runtime)
				implementation(libs.cmp.foundation)
				implementation(libs.cmp.ui)
				implementation(libs.cmp.material3)
				implementation(libs.cmp.components.resources)
				implementation(libs.cmp.ui.tooling.preview)

				// lifecyle and viewmodel
				implementation(libs.androidx.lifecycle.viewmodelCompose)
				implementation(libs.androidx.lifecycle.runtimeCompose)

				//koin
				implementation(libs.koin.core)
				implementation(libs.koin.compose)
				implementation(libs.koin.viewmodel)
				implementation(libs.koin.annotations)
				implementation(libs.koin.compose.viewmodel)

				// color
				implementation(libs.materialKolor)
				implementation(libs.nucleus.system.accent)

				implementation(project(":shared"))
			}
		}
	}

	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
		optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
	}
}

koinCompiler {
	userLogs = true
}

composeCompiler {
	metricsDestination = layout.buildDirectory.dir("compose_compiler")
	reportsDestination = layout.buildDirectory.dir("compose_compiler")
	stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

