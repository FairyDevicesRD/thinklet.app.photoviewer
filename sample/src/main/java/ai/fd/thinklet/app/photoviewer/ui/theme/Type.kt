package ai.fd.thinklet.app.photoviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * アプリケーション全体で使用されるMaterial Design 3のタイポグラフィ定義。
 * このオブジェクトは、Compose UIでテキストを表示する際のデフォルトのスタイルセットを提供する。
 *
 * @see androidx.compose.material3.Typography
 * @see androidx.compose.material3.MaterialTheme
 */
val Typography = Typography(
    /**
     * 大きめの本文テキストスタイル (`bodyLarge`)。
     * 通常の段落や情報表示に適している。
     */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
)
