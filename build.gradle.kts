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
    mingwX64()
}