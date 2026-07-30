
package com.sam.shared_desktop

import kotlinx.cinterop.*
import platform.posix.*

internal object FileReadingUtil {

    const val POWER_INFO_DIR_LOCATION = "/sys/class/power_supply"

    fun readFile(
        filePath: String,
        bufferSize: Int = 128
    ): String? {
        val file = fopen(filePath, "r") ?: return null

        return try {
            memScoped {
                val buffer = allocArray<ByteVar>(bufferSize)

                buildString {
                    while (fgets(buffer, bufferSize, file) != null) {
                        append(buffer.toKString())
                    }
                }.trim()
            }
        } finally {
            fclose(file)
        }
    }

    fun findPowerSupplyDevice(type: LinuxPowerClass): String? {

        val dir = opendir(POWER_INFO_DIR_LOCATION) ?: return null

        return try {
            while (true) {
                val entry = readdir(dir) ?: break

                val dirName = entry.pointed.d_name.toKString()

                if (dirName == "." || dirName == "..")
                    continue

                val deviceType = readFile(
                    "$POWER_INFO_DIR_LOCATION/$dirName/type"
                ) ?: continue

                if (deviceType == type.type) {
                    return dirName
                }
            }

            null
        } finally {
            closedir(dir)
        }
    }
}
