package com.sam.shared.native

/**
 * Native battery state acts as an marker interface without amount and code
 * These fields later can be combined to form the actual battery state data
 */
open class NativeBatteryState(val amt: Float, val code: Int = -1)

class NativeBatteryStateFull : NativeBatteryState(-1f, 0)
class NativeBatteryStateCharging(val amount: Float) : NativeBatteryState(amount, 1)
class NativeBatteryStateDisCharging(val amount: Float) : NativeBatteryState(amount, 2)
class NativeBatteryStateNoBatteryFound : NativeBatteryState(-1f, 3)
class NativeBatteryStateUnknown : NativeBatteryState(-1f, -1)
