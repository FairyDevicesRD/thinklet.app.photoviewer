package com.example.fd.thinkletvision.httpserver

interface VisionRepository {
    fun start(port: Int = 8080)
    fun stop()
    fun updateJpeg(bytes: ByteArray)
}
