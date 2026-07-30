import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.DmgContentType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.resources.ResourcesExtension
import java.util.Properties

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.nucleus.framework)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // compose
    implementation(libs.cmp.runtime)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.ui)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.components.resources)
    implementation(compose.desktop.currentOs)
    // koin
    implementation(libs.koin.core)
    // nucleus
    implementation(libs.nucleus.core.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.decorated.window.material3)

    // shared data
    implementation(project(":sample:composeApp"))
}

nucleus.application {
    mainClass = "com.sam.kmp_battery.MainKt"

    jvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "-Dsun.misc.unsafe.allow=true",
    )

    nativeDistributions {

        val commonProperties = Properties().apply {
            val commons = project.file("packaging.properties")
            commons.inputStream().use(::load)
        }

        // application targets
        targetFormats(
            // windows
            TargetFormat.Msi, TargetFormat.Portable,
            // macos
            TargetFormat.Deb,
            // linux
            TargetFormat.Rpm,
            TargetFormat.AppImage,
            TargetFormat.Pacman,
            TargetFormat.Snap,
            TargetFormat.Flatpak,
        )

        // target base config
        appName = commonProperties.getProperty("APP_NAME")
        packageName = commonProperties.getProperty("APP_PACKAGE_NAME")
        packageVersion = commonProperties.getProperty("APP_PACKAGE_VERSION")
        description = commonProperties.getProperty("APP_DESCRIPTION")
        vendor = commonProperties.getProperty("APP_VENDOR")
        copyright = commonProperties.getProperty("APP_COPYRIGHT")
        licenseFile.set(rootProject.file("LICENCE"))

        // java modules
        modules("java.instrument", "jdk.unsupported", "java.management")

        // target common connfiguration
        outputBaseDir.set(project.layout.buildDirectory.dir("desktop"))
        appResourcesRootDir.set(project.layout.projectDirectory.dir("desktopResources"))
        compressionLevel = CompressionLevel.Normal
        cleanupNativeLibs = true

        val packagingRoot = project.layout.projectDirectory.dir("packaging")
        windows {
            iconFile.set(packagingRoot.file("windows/kmp_battery.ico"))
            upgradeUuid =
                commonProperties.getProperty("WINDOWS_UPGRADE_UUID", null)?.ifEmpty { null }
            console = false
            perUserInstall = true
            dirChooser = true

            appx {

                applicationId = commonProperties.getProperty("APP_PACKAGE_NAME")
                publisherDisplayName = commonProperties.getProperty("APP_VENDOR")
                displayName = commonProperties.getProperty("APP_NAME")
                publisher = commonProperties.getProperty("WINDOWS_APPX_PUBLISHER")
                identityName = commonProperties.getProperty("APP_PACKAGE_NAME")

                languages = listOf("en-US")
                backgroundColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND")
                showNameOnTiles = true
                addAutoLaunchExtension = false
                setBuildNumber = true

                storeLogo.set(packagingRoot.file("windows/appx/StoreLogo.png"))
                square44x44Logo.set(packagingRoot.file("windows/appx/Square44x44Logo.png"))
                square150x150Logo.set(packagingRoot.file("windows/appx/Square150x150Logo.png"))
                wide310x150Logo.set(packagingRoot.file("windows/appx/Wide310x150Logo.png"))
            }
        }

        macOS {
            bundleID = commonProperties.getProperty("APP_PACKAGE_NAME")
            dockName = commonProperties.getProperty("APP_NAME")

            minimumSystemVersion = "12.0"
            macOsSdkVersion = "26.0"

            appCategory = "public.app-category.utilities"

            layeredIconDir.set(packagingRoot.dir("macos/kmp_battery.icon"))
            iconFile.set(packagingRoot.file("macos/kmp_battery.icns"))

            entitlementsFile.set(packagingRoot.file("macos/entitlements.plist"))
            runtimeEntitlementsFile.set(packagingRoot.file("macos/runtime-entitlements.plist"))

            dmg {
                title = $$"${productName} ${version}"
                iconSize = 128
                window { x = 400; y = 100; width = 540; height = 380 }
                backgroundColor =
                    commonProperties.getProperty("APP_INSTALLER_BACKGROUND", "#C4F18C")
                content(x = 130, y = 220, type = DmgContentType.File, name = "MyApp.app")
                content(x = 410, y = 220, type = DmgContentType.Link, path = "/Applications")
            }
        }

        linux {
            iconFile.set(packagingRoot.file("linux/app.png"))
            packageName = commonProperties.getProperty("APP_PACKAGE_NAME")
            shortcut = true
            appCategory = "Utility"
            menuGroup = "Development"
            debMaintainer = commonProperties.getProperty("APP_VENDOR")
            rpmLicenseType = "MIT"

            // --- Debian / Ubuntu (.deb) ---
            debDepends = listOf(
                "libfuse2", "libgtk-3-0", "libasound2", "libglib2.0-0", "libblkid1", "libmount1",
            )
            // --- RHEL / Fedora / CentOS (.rpm) ---
            rpmRequires = listOf("gtk3", "libX11", "alsa-lib", "glib2", "libblkid", "libmount")
            // --- Arch Linux (.pacman) ---
            pacmanDepends = listOf("gtk3", "libx11", "alsa-lib", "glib2", "util-linux-libs")
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.sam.kmp_battery.resources"
    generateResClass = ResourcesExtension.ResourceClassGeneration.Auto

    customDirectory(
        sourceSetName = "main",
        directoryProvider = provider {
            layout.projectDirectory.dir("src/composeResources")
        },
    )
}

val generateMacosAppIcns = tasks.register<Exec>("genAppIconMacos") {
    group = "nucleus packaging"
    description = "Generates clipped, rounded macos icons"

    val icon = project.layout.projectDirectory.file("packaging/kmp_battery.svg").asFile
    val commonProperties = Properties().apply {
        val commons = project.file("packaging.properties")
        commons.inputStream().use(::load)
    }

    val script = project.layout.projectDirectory.file("packaging/macos/app_images.sh").asFile
    val bgColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND", "#C4F18C")

    workingDir(project.layout.projectDirectory.file("packaging"))
    commandLine(
        "bash", script.absolutePath, icon.absolutePath,
        "-c", bgColor, "-r", "22", "-f", "85",
    )

    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX
    }
}

