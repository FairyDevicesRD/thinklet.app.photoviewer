package com.example.fd.thinkletvision

import ai.fd.thinklet.camerax.vision.Vision
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.util.getWifiIPAddress

interface IMainViewModel {
    fun setup(lifecycle: Lifecycle)
    fun teardown(lifecycle: Lifecycle)

    fun message(context: Context): String
}

class MainViewModel(
    private val cameraRepository: CameraRepository,
    private val vision: Vision
) : ViewModel(), IMainViewModel, DefaultLifecycleObserver {
    private companion object {
        const val PORT = 8080
    }

    @MainThread
    override fun setup(lifecycle: Lifecycle) = lifecycle.addObserver(this)

    @MainThread
    override fun teardown(lifecycle: Lifecycle) = lifecycle.removeObserver(this)

    override fun message(context: Context): String {
        return if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val ip = getWifiIPAddress(context)
            if (ip.isEmpty()) "Wi-Fiに接続してください．" else "http://$ip:$PORT でアクセス可能です"
        } else {
            "CameraのPermissionを許可してください"
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        cameraRepository.configure(owner)
        vision.start(PORT)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        vision.stop()
    }
}
