package com.example.fd.thinkletvision

import ai.fd.thinklet.camerax.vision.Vision
import com.example.fd.thinkletvision.camera.CameraRepository
import com.example.fd.thinkletvision.camera.impl.CameraRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin v4 を用いたDI．
 */
val appModule = module {
    singleOf(::Vision) { bind<Vision>() }
    singleOf(::CameraRepositoryImpl) { bind<CameraRepository>() }
    viewModelOf(::MainViewModel)
}