val generateWindowsAppIcon = tasks.register<Exec>("genAppIconWindos") {
    group = "nucleus packaging"
    description = "Generates clipped, rounded Windows assets from the SVG source icon."

    val icon = project.layout.projectDirectory.file("packaging/kmp_battery.svg").asFile
    val commonProperties = Properties().apply {
        val commons = project.file("packaging.properties")
        commons.inputStream().use(::load)
    }

    val script = project.layout.projectDirectory.file("packaging/windows/app_images.bat").asFile
    val bgColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND", "#C4F18C")
    val outputDir = project.layout.projectDirectory.dir("packaging/windows/appx").asFile

    workingDir(project.layout.projectDirectory.file("packaging"))
    commandLine(
        "cmd", "/c", script.absolutePath, icon.absolutePath, outputDir.absolutePath,
        "-c", bgColor, "-r", "12",
    )
    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isWindows
    }
}

val generateLinuxIconFile = tasks.register<Exec>("genAppIconLinux") {
    group = "nucleus packaging"
    description = "Generates clipped, rounded Windows assets from the SVG source icon."

    val icon = project.layout.projectDirectory.file("packaging/kmp_battery.svg").asFile
    val commonProperties = Properties().apply {
        val commons = project.file("packaging.properties")
        commons.inputStream().use(::load)
    }

    val script = project.layout.projectDirectory.file("packaging/linux/app_images.sh").asFile
    val bgColor = commonProperties.getProperty("APP_INSTALLER_BACKGROUND", "#C4F18C")
    val outputDir = project.layout.projectDirectory.dir("packaging/windows/appx").asFile

    workingDir(project.layout.projectDirectory.file("packaging"))
    commandLine(
        "bash", script.absolutePath, icon.absolutePath,
        "-c", bgColor, "-r", "22", "-f", "85",
    )
    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isLinux
    }
}

tasks.matching { task ->
    val taskName = task.name
    taskName.startsWith("package") ||
        taskName.startsWith("createDist") ||
        taskName.startsWith("compile")
}.configureEach {
    val os = org.gradle.internal.os.OperatingSystem.current()
    when {
        os.isWindows -> dependsOn(generateWindowsAppIcon)
        os.isLinux -> dependsOn(generateLinuxIconFile)
        os.isMacOsX -> dependsOn(generateMacosAppIcns)
        else -> {}
    }
}
