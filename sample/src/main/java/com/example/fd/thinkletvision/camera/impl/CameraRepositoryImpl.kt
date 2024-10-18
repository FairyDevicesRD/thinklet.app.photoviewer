package com.example.fd.thinkletvision.camera.impl

import ai.fd.thinklet.camerax.vision.Vision
import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.util.Logging
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraRepositoryImpl(
    private val context: Context,
    vision: Vision
) : CameraRepository {
    private val camProducer = ImageProducer(analyzer = vision)

    override fun configure(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                bindToLifecycle(
                    lifecycleOwner = lifecycleOwner,
                    cameraProvider = cameraProvider,
                )
            }, ContextCompat.getMainExecutor(context)
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
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(
                        Size(720, 1280), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                ).build()
            ).build().also {
                it.setAnalyzer(executorService, analyzer)
            }
        }
    }
}
