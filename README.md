## KMP Battery

A Kotlin Multiplatform library example demonstrating how to read battery state across different
platforms.

### Supported Platforms

With Kmp we can target various platform, these are the platforms that this project currently
targets.

- [x] Android
- [x] Windows (mingw x64)
- [ ] Linux (x64)
- [x] IOS (Not tested)
- [x] MacOS

### Project Modules

- `androidApp`: An Android application module.
- `composeApp`: Contains shared UI logic for Compose Multiplatform.
- `desktopApp`: A Compose Desktop application module.
- `iosAppCmp`: An iOS application module.
- `terminalApp`: A native terminal application demonstrating battery state access.

### Building and Running

To build and run the samples as terminal applications, use the Gradle wrapper from the root
directory. Note that these applications are designed as terminal tools and require an interactive
terminal environment to run properly; please launch them independently in your shell.


## Conclusion

This project serves as a practice ground to explore the powerful features of Kotlin Multiplatform (
KMP). It demonstrates how to interact with platform-specific native APIs from common Kotlin code,
which is a core strength of KMP. Feel free to use this as a reference for your own multiplatform
explorations.
