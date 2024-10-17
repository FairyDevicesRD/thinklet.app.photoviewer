package com.example.fd.thinkletvision.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toJpegBytes(): ByteArray {
    val bmp = this.toBitmap()
    val m = Matrix()
    m.setRotate(this.imageInfo.rotationDegrees.toFloat())
    val bitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val jpegData = baos.toByteArray()
    return jpegData
}