package com.sam.kmp_battery_sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sam.shared.BatteryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
	state: BatteryState,
	modifier: Modifier = Modifier
) {
	Scaffold(
		topBar = {
			TopAppBar(title = { Text("Battery Screen") })
		},
		modifier = modifier,
	) { scPadding ->
		Column(
			modifier = Modifier
				.padding(scPadding)
				.fillMaxSize(),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			BatteryUI(state)
		}
	}
}