package com.sam.kmp_battery.di

import com.sam.shared.BatteryManager
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.scope.Scope

@Module
expect class PlatformModule {

	@Factory
	fun providesPlatformComponent(scope: Scope): BatteryManager
}

