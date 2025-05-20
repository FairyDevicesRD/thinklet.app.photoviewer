package ai.fd.thinklet.camerax.vision.camera

import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * カメラ操作に関する機能を提供するリポジトリインターフェース。
 * カメラの初期設定、静止画撮影、リソース解放などの基本的なカメラ操作を定義する。
 */
interface CameraRepository {

    /**
     * カメラの初期設定を行い、指定されたLifecycleOwnerにカメラのライフサイクルをバインドする。
     * このメソッドは中断可能なコルーチン内で実行される。
     *
     * @param lifecycleOwner カメラのライフサイクルを管理するLifecycleOwner。
     *                       通常はActivityやFragmentのインスタンス。
     * @return 設定が成功した場合は`true`、失敗した場合は`false`を返す。
     */
    suspend fun configure(lifecycleOwner: LifecycleOwner): Boolean

    /**
     * カメラが使用するデフォルトのターゲット回転値を設定する。
     * この値は `configure` メソッドが呼び出された際に、各種 UseCase の targetRotation に適用される。
     *
     * @param rotation 設定する回転角度。`android.view.Surface.ROTATION_0`,
     *                 `android.view.Surface.ROTATION_90` など、`Surface`クラスで定義される回転定数。
     */
    fun setDefaultTargetRotation(rotation: Int)

    /**
     * 現在設定されているデフォルトのターゲット回転値を取得する。
     *
     * @return 設定されている回転角度。`android.view.Surface.ROTATION_0`などの`Surface`クラスの定数。
     */
    fun getDefaultTargetRotation(): Int

    /**
     * カメラが初期化され、使用可能な状態であるかを確認する。
     * `configure`メソッドが正常に完了し、関連リソースが適切にセットアップされていれば`true`を返す。
     *
     * @return カメラが初期化されていれば`true`、そうでなければ`false`。
     */
    fun isCameraInitialized(): Boolean

    /**
     * カメラリソースを解放する。
     * カメラの使用が終了した際や、Activity/Fragmentが破棄される際に呼び出す必要がある。
     * このメソッドを呼び出すと、カメラプレビューや画像キャプチャなどの機能は利用できなくなる。
     */
    fun releaseCamera()

    /**
     * 静止画を撮影し、内部的に定義された場所（通常は共有ストレージまたはアプリ固有ディレクトリ）に保存する。
     * このメソッドを呼び出す前に、`configure`が正常に完了し、`isCameraInitialized`が`true`を返すことを確認する必要がある。
     * 画像の保存場所は`getPhotoOutputDirectory`で取得できる。
     */
    fun captureStillImage()

    /**
     * 撮影した写真が保存されるディレクトリのパスを取得する。
     *
     * @return 写真が保存されるディレクトリを示す`File`オブジェクト。
     *         ディレクトリが存在しない場合やアクセスできない場合でも、期待されるパスを返すことがあるため、
     *         使用前にディレクトリの存在確認や作成処理が必要になる場合がある。
     */
    fun getPhotoOutputDirectory(): File
}
