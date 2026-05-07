package com.example.escanqradmin.app.android

import android.app.Application
import com.example.escanqradmin.data.network.ApiConstants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EscanQRApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiConstants.init(this)
    }
}