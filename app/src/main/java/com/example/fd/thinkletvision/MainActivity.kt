package com.example.fd.thinkletvision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.camera.impl.CameraRepositoryImpl
import com.example.fd.thinkletvision.httpserver.impl.VisionRepositoryImpl
import com.example.fd.thinkletvision.ui.theme.ThinkletVisionTheme
import com.example.fd.thinkletvision.util.getWifiIPAddress
import com.example.fd.thinkletvision.util.toJpegBytes

class MainActivity : ComponentActivity() {
    private val vision = VisionRepositoryImpl()
    private val camera: CameraRepository by lazy {
        CameraRepositoryImpl(
            this,
            { vision.updateJpeg(it.toJpegBytes()) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThinkletVisionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Screen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vision.start()
        camera.configure(this)
    }

    override fun onStop() {
        super.onStop()
        vision.stop()
    }
}

@Composable
fun Screen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        val ip = getWifiIPAddress(context)
        if (ip.isEmpty()) {
            Text(
                text = "Wi-Fiに接続してください．",
                modifier = modifier
            )
        } else {
            Text(
                text = "http://$ip:8080 でアクセス可能です",
                modifier = modifier
            )
        }
    } else {
        Text(
            text = "CameraのPermissionを許可してください",
            modifier = modifier
        )
    }
}
