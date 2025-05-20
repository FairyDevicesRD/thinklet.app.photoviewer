package ai.fd.thinklet.app.photoviewer

import ai.fd.thinklet.app.photoviewer.camera.impl.CameraRepositoryImpl
import ai.fd.thinklet.camerax.vision.Vision
import ai.fd.thinklet.camerax.vision.VisionController
import ai.fd.thinklet.camerax.vision.camera.CameraRepository
import ai.fd.thinklet.camerax.vision.httpserver.VisionRepository
import ai.fd.thinklet.camerax.vision.httpserver.impl.VisionRepositoryImpl
import androidx.camera.core.ImageAnalysis
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.binds
import org.koin.dsl.module


/**
 * アプリケーション全体で使用される依存関係を定義するKoinモジュール。
 * このモジュールは、アプリケーション起動時に `VisionApp` クラスでKoinに登録され、
 * 各コンポーネントが必要とする依存オブジェクトの生成と注入（DI）を管理する。
 */
val appModule = module {

    /**
     * `VisionRepository`インターフェースのシングルトンインスタンスを定義。
     * 実際のインスタンスとして`VisionRepositoryImpl`が生成される。
     * `VisionRepositoryImpl`はAndroidコンテキストを必要とする。
     */
    single<VisionRepository> { VisionRepositoryImpl(androidContext()) }

    /**
     * `Vision`クラスのシングルトンインスタンスを定義。
     * `Vision`は`VisionRepository`に依存しており、Koinが自動的に解決する (`get()`)。
     * この`Vision`インスタンスは、`VisionController`インターフェースと
     * `ImageAnalysis.Analyzer`インターフェースとしても利用可能になるようにバインドされる。
     */
    single { Vision(get()) } binds arrayOf(
        VisionController::class,
        ImageAnalysis.Analyzer::class,
    )

    /**
     * `CameraRepository`インターフェースのシングルトンインスタンスを定義。
     * 実際のインスタンスとして`CameraRepositoryImpl`が生成される。
     * `CameraRepositoryImpl`はAndroidコンテキストと`ImageAnalysis.Analyzer` (この場合は`Vision`インスタンス) を必要とする。
     */
    single<CameraRepository> { CameraRepositoryImpl(androidContext(), get()) }

    /**
     * `MainViewModel`のインスタンスをViewModelスコープで定義。
     * `viewModelOf` を使用することで、KoinがViewModelのライフサイクルを管理し、
     * 必要な依存関係をコンストラクタインジェクションで提供する。
     */
    viewModelOf(::MainViewModel)

}
