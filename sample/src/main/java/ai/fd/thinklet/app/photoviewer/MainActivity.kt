package ai.fd.thinklet.app.photoviewer

import ai.fd.thinklet.app.photoviewer.settings.SettingsActivity
import ai.fd.thinklet.app.photoviewer.ui.theme.ThinkletVisionTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.android.ext.android.inject

/**
 * アプリケーションのメイン画面となるActivity。
 * カメラパーミッションの管理、カメラとVision機能の初期化と制御、
 * およびユーザーインターフェースの表示を行う。
 * Jetpack Composeを使用してUIを構築し、ViewModel (`MainViewModel`) を介してUIロジックとデータ処理を行う。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by inject()

    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * カメラパーミッションのリクエスト結果を処理するためのランチャー。
     * パーミッションが許可された場合はカメラとVision機能を開始し、
     * 拒否された場合はそのステータスをViewModelに通知する。
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Camera permission GRANTED by user.")
                viewModel.updateCameraPermissionStatus(this)
                viewModel.startCameraAndVision(this)
            } else {
                Log.d(TAG, "Camera permission DENIED by user.")
                viewModel.updateCameraPermissionStatus(this)
            }
        }

    /**
     * Activityが作成されるときに呼び出される。
     * UIのセットアップ、ViewModelの初期化、パーミッションの確認と要求、
     * およびカメラの初期回転値の設定を行う。
     *
     * @param savedInstanceState 以前に保存されたActivityの状態。通常はnull。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val defaultRotationFromSettings = AppPreferences.getInitialCameraRotation(
            context = this,
            defaultValue = Surface.ROTATION_0
        )
        Log.d(TAG, "Initial rotation from settings: $defaultRotationFromSettings in onCreate")
        viewModel.setInitialCameraRotation(defaultRotationFromSettings)

        viewModel.updateCameraPermissionStatus(this)

        setContent {
            ThinkletVisionTheme {
                val cameraPermissionStatus by viewModel.cameraPermissionStatus.collectAsState()
                val displayMessage by viewModel.getDisplayMessage(LocalContext.current)
                    .collectAsState()

                LaunchedEffect(cameraPermissionStatus) {
                    when (cameraPermissionStatus) {
                        PermissionStatus.UNREQUESTED -> {
                            Log.d(TAG, "Permission UNREQUESTED, launching permission request.")
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }

                        PermissionStatus.DENIED -> {
                            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                                Log.d(
                                    TAG,
                                    "Permission DENIED, rationale should be shown (user can be asked again)."
                                )
                            } else {
                                Log.d(
                                    TAG,
                                    "Permission DENIED permanently or not yet requested systematically. Will not re-request automatically."
                                )
                            }
                        }

                        PermissionStatus.GRANTED -> {
                            Log.d(
                                TAG,
                                "Permission GRANTED, ensuring camera and vision are started."
                            )
                            viewModel.startCameraAndVision(this@MainActivity)
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(getString(R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.LightGray
                            ),
                            actions = {
                                IconButton(onClick = {
                                    startActivity(
                                        Intent(
                                            this@MainActivity,
                                            SettingsActivity::class.java
                                        )
                                    )
                                }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "設定")
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Screen(
                        modifier = Modifier.padding(innerPadding),
                        message = displayMessage,
                        permissionStatus = cameraPermissionStatus,
                        onRequestPermissionAgain = {
                            Log.d(TAG, "onRequestPermissionAgain called from UI.")
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            Log.d(TAG, "onOpenSettings called from UI.")
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", packageName, null)
                            intent.data = uri
                            startActivity(intent)
                        }
                    )
                }
            }
        }
        viewModel.setup(this.lifecycle)
    }

    /**
     * Activityがフォアグラウンドに戻るときに呼び出される。
     * カメラパーミッションのステータスを再確認し、許可されていればカメラとVision機能を開始する。
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Updating camera permission status.")
        viewModel.updateCameraPermissionStatus(this)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startCameraAndVision(this)
        }
    }

    /**
     * Activityが破棄されるときに呼び出される。
     * ViewModelのライフサイクルイベントハンドラを破棄する。
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.teardown(this.lifecycle)
    }
}

/**
 * アプリケーションのメイン画面コンテンツを表示するコンポーザブル関数。
 * ViewModelから受け取ったメッセージとパーミッションステータスに基づいてUIをレンダリングする。
 * パーミッションが拒否されている場合は、再要求ボタンや設定画面を開くボタンを表示する。
 *
 * @param modifier このコンポーザブルに適用するModifier。
 * @param message 表示するメッセージ文字列。IPアドレスやステータス情報など。
 * @param permissionStatus 現在のカメラパーミッションの状態。
 * @param onRequestPermissionAgain 「権限を要求する」ボタンがクリックされたときに呼び出されるコールバック。
 * @param onOpenSettings 「アプリ設定を開く」ボタンがクリックされたときに呼び出されるコールバック。
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    message: String,
    permissionStatus: PermissionStatus,
    onRequestPermissionAgain: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (permissionStatus) {
            PermissionStatus.UNREQUESTED -> {
                Button(onClick = onRequestPermissionAgain) {
                    Text("カメラ権限を確認する")
                }
            }

            PermissionStatus.DENIED -> {
                Button(onClick = onRequestPermissionAgain) {
                    Text("権限を要求する")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onOpenSettings) {
                    Text("アプリ設定を開く")
                }
            }

            PermissionStatus.GRANTED -> {
                // パーミッションが許可されている場合は、UI上での追加アクションは不要
            }
        }
    }
}
