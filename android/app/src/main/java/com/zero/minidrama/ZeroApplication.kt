package com.zero.minidrama

import android.app.Application
import android.util.Log
import coil.ImageLoaderFactory
import com.zero.minidrama.core.DramaWaveRelay
import com.zero.minidrama.core.ImageLoading

class ZeroApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        try {
            DramaWaveRelay.start(this)
            Log.i(TAG, "DramaWave relay baslatildi")
        } catch (e: Exception) {
            Log.e(TAG, "DramaWave relay baslatilamadi", e)
        }
    }

    override fun newImageLoader() = ImageLoading.createLoader(this)

    companion object {
        private const val TAG = "ZeroApplication"
    }
}
