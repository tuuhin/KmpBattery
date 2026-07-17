package com.sam.kmp_battery.di

import org.koin.core.annotation.KoinApplication

@KoinApplication(modules = [AppModule::class, PlatformModule::class])
class KoinApp