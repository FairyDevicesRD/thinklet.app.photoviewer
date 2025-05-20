package ai.fd.thinklet.app.photoviewer

import ai.fd.thinklet.camerax.vision.VisionController
import ai.fd.thinklet.camerax.vision.camera.CameraRepository
import ai.fd.thinklet.camerax.vision.util.getWifiIPAddress
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * カメラパーミッションの状態を表すenum。
 */
enum class PermissionStatus {
    /** まだパーミッション要求を行っていない状態。初期状態。 */
    UNREQUESTED,

    /** ユーザーによってパーミッションが許可された状態。 */
    GRANTED,

    /** ユーザーによってパーミッションが拒否された状態。 */
    DENIED
}

/**
 * メイン画面のUIロジックと状態を管理するViewModelのインターフェース。
 */
interface IMainViewModel {
    /**
     * ViewModelの初期設定を行い、Lifecycleの監視を開始する。
     * @param lifecycle 監視対象のLifecycle。
     */
    fun setup(lifecycle: Lifecycle)

    /**
     * ViewModelのリソースを解放し、Lifecycleの監視を終了する。
     * @param lifecycle 監視対象だったLifecycle。
     */
    fun teardown(lifecycle: Lifecycle)

    /**
     * UIに表示するメッセージのStateFlowを取得する。
     * メッセージはパーミッション状態やネットワーク状態によって変化する。
     * @param context メッセージ生成に必要なContext。
     * @return 表示メッセージを公開するStateFlow。
     */
    fun getDisplayMessage(context: Context): StateFlow<String>

    /**
     * カメラの初期回転角度を設定する。
     * これは通常、デバイスの初期向きに基づいてActivityから呼び出される。
     * @param rotation Surface.ROTATION_0, Surface.ROTATION_90などの回転角度。
     */
    fun setInitialCameraRotation(rotation: Int)

    /** カメラパーミッションの現在の状態を公開するStateFlow。 */
    val cameraPermissionStatus: StateFlow<PermissionStatus>

    /**
     * 現在のカメラパーミッション状態をシステムから取得し、内部状態を更新する。
     * @param context パーミッション確認に必要なContext。
     */
    fun updateCameraPermissionStatus(context: Context)

    /**
     * カメラのプレビューとVisionControllerによるHTTPサーバーを開始する。
     * このメソッドは、カメラパーミッションが許可されており、かつLifecycleOwnerが適切な状態の場合に呼び出される。
     * 冪等性が担保されており、複数回呼び出されても安全に処理される。
     * @param owner カメラリソースとライフサイクルを紐付けるためのLifecycleOwner。
     */
    fun startCameraAndVision(owner: LifecycleOwner)
}

/**
 * メイン画面に関連するビジネスロジックとUI状態を管理するViewModel。
 * カメラの制御、パーミッション管理、HTTPサーバーの起動/停止、UIへの情報提供を行う。
 *
 * @property application アプリケーションコンテキスト。リソースアクセスやシステムサービス取得に使用。
 * @property cameraRepository カメラ操作を行うためのリポジトリ。
 * @property vision VisionControllerのインスタンス。HTTPサーバーの制御を行う。
 */
class MainViewModel(
    private val application: Application,
    private val cameraRepository: CameraRepository,
    private val vision: VisionController
) : ViewModel(), IMainViewModel, DefaultLifecycleObserver {

    /**
     * カメラパーミッションの現在の状態を保持するMutableStateFlow。
     * 内部でのみ更新可能。
     * @see cameraPermissionStatus
     */
    private val _cameraPermissionStatus = MutableStateFlow(PermissionStatus.UNREQUESTED)

    /**
     * カメラパーミッションの現在の状態を外部に公開するStateFlow。
     * UIはこのFlowを監視してパーミッション状態の変化に応じた表示更新を行う。
     */
    override val cameraPermissionStatus: StateFlow<PermissionStatus> =
        _cameraPermissionStatus.asStateFlow()

    /**
     * UIに表示するメッセージを保持するMutableStateFlow。
     * このメッセージは、カメラのパーミッション状態、Wi-Fi接続状態、サーバーの起動状態などに基づいて動的に生成される。
     */
    private val _displayMessage = MutableStateFlow("")

    /**
     * カメラとVisionControllerの起動処理が既に実行されたかどうかを示すフラグ。
     * `startCameraAndVision`の冪等性を担保するために使用される。
     * マルチスレッドからのアクセスを考慮して`@Volatile`アノテーションが付与されている。
     */
    @Volatile
    private var isCameraAndVisionStarted = false

    /**
     * `startCameraAndVision`および`teardown`/`onCleared`での起動・停止処理の排他制御を行うためのロックオブジェクト。
     * これにより、複数のスレッドやライフサイクルイベントから同時に状態が変更されることを防ぐ。
     */
    private val startStopLock = Any()

    /**
     * 現在のパーミッション状態とネットワーク状態に基づいて、加工前のメッセージ文字列を生成する。
     *
     * @param context Wi-Fi IPアドレス取得などのために使用されるContext。
     * @param status 現在のカメラパーミッション状態。
     * @return 生成されたメッセージ文字列。
     */
    private fun getRawMessage(context: Context, status: PermissionStatus): String {
        return when (status) {
            PermissionStatus.GRANTED -> {
                val ip = getWifiIPAddress(context)
                if (ip.isEmpty()) {
                    "Wi-Fiに接続してください。"
                } else {
                    if (isCameraAndVisionStarted) {
                        "http://$ip:$PORT でアクセス可能です"
                    } else {
                        "カメラとサーバーを起動中です... (IP: $ip:$PORT)"
                    }
                }
            }

            PermissionStatus.DENIED -> "カメラの権限が不足しています"
            PermissionStatus.UNREQUESTED -> "カメラの権限を要求しています..."
        }
    }

    /**
     * UIに表示するためのメッセージをStateFlowとして提供する。
     * このFlowは`cameraPermissionStatus`の変更を監視し、自動的にメッセージを更新する。
     *
     * @param context メッセージ生成に必要なContext。
     * @return 表示メッセージを公開するStateFlow。
     */
    override fun getDisplayMessage(context: Context): StateFlow<String> {
        viewModelScope.launch {
            cameraPermissionStatus.collect { status ->
                _displayMessage.value = getRawMessage(context, status)
            }
        }
        // 初期値も設定 (collectが始まる前に値が設定されるように)
        _displayMessage.value = getRawMessage(context, _cameraPermissionStatus.value)
        return _displayMessage.asStateFlow()
    }

    /**
     * ViewModelのセットアップ処理。指定されたLifecycleに自身をObserverとして登録する。
     * これにより、ActivityやFragmentのライフサイクルイベント（onStart, onStopなど）をViewModelで検知できるようになる。
     * このメソッドはメインスレッドで呼び出される必要がある。
     *
     * @param lifecycle 監視対象のLifecycle。
     */
    @MainThread
    override fun setup(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
    }

    /**
     * ViewModelの破棄処理。指定されたLifecycleから自身をObserverとして削除する。
     * VisionControllerの停止と関連フラグのリセットも行う。
     * このメソッドはメインスレッドで呼び出される必要がある。
     *
     * @param lifecycle 監視を解除するLifecycle。
     */
    @MainThread
    override fun teardown(lifecycle: Lifecycle) {
        Log.d(TAG, "teardown called. Stopping vision and resetting state.")
        synchronized(startStopLock) {
            if (isCameraAndVisionStarted) {
                vision.stop()
                isCameraAndVisionStarted = false
                Log.d(TAG, "Vision stopped and isCameraAndVisionStarted set to false in teardown.")
            }
        }
        lifecycle.removeObserver(this)
    }

    /**
     * システムから現在のカメラパーミッション状態を確認し、内部の`_cameraPermissionStatus`を更新する。
     * 状態が変更された場合、表示メッセージも更新する。
     *
     * @param context パーミッションの確認に必要なContext。
     */
    override fun updateCameraPermissionStatus(context: Context) {
        val newStatus = if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }

        val oldStatus = _cameraPermissionStatus.value
        _cameraPermissionStatus.update { newStatus }
        _displayMessage.update { getRawMessage(context, newStatus) }

        Log.d(TAG, "Camera permission status updated from $oldStatus to: $newStatus")

        if (newStatus == PermissionStatus.GRANTED && oldStatus != PermissionStatus.GRANTED) {
            Log.d(
                TAG,
                "Permission newly GRANTED in updateCameraPermissionStatus. Camera will be started on next onStart or explicit call."
            )
        }
    }

    /**
     * LifecycleOwnerの`onStart`イベントハンドラ。
     * このタイミングでカメラパーミッションが許可されていれば、カメラとVisionControllerの起動を試みる。
     *
     * @param owner `onStart`イベントを発生させたLifecycleOwner。
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "onStart called. Current permission: ${_cameraPermissionStatus.value}")
        if (_cameraPermissionStatus.value == PermissionStatus.GRANTED) {
            Log.d(TAG, "Permission GRANTED in onStart, attempting to start camera/vision.")
            startCameraAndVision(owner)
        }
    }

    /**
     * カメラのプレビュー設定とVisionControllerによるHTTPサーバーの起動を行う。
     * この処理は冪等性が担保されており、複数回呼び出されても安全。
     * 起動処理は`synchronized`ブロック内で排他制御される。
     * カメラパーミッションが許可されていない場合は起動しない。
     *
     * @param owner カメラリソースとライフサイクルを紐付けるためのLifecycleOwner。
     *              通常はActivityやFragmentのインスタンス。
     */
    override fun startCameraAndVision(owner: LifecycleOwner) {
        // synchronizedブロックで排他制御
        synchronized(startStopLock) {
            if (_cameraPermissionStatus.value != PermissionStatus.GRANTED) {
                Log.w(
                    TAG,
                    "Attempted to start camera and vision without GRANTED permission. Status: ${_cameraPermissionStatus.value}"
                )
                _displayMessage.update {
                    getRawMessage(
                        application,
                        _cameraPermissionStatus.value
                    )
                }
                return@synchronized
            }

            if (isCameraAndVisionStarted) {
                Log.d(TAG, "Camera and Vision already started. Ignoring redundant call.")
                _displayMessage.update { getRawMessage(application, _cameraPermissionStatus.value) }
                return@synchronized
            }

            Log.i(TAG, "Starting Camera and Vision...")
            viewModelScope.launch {
                try {
                    cameraRepository.configure(owner)
                    vision.start(PORT)
                    isCameraAndVisionStarted = true
                    Log.i(TAG, "Camera and Vision started successfully.")
                    _displayMessage.update { getRawMessage(application, PermissionStatus.GRANTED) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting Camera or Vision", e)
                    isCameraAndVisionStarted = false
                    _displayMessage.update { "カメラまたはサーバーの起動に失敗: ${e.localizedMessage}" }
                    vision.stop()
                }
            }
        }
    }

    /**
     * LifecycleOwnerの`onStop`イベントハンドラ。
     * 現在の実装では特別な処理は行わないが、将来的に`onStop`でリソースを部分的に解放する場合などに使用できる。
     * VisionControllerの完全な停止とリソース解放は`onCleared`または`teardown`で行われる。
     *
     * @param owner `onStop`イベントを発生させたLifecycleOwner。
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "onStop called. Currently no specific action taken here for stopping vision.")
    }

    /**
     * ViewModelが破棄される直前に呼び出される。
     * `DefaultLifecycleObserver`のライフサイクルイベントとは独立して、ViewModelのスコープ終了時に確実に呼ばれる。
     * VisionControllerの停止、関連フラグのリセット、およびカメラリソースの解放を行う。
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared called. Ensuring vision is stopped and resources are released.")
        synchronized(startStopLock) {
            if (isCameraAndVisionStarted) {
                vision.stop()
                isCameraAndVisionStarted = false
                Log.d(TAG, "Vision stopped and isCameraAndVisionStarted set to false in onCleared.")
            }
        }
        cameraRepository.releaseCamera()
    }

    /**
     * カメラプレビューの初期回転角度を設定する。
     * このメソッドは通常、Activityが生成された直後や画面回転が検知された際に、
     * 現在のデバイスの向きに基づいて適切な回転角度を設定するために呼び出される。
     *
     * @param rotation 設定する回転角度。通常は`Surface.ROTATION_0`, `Surface.ROTATION_90`など。
     */
    override fun setInitialCameraRotation(rotation: Int) {
        Log.d(TAG, "Setting initial camera rotation to: $rotation via ViewModel")
        cameraRepository.setDefaultTargetRotation(rotation)
    }

    /**
     * `MainViewModel`固有の定数を保持するコンパニオンオブジェクト。
     */
    private companion object {
        /** HTTPサーバーがリッスンするポート番号。 */
        const val PORT = 8080

        /** Logcat出力用のタグ。 */
        const val TAG = "MainViewModel"
    }
}
