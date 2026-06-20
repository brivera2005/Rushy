package com.streamvault.app.update

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateReceiverEntryPoint {
    fun appUpdateInstaller(): AppUpdateInstaller
}
