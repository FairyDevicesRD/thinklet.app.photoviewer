package ai.fd.thinklet.camerax.vision

import ai.fd.thinklet.camerax.vision.httpserver.impl.VisionRepositoryImpl
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * TODO: CameraXの `ImageAnalysis.Analyzer` を用いて，Analyzeに与えたImageをHTTPサーバに公開する．
 * The supported ImageProxy format is ImageFormat. YUV_420_888, ImageFormat. JPEG or PixelFormat. RGBA_8888.
 */
class Vision : ImageAnalysis.Analyzer {
    private val visionRepository = VisionRepositoryImpl()

    fun start(port: Int = 8080) {
        visionRepository.start(port)
    }

    fun stop() {
        visionRepository.stop()
    }

    override fun analyze(image: ImageProxy) {
        visionRepository.updateJpeg(image.toJpegBytes())
        image.close()
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val bmp = this.toBitmap()
        val m = Matrix()
        m.setRotate(this.imageInfo.rotationDegrees.toFloat())
        val bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        val jpegData = bos.toByteArray()
        return jpegData
    }
}
