package com.example.fd.thinkletvision

import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.camera.impl.CameraRepositoryImpl
import com.example.fd.thinkletvision.httpserver.VisionRepository
import com.example.fd.thinkletvision.httpserver.impl.VisionRepositoryImpl
import com.example.fd.thinkletvision.util.toJpegBytes
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin v4 を用いたDI．
 */
val appModule = module {
    singleOf(::VisionRepositoryImpl) { bind<VisionRepository>() }
    single<CameraRepository> {
        CameraRepositoryImpl(
            androidContext()
        ) { get<VisionRepository>().updateJpeg(it.toJpegBytes()) }
    }
    viewModelOf(::MainViewModel)
}
