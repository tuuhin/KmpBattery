package com.sam.kmp_battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sam.shared.BatteryManager
import com.sam.shared.BatteryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class BatteryScreenViewmodel(btManager: BatteryManager) : ViewModel() {

	val batteryState = btManager.batteryStateFlow.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(2_000L),
		initialValue = BatteryState.Unknown
	)
}