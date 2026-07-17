package com.sam.kmp_battery.di

import com.sam.shared.BatteryManager
import com.sam.shared.BatteryManagerFactory
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.scope.Scope

@Module
actual class AppModule {
	@Factory
	actual fun providesPlatformComponent(scope: Scope): BatteryManager {
		return BatteryManagerFactory().createProvider()
	}
}