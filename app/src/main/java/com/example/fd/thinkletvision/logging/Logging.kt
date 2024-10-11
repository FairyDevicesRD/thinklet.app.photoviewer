package com.example.fd.thinkletvision.logging

import android.util.Log

object Logging {
    const val TAG = "ThinkletVision"
    fun v(value: String) = Log.v(TAG, value)
    fun d(value: String) = Log.d(TAG, value)
    fun e(value: String) = Log.e(TAG, value)
    fun w(value: String) = Log.w(TAG, value)
    fun i(value: String) = Log.i(TAG, value)
}
