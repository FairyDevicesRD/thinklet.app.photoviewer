package com.example.fd.thinkletvision.camera

import androidx.lifecycle.LifecycleOwner

interface CameraRepository {
    suspend fun configure(lifecycleOwner: LifecycleOwner)
}
