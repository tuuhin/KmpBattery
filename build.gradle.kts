plugins {
    kotlin("multiplatform") version "2.1.20"
}

group = "com.sam.kmp_battery"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {

    jvmToolchain(19)
    jvm()


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.github.oshai:kotlin-logging:7.0.7")
            }
        }

        val mingwX64Main by creating {
            dependencies {
                implementation("io.github.oshai:kotlin-logging-mingwx64:7.0.7")
            }
        }
    }


    mingwX64 {
        binaries {
            executable {
                this.debuggable = true
                entryPoint("main")
            }
        }
    }
}