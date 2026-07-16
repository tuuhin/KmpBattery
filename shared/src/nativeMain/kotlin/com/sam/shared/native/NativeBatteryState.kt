package com.sam.shared.native

open class NativeBatteryState

class NativeBatteryStateFull : NativeBatteryState()
class NativeBatteryStateCharging(val amount: Float) : NativeBatteryState()
class NativeBatteryStateDisCharging(val amount: Float) : NativeBatteryState()
class NativeBatteryStateUnknown : NativeBatteryState()
class NativeBatteryStateNoBatteryFound : NativeBatteryState()
