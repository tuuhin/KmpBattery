package com.sam.kmp_battery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sam.kmp_battery.theme.KmpBatteryTheme
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		setContent {
			KmpBatteryTheme(dynamicColor = true) {
				App {
					androidLogger()
					androidContext(applicationContext)
				}
			}
		}
	}
}