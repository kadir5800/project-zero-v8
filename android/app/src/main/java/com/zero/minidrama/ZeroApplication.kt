package com.zero.minidrama

import android.app.Application
import android.util.Log
import com.zero.minidrama.core.DramaWaveRelay

class ZeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            DramaWaveRelay.start(this)
            Log.i(TAG, "DramaWave relay baslatildi")
        } catch (e: Exception) {
            Log.e(TAG, "DramaWave relay baslatilamadi", e)
        }
    }

    companion object {
        private const val TAG = "ZeroApplication"
    }
}
