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
import androidx.lifecycle.viewModelScope
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.util.getWifiIPAddress
import kotlinx.coroutines.launch

interface IMainViewModel {
    /**
     * ライフサイクルのイベントを受信開始する関数
     * @param lifecycle
     */
    fun setup(lifecycle: Lifecycle)

    /**
     * ライフサイクルのイベントを受信停止する関数
     * @param lifecycle
     */
    fun teardown(lifecycle: Lifecycle)

    /**
     * UI側の表示内容を返す
     * @param context
     */
    fun message(context: Context): String
}

class MainViewModel(
    private val cameraRepository: CameraRepository,
    private val vision: Vision
) : ViewModel(), IMainViewModel, DefaultLifecycleObserver {
    @MainThread
    override fun setup(lifecycle: Lifecycle) = lifecycle.addObserver(this)

    @MainThread
    override fun teardown(lifecycle: Lifecycle) = lifecycle.removeObserver(this)

    override fun message(context: Context): String {
        return if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val ip = getWifiIPAddress(context)
            if (ip.isEmpty()) {
                "Wi-Fiに接続してください．"
            } else {
                "http://$ip:$PORT でアクセス可能です"
            }
        } else {
            "CameraのPermissionを許可してください"
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch {
            cameraRepository.configure(owner)
            vision.start(PORT)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        vision.stop()
    }

    private companion object {
        // HTTPサーバーに使用するポート番号
        const val PORT = 8080
    }
}
