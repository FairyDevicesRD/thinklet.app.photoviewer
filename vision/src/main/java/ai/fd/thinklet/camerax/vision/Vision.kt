package ai.fd.thinklet.camerax.vision

import ai.fd.thinklet.camerax.vision.httpserver.VisionRepository
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * CameraXの`ImageAnalysis.Analyzer`を拡張し、`VisionController`を実装するクラス。
 *
 * カメラプレビューから取得したフレーム (`ImageProxy`) をJPEGデータに変換し、
 * 内部HTTPサーバー (`VisionRepository`経由) で公開します。
 * このクラスは、{@link VisionController} および {@link ImageAnalysis.Analyzer}
インターフェースの
 * 具体的な実装を提供します。詳細な機能説明やパラメータについては、各インターフェースの
 * ドキュメントを参照してください。
 *
 * 対応する`ImageProxy`のフォーマットは、`ImageFormat.YUV_420_888`、
 * `ImageFormat.JPEG`、または`PixelFormat.RGBA_8888` です。
 *
 * @property visionRepository HTTPサーバー機能を提供するリポジトリ。
 *                            画像の更新やサーバーの制御はこのリポジトリを通じて行われます。
 * @see VisionController
 * @see ImageAnalysis.Analyzer
 */
class Vision(
    private val visionRepository: VisionRepository
) : ImageAnalysis.Analyzer, VisionController {


    override fun start(port: Int) {
        visionRepository.start(port)
    }


    override fun stop() {
        visionRepository.stop()
    }


    override fun analyze(image: ImageProxy) {
        visionRepository.updateJpeg(image.toJpegBytes())
        image.close()
    }

    /**
     * `ImageProxy`をJPEG形式のバイト配列に変換する拡張関数。
     * `ImageProxy`からBitmapを生成し、`imageInfo.rotationDegrees`に基づいて回転させ、
     * JPEG形式に圧縮します。
     *
     * @return 変換されたJPEGデータのバイト配列。
     * @receiver 変換元の`ImageProxy`インスタンス。
     */
    private fun ImageProxy.toJpegBytes(): ByteArray {
        val bmp = this.toBitmap()
        val m = Matrix()
        m.setRotate(this.imageInfo.rotationDegrees.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val bos = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        val jpegData = bos.toByteArray()
        return jpegData
    }
}
