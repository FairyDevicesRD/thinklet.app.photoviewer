package ai.fd.thinklet.camerax.vision.httpserver

internal interface VisionRepository {
    fun start(port: Int)
    fun stop()
    fun updateJpeg(bytes: ByteArray)
}
