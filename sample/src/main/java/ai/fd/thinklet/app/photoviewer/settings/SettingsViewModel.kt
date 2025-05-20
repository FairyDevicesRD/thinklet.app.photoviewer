package ai.fd.thinklet.app.photoviewer.settings

import ai.fd.thinklet.app.photoviewer.AppPreferences
import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * アプリケーションの設定画面 (`SettingsActivity`) のためのViewModel。
 * カメラの初期回転設定の読み込み、保存、およびUIへの公開を行う。
 * 設定値は `ai.fd.thinklet.app.photoviewer.AppPreferences` (SharedPreferencesのラッパー) を通じて永続化される。
 *
 * @param application Androidアプリケーションのコンテキストを提供するApplicationオブジェクト。
 *                    `AndroidViewModel` の要件であり、SharedPreferencesへのアクセスなどに使用する。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 現在のカメラ初期回転値を保持する内部的なMutableStateFlow。
     * 初期値は `ai.fd.thinklet.app.photoviewer.AppPreferences.DEFAULT_ROTATION`。
     * この値は `loadCurrentRotation()` によってSharedPreferencesから読み込まれた実際の値で更新される。
     */
    private val _currentRotation = MutableStateFlow(AppPreferences.DEFAULT_ROTATION)

    /**
     * 現在のカメラ初期回転値を外部に公開するためのStateFlow。
     * UI (Composeなど) はこのFlowを収集して、回転設定の変更をリアクティブに監視できる。
     * このFlowは読み取り専用であり、値の変更はViewModel内の関数を通じて行われる。
     */
    val currentRotation: StateFlow<Int> = _currentRotation.asStateFlow()

    /**
     * カメラ回転オプションの定義。
     * 表示名（ユーザーフレンドリーな文字列、例: "0° (ポートレート)") と、
     * それに対応する `Surface` クラスの回転定数 (例: `Surface.ROTATION_0`) のペアをマップとして保持する。
     * このマップはUIのドロップダウンメニューなどで使用される。
     */
    val rotationOptions = mapOf(
        "0° (ポートレート)" to Surface.ROTATION_0,
        "90° (横向き左)" to Surface.ROTATION_90,
        "180° (逆ポートレート)" to Surface.ROTATION_180,
        "270° (横向き右)" to Surface.ROTATION_270
    )

    /**
     * ViewModelの初期化ブロック。
     * ViewModelが生成される際に、保存されている現在の回転設定をSharedPreferencesから読み込む。
     */
    init {
        loadCurrentRotation()
    }

    /**
     * SharedPreferencesから現在のカメラ初期回転値を非同期で読み込み、`_currentRotation` StateFlowを更新する。
     * 読み込み処理は `viewModelScope` 内のコルーチンで行われる。
     */
    private fun loadCurrentRotation() {
        viewModelScope.launch {
            _currentRotation.value = AppPreferences.getInitialCameraRotation(getApplication())
        }
    }

    /**
     * ユーザーによって選択された新しいカメラ初期回転値を保存する。
     * 指定された `rotationValue` をSharedPreferencesに永続化し、
     * 同時に `_currentRotation` StateFlowも更新してUIに即座に反映させる。
     * 保存処理は `viewModelScope` 内のコルーチンで行われる。
     *
     * @param rotationValue 保存する回転値。`Surface.ROTATION_0`、`Surface.ROTATION_90` などの定数を期待する。
     */
    fun saveRotationSetting(rotationValue: Int) {
        viewModelScope.launch {
            AppPreferences.setInitialCameraRotation(getApplication(), rotationValue)
            _currentRotation.value = rotationValue
        }
    }
}
