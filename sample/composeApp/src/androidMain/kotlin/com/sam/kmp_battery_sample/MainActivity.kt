package com.sam.kmp_battery_sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sam.shared.BatteryManagerFactory
import com.sam.shared.BatteryState

class MainActivity : ComponentActivity() {

	val batteryState by lazy { BatteryManagerFactory(applicationContext).createProvider() }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			Surface {
				val state by batteryState.batteryStateFlow.collectAsStateWithLifecycle(BatteryState.Unknown)
				BatteryScreen(state = state)
			}
		}
	}
}