package com.example.fd.thinkletvision.util

import org.koin.core.annotation.KoinInternalApi
import org.koin.core.logger.Level
import org.koin.java.KoinJavaComponent.getKoin

/**
 * サンプルでは，Koinのロガーを拝借しています．
 */
object Logging {
    val DEFAULT_LEVEL = Level.INFO

    @OptIn(KoinInternalApi::class)
    private val logger = getKoin().logger
    fun i(value: String) = logger.log(Level.INFO, value)
    fun e(value: String) = logger.log(Level.ERROR, value)
    fun w(value: String) = logger.log(Level.WARNING, value)
}
