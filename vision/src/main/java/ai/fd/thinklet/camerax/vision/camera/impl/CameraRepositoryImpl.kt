package ai.fd.thinklet.app.photoviewer.camera.impl

import ai.fd.thinklet.camerax.vision.camera.CameraRepository
import ai.fd.thinklet.camerax.vision.util.Logging
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.guava.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX APIを利用してカメラ操作を行う {@link CameraRepository} の実装クラスです。
 * 画像キャプチャ、画像解析のセットアップ、およびカメラリソースの管理機能を提供します。
 * 詳細な機能説明やパラメータについては、{@link CameraRepository} インターフェースの
 * ドキュメントを参照してください。
 *
 * @property context アプリケーションコンテキスト。CameraXの初期化やファイルシステムへのアクセスに使用します。
 * @property vision オプションの画像解析アナライザー。指定された場合、カメラプレビューからフレームを取得し解析処理を行います。
 * @see CameraRepository
 */
class CameraRepositoryImpl(
    private val context: Context,
    private val vision: ImageAnalysis.Analyzer? = null
) : CameraRepository {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null
    private var isConfigured = false
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var defaultTargetRotation: Int = Surface.ROTATION_0

    companion object {
        private const val TAG = "CameraRepositoryImpl"
    }

    init {
        CameraXPatch.apply()
    }


    override fun setDefaultTargetRotation(rotation: Int) {
        val validRotations = listOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270
        )
        if (rotation in validRotations) {
            this.defaultTargetRotation = rotation
            Log.d(TAG, "Default target rotation set to: $rotation")
        } else {
            Log.w(
                TAG,
                "Invalid rotation value provided to setDefaultTargetRotation: $rotation. Using current value: ${this.defaultTargetRotation}"
            )
        }
    }


    override fun getDefaultTargetRotation(): Int {
        return this.defaultTargetRotation
    }


    override suspend fun configure(lifecycleOwner: LifecycleOwner): Boolean {
        if (isConfigured) {
            Log.d(TAG, "Camera already configured.")
            return true
        }
        this.currentLifecycleOwner = lifecycleOwner

        try {
            cameraProvider = ProcessCameraProvider.getInstance(context).await()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(this.defaultTargetRotation)
                .build()

            if (vision != null) {
                imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .build()
                    )
                    .setTargetRotation(this.defaultTargetRotation)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, vision)
                    }
            } else {
                imageAnalysis = null
            }
            return bindUseCases(lifecycleOwner, cameraSelector)

        } catch (e: Exception) {
            Log.e(TAG, "Camera configuration failed", e)
            releaseInternal()
            isConfigured = false
            return false
        }
    }

    /**
     * 設定されたカメラユースケース（ImageCapture, ImageAnalysis）を
     * 指定されたLifecycleOwnerとCameraSelectorにバインドする。
     * バインドする前に、既存の全てのユースケースのバインドを解除する。
     *
     * @param lifecycleOwner ユースケースをバインドするLifecycleOwner。
     * @param cameraSelector 使用するカメラ（前面、背面など）を選択するためのセレクタ。
     * @return バインドに成功した場合は`true`、失敗した場合は`false`。
     *         cameraProviderがnullの場合やバインドするユースケースがない場合も`false`を返す。
     */
    private fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector
    ): Boolean {
        val provider = cameraProvider ?: return false
        kotlin.runCatching { provider.unbindAll() }
            .onFailure { Logging.w("Failed to unbindAll before rebind") }

        val useCasesToBind = mutableListOf<androidx.camera.core.UseCase>()
        imageCapture?.let { useCasesToBind.add(it) }
        imageAnalysis?.let { useCasesToBind.add(it) }

        if (useCasesToBind.isEmpty()) {
            Log.w(TAG, "No use cases to bind.")
            isConfigured = false
            return false
        }

        provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            *useCasesToBind.toTypedArray()
        )
        isConfigured = true
        Log.d(
            TAG,
            "Camera configured successfully with default rotation ${this.defaultTargetRotation} and use cases: ${useCasesToBind.joinToString { it::class.java.simpleName }}"
        )
        return true
    }



    override fun isCameraInitialized(): Boolean {
        return isConfigured && cameraProvider != null && (imageCapture != null || imageAnalysis != null)
    }


    override fun captureStillImage() {
        val currentImageCapture = this.imageCapture
        if (!isCameraInitialized() || currentImageCapture == null) {
            Log.w(
                TAG,
                "Camera not initialized or ImageCapture not available. Cannot capture image."
            )
            return
        }

        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.JAPAN)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        Log.i(TAG, "Attempting to take picture. Output file: ${photoFile.absolutePath}")
        currentImageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Log.d(TAG, msg)
                }
            }
        )
    }


    override fun releaseCamera() {
        releaseInternal()
        isConfigured = false
        Log.d(TAG, "Camera resources released by public call.")
    }

    /**
     * 内部的なカメラリソースの解放処理を行う。
     * CameraProviderから全てのユースケースのバインドを解除し、
     * ImageCapture, ImageAnalysis, CameraProviderのインスタンスをnull化する。
     * また、`cameraExecutor`をシャットダウンする。
     */
    private fun releaseInternal() {
        kotlin.runCatching {
            cameraProvider?.unbindAll()
        }.onFailure {
            Logging.w("Failed to unbindAll on release")
        }
        imageCapture = null
        imageAnalysis = null
        cameraProvider = null

        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        Log.d(TAG, "Internal camera resources released.")
    }


    override fun getPhotoOutputDirectory(): File {
        return getOutputDirectory()
    }

    /**
     * アプリケーションがメディアファイルを保存するための推奨ディレクトリを取得する。
     * 外部メディアディレクトリ（通常はSDカードなど）が存在し、利用可能であればそれを返す。
     * そうでなければ、アプリケーションの内部ファイルディレクトリを返す。
     *
     * @return 出力先のディレクトリを示すFileオブジェクト。
     */
    private fun getOutputDirectory(): File {
        val externalStorageVolumes: Array<File> = ContextCompat.getExternalFilesDirs(context, null)
        val primaryExternalStorage = externalStorageVolumes.firstOrNull()
        return if (primaryExternalStorage != null && primaryExternalStorage.exists()) {
            primaryExternalStorage
        } else {
            context.filesDir.also {
                Log.w(TAG, "Primary external storage not available, using internal files directory: ${it.absolutePath}")
            }
        }
    }

    /**
     * 特定のデバイスモデル("THINKLET")に対してCameraXの初期設定にパッチを適用するためのオブジェクト。
     * このパッチは一度だけ適用される。
     */
    private object CameraXPatch {
        /** パッチが既に適用されたかどうかを示すフラグ。 */
        private var patched: Boolean = false

        /**
         * デバイスモデルが "THINKLET" を含む場合、かつパッチが未適用の場合に、
         * CameraXのグローバル設定 (`ProcessCameraProvider`) をカスタマイズする。
         * 具体的には、利用可能なカメラを背面カメラのみに制限し、最小ログレベルを警告に設定する。
         */
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
