package com.example.fd.thinkletvision.camera.impl

import ai.fd.thinklet.camerax.vision.Vision
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.util.Logging
import kotlinx.coroutines.guava.await
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraRepositoryImpl(
    private val context: Context,
    vision: Vision
) : CameraRepository {
    init {
        CameraXPatch.apply()
    }

    private val camProducer = ImageProducer(analyzer = vision)

    override suspend fun configure(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            cameraProvider = cameraProvider
        )
    }

    private fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner, cameraProvider: ProcessCameraProvider
    ) {
        // 念のために，rebindingする前に unbind
        kotlin.runCatching {
            cameraProvider.unbindAll()
        }.onFailure {
            Logging.w("Failed to unbindAll")
        }

        // bind開始
        kotlin.runCatching {
            cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, camProducer.get()
            )
        }.onFailure {
            Logging.e("Use case binding failed")
            return
        }
    }

    private class ImageProducer(
        private val executorService: ExecutorService = Executors.newSingleThreadExecutor(),
        private val analyzer: ImageAnalysis.Analyzer
    ) {
        fun get(): ImageAnalysis {
            return ImageAnalysis.Builder().setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            ).build().also {
                it.setAnalyzer(executorService, analyzer)
            }
        }
    }

    /**
     * CameraX向けのTHINKLETの高速化パッチ
     */
    private object CameraXPatch {
        private var patched: Boolean = false

        fun apply() {
            if (!patched && Build.MODEL.contains("THINKLET")) {
                ProcessCameraProvider.configureInstance(
                    CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                        .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
                        .setMinimumLoggingLevel(Log.WARN)
                        .build()
                )
                patched = true
            }
        }
    }
}
