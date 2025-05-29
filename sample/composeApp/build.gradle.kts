plugins {
	alias(libs.plugins.androidx.application)
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.androidx.compose)
}

kotlin {
	jvmToolchain(19)
	androidTarget()

	sourceSets {

		commonMain {
			dependencies {
				implementation(compose.runtime)
				implementation(compose.foundation)
				implementation(compose.material3)
				implementation(compose.uiTooling)
				implementation(project(":shared"))
			}
		}

		androidMain {
			dependencies {
				implementation(libs.activity.compose)
			}
		}
	}
}

android {
	namespace = "com.sam.kmp_battery_sample"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.compileSdk.get().toInt()

		versionCode = 1
		versionName = "1.0.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		vectorDrawables {
			useSupportLibrary = true
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_19
		targetCompatibility = JavaVersion.VERSION_19
	}
	
	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}
