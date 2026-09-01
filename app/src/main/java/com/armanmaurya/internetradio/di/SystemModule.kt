package com.armanmaurya.internetradio.di

import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.system.FileSystemFacadeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {

    @Binds
    @Singleton
    abstract fun bindFileSystemFacade(
        fileSystemFacadeImpl: FileSystemFacadeImpl
    ): FileSystemFacade

    @Binds
    @Singleton
    abstract fun bindSystemFacade(
        systemFacadeImpl: com.armanmaurya.internetradio.core.system.SystemFacadeImpl
    ): com.armanmaurya.internetradio.core.system.SystemFacade
}
