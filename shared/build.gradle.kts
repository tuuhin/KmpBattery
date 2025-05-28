plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
	androidLibrary {
		namespace = "com.sam.shared"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()
	}
	mingwX64()
	linuxX64()

	sourceSets {
		commonMain {
			dependencies {
				implementation(libs.kotlinx.coroutines)
				api(libs.kotlin.logging)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
			}
		}

		androidMain {
			dependencies {
				implementation(libs.androidx.core.ktx)
			}
		}
	}

	compilerOptions {
		freeCompilerArgs.set(freeCompilerArgs.get() + "-Xexpect-actual-classes")
	}
}