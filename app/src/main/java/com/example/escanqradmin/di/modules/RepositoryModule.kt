package com.example.escanqradmin.di.modules

import com.example.escanqradmin.data.repository.BluetoothRepositoryImpl
import com.example.escanqradmin.data.repository.HistoryRepositoryImpl
import com.example.escanqradmin.data.repository.SyncRepositoryImpl
import com.example.escanqradmin.domain.repository.BluetoothRepository
import com.example.escanqradmin.domain.repository.HistoryRepository
import com.example.escanqradmin.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(
        bluetoothRepositoryImpl: BluetoothRepositoryImpl
    ): BluetoothRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
