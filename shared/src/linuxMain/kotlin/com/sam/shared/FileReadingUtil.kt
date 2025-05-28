package com.sam.shared

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.opendir
import platform.posix.readdir

const val POWER_INFO_DIR_LOCATION = "/sys/class/power_supply"

@OptIn(ExperimentalForeignApi::class)
suspend fun readFile(filePath: String, bufferSize: Int = 128): String? {
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
suspend fun findPowerSupplyDevice(type: LinuxPowerClass): String? {
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
