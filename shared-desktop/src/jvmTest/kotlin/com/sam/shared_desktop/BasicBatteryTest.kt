package com.sam.shared_desktop

import com.sam.bluepad.platform.native.NativePlatformBatteryManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BasicBatteryTest {

	@Test
	fun `battery level should not be negative 1`()= runTest {
		val level = NativePlatformBatteryManager()
			.use { it.batteryLevel() }
		assertTrue(level>0,"Battery level is greater than zero")
	}

}