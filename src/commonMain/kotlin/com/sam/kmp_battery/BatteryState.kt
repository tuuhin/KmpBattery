package com.sam.kmp_battery

sealed interface BatteryState {
    data object Full : BatteryState
    data class Charging(val amount: Float) : BatteryState
    data class DisCharging(val amount: Float) : BatteryState
    data object Unknown : BatteryState
    data object NoBatteryFound : BatteryState
}