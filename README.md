## 🔋 KMP Battery

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" />
  <img src="https://img.shields.io/badge/Platform-iOS-lightgrey?logo=apple" />
  <img src="https://img.shields.io/badge/Platform-Windows-blue?logo=windows" />
  <img src="https://img.shields.io/badge/Platform-macOS-black?logo=apple" />
</p>

A Kotlin Multiplatform library example demonstrating how to read battery state across different
platforms.

### 📱 Supported Platforms

With KMP we can target various platforms. The table below lists the platforms, their status, and
relevant screenshots:

| Platform | Screenshot                                                                              | Tested? |
|:---------|:----------------------------------------------------------------------------------------|:--------|
| Android  | <img src="screenshots/kmp_battery_android.jpeg" width="400" alt="Android Screenshot" /> | Yes     |
| Windows  | <img src="screenshots/kmp_battery_windows.png" width="400" alt="Windows Screenshot" />  | Yes     |
| MacOS    | <img src="screenshots/kmp_battery_macos.jpeg" width="400" alt="MacOS Screenshot" />     | Yes     |
| iOS      | <img src="screenshots/kmp_battery_ios.jpeg" width="400" alt="iOS Screenshot" />         | No      |
| Linux    | N/A                                                                                     | No      |

## 🚀 Features

* **Native Battery Access:** Demonstrates how to bridge platform-specific native APIs to Kotlin.
* **Compose Multiplatform UI:** Shared UI implementation across Android, Desktop, and iOS.
* **Lightweight:** Minimal dependencies, focused on educational content.

## 🛠️ Prerequisites

* **JDK:** Version 17 or higher.
* **Gradle:** The project uses the Gradle Wrapper; no manual Gradle installation is required.
* **OS Requirements:**
    * **macOS:** Required for building iOS targets (with Xcode installed).
    * **Windows/Linux:** Standard environment for Desktop and Android builds.

## 📦 Project Modules

- `androidApp`: An Android application module.
- `composeApp`: Contains shared UI logic for Compose Multiplatform.
- `desktopApp`: A Compose Desktop application module.
- `iosAppCmp`: An iOS application module.
- `terminalApp`: A native terminal application demonstrating battery state access.

## 🤝 Contributing

Contributions are welcome! If you find a bug or have an improvement, please see
the [CONTRIBUTING.md](CONTRIBUTING.md) file for guidelines.

## 📜 License

This project is licensed under the MIT License. Please see the [LICENSE](LICENSE) file for details.

## 🎯 Conclusion

This project serves as a practice ground to explore the powerful features of Kotlin Multiplatform (
KMP). It demonstrates how to interact with platform-specific native APIs from common Kotlin code,
which is a core strength of KMP. Feel free to use this as a reference for your own multiplatform
explorations.