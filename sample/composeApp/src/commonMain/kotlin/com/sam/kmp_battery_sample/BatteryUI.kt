package com.sam.kmp_battery_sample


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sam.shared.BatteryState

@Composable
fun BatteryUI(
	state: BatteryState,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier.padding(16.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Canvas(
			modifier = Modifier.size(width = 50.dp, height = 100.dp)
		) {

			drawRoundRect(
				color = Color.Black,
				cornerRadius = CornerRadius(10.dp.toPx()),
				style = Stroke(width = 2.dp.toPx())
			)

			clipPath(
				path = Path().apply {
					addRoundRect(
						RoundRect(
							rect = Rect(
								Offset(5.dp.toPx(), 5.dp.toPx()),
								Offset(size.width - 5.dp.toPx(), size.height - 5.dp.toPx())
							),
							CornerRadius(5.dp.toPx())
						)
					)
				},
			) {
				when (state) {
					is BatteryState.Charging -> {
						val batteryBarHeight = (state.amount / 100) * size.height

						drawRoundRect(
							color = Color.Green,
							topLeft = Offset(0f, size.height - batteryBarHeight),
							cornerRadius = CornerRadius(10.dp.toPx()),
						)
					}

					is BatteryState.DisCharging -> {
						val batteryBarHeight = (state.amount / 100) * size.height

						drawRoundRect(
							color = Color.Yellow,
							topLeft = Offset(0f, size.height - batteryBarHeight),
							cornerRadius = CornerRadius(10.dp.toPx()),
						)
					}

					BatteryState.Full -> {
						drawRoundRect(
							color = Color.Green,
							cornerRadius = CornerRadius(10.dp.toPx()),
						)
					}

					BatteryState.NoBatteryFound, BatteryState.Unknown -> {

					}
				}
			}
		}

		Spacer(modifier = Modifier.height(8.dp))
		Text(
			text = when (state) {
				is BatteryState.Full -> "Battery Full"
				is BatteryState.Charging -> "Charging: ${(state.amount).toInt()}%"
				is BatteryState.DisCharging -> "Discharging: ${(state.amount).toInt()}%"
				is BatteryState.Unknown -> "Unknown State"
				is BatteryState.NoBatteryFound -> "No Battery Found"
			},
			style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
		)
	}
}
