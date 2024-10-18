package com.example.fd.thinkletvision.camera

import androidx.lifecycle.LifecycleOwner

interface CameraRepository {
    fun configure(lifecycleOwner: LifecycleOwner)
}
