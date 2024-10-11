package com.example.fd.thinkletvision.httpserver

interface VisionRepository {
    fun start()
    fun stop()
    fun updateJpeg(bytes: ByteArray)
}
