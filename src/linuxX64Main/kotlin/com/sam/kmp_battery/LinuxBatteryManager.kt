package com.sam.kmp_battery

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import platform.posix.*

private const val POWER_INFO_DIR_LOCATION = "/sys/class/power_supply"

private val logger = KotlinLogging.logger("LinuxBatteryManager")

@OptIn(ExperimentalForeignApi::class)
private suspend fun readFile(filePath: String, bufferSize: Int = 128): String? {
    return withContext(Dispatchers.IO) {
        val filePointer = fopen(filePath, "r") ?: return@withContext null
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(bufferSize)
                buildString {
                    while (fgets(buffer, bufferSize, filePointer) != null) {
                        append(buffer.toKString())
                    }
                }.trim()
            }
        } finally {
            fclose(filePointer)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun findPowerSupplyDevice(type: LinuxPowerClass): String? {
    var powerName: String? = null
    withContext(Dispatchers.IO) {
        val directory = opendir(POWER_INFO_DIR_LOCATION) ?: return@withContext null
        try {
            while (true) {
                val readDir = readdir(directory) ?: break
                val dirName = readDir.pointed.d_name.toKString()
                // skip these values
                if (dirName == "." || dirName == "..") continue

                val filePath = "$POWER_INFO_DIR_LOCATION/$dirName/type"
                val fileContent = readFile(filePath) ?: continue
                if (fileContent == type.type) {
                    powerName = type.folderName
                    break
                }
            }
        } finally {
            closedir(directory)
        }
    }
    return powerName
}

class LinuxBatteryManager : BatteryManager {

    override suspend fun batteryLevel(): Int {
        val powerSupplyType = findPowerSupplyDevice(LinuxPowerClass.BATTERY) ?: return 0
        val levelFileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/capacity"
        val levelAsString = readFile(levelFileName, 3) ?: return 0
        return levelAsString.trim().toIntOrNull() ?: 0
    }

    override suspend fun isBatteryInPowerSavingMode(): Boolean {
        logger.info { "NO DIRECT API IS READY" }
        return false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun batteryState(): BatteryState = coroutineScope {
        val powerSupplyType = findPowerSupplyDevice(LinuxPowerClass.BATTERY)
            ?: return@coroutineScope BatteryState.NoBatteryFound

        val batteryLevelDeferred = async(Dispatchers.IO) {
            val fileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/capacity"
            val levelAsString = readFile(fileName, 3) ?: return@async 0
            levelAsString.trim().toIntOrNull() ?: 0
        }
        val batteryStatusDeferred = async(Dispatchers.IO) {
            val fileName = "$POWER_INFO_DIR_LOCATION/$powerSupplyType/status"
            val statusAsString = readFile(fileName, 3)?.trim() ?: return@async PowerStatus.UNKNOWN
            when (statusAsString) {
                "Discharging" -> PowerStatus.DISCHARGING
                "Charging" -> PowerStatus.CHARING
                else -> PowerStatus.UNKNOWN
            }
        }
        awaitAll(batteryLevelDeferred, batteryStatusDeferred)

        val batteryLevel = batteryLevelDeferred.getCompleted()
        val batteryStatus = batteryStatusDeferred.getCompleted()

        if (batteryLevel == 100) return@coroutineScope BatteryState.Full
        when (batteryStatus) {
            PowerStatus.CHARING -> BatteryState.Charging(batteryLevel.toFloat())
            PowerStatus.DISCHARGING -> BatteryState.DisCharging(batteryLevel.toFloat())
            PowerStatus.UNKNOWN -> BatteryState.Unknown
        }
    }

    override val batteryStateFlow: Flow<BatteryState>
        // TODO: Implement this properly
        get() = emptyFlow()
}