package com.sam.kmp_battery.di

import com.sam.shared_desktop.NativeBatteryManager
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.scope.Scope

@Module
expect class AppModule {

	@Factory
	fun providesPlatformComponent(scope: Scope): NativeBatteryManager
}