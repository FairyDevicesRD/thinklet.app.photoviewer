package ai.fd.thinklet.app.photoviewer

import ai.fd.thinklet.camerax.vision.util.Logging
import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * アプリケーション全体で共有される状態やリソースを初期化・管理するためのカスタムApplicationクラス。
 * このクラスは、アプリケーションの起動時に最初にインスタンス化され、
 * Koinによる依存性注入フレームワークのセットアップなど、
 * アプリケーション全体の初期設定を行う。
 */
class VisionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Logging.DEFAULT_LEVEL)
            androidContext(this@VisionApp)
            modules(appModule)
        }
    }
}
