plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.compose.compiler)
	alias(libs.plugins.koin.compiler)
}

android {
	namespace = "com.sam.kmp_battery"
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
	buildFeatures {
		compose = true
	}
}

dependencies {
	// android
	implementation(libs.androidx.core.ktx)
	implementation(libs.activity.compose)
	implementation(libs.androidx.splash)
	// koin
	implementation(libs.koin.core)
	implementation(libs.koin.android)
	// shared compose ui
	implementation(project(":sample:composeApp"))
}