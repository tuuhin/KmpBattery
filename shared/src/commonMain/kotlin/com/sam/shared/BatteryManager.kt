package com.sam.shared

import kotlinx.coroutines.flow.Flow

interface BatteryManager {

    suspend fun batteryLevel(): Int

    suspend fun isBatteryInPowerSavingMode(): Boolean

    suspend fun batteryState(): BatteryState

    val batteryStateFlow: Flow<BatteryState>
}