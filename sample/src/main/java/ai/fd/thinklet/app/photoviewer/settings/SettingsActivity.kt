package ai.fd.thinklet.app.photoviewer.settings

import ai.fd.thinklet.app.photoviewer.R
import ai.fd.thinklet.app.photoviewer.ui.theme.ThinkletVisionTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * アプリケーションの設定画面を提供するActivity。
 * 現在はカメラの初期回転設定機能を持つ。
 * Jetpack Composeを使用してUIを構築し、`SettingsViewModel` を介して設定の読み書きを行う。
 */
class SettingsActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    /**
     * Activityが作成されるときに呼び出される。
     * UIのセットアップ（`SettingsScreen`コンポーザブルの表示）を行う。
     *
     * @param savedInstanceState 以前に保存されたActivityの状態。通常はnull。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThinkletVisionTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(id = R.string.back_to_main)) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    finish()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "戻る"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    SettingsScreen(settingsViewModel, paddingValues = innerPadding)
                }
            }
        }
    }
}

/**
 * 設定画面のUIを定義するコンポーザブル関数。
 * カメラの初期回転値を設定するためのドロップダウンメニューを表示する。
 *
 * @param viewModel 設定データの読み書きとロジックを担う`SettingsViewModel`。
 *                  デフォルトでは`viewModel()`ファクトリ関数によりインスタンスが取得される。
 * @param paddingValues 親コンポーザブル（Scaffold）から提供されるパディング値。
 *                      ステータスバーやナビゲーションバーなどのシステムUIとの衝突を避けるために使用する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(), paddingValues: PaddingValues) {
    val currentRotation by viewModel.currentRotation.collectAsState()
    val rotationOptions = viewModel.rotationOptions
    var expanded by remember { mutableStateOf(false) }

    val selectedRotationLabel = rotationOptions.entries.find { it.value == currentRotation }?.key
        ?: rotationOptions.entries.firstOrNull()?.key ?: "未設定"


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "カメラ初期回転設定",
            style = MaterialTheme.typography.headlineSmall
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedRotationLabel,
                onValueChange = {},
                label = { Text("初期回転") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                rotationOptions.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.saveRotationSetting(value)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "注意: 設定変更後、アプリを再起動するとカメラに反映されます。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
