package ai.fd.thinklet.app.photoviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * アプリケーションのダークテーマで使用される固定のカラーパレット。
 * `Purple80`、`PurpleGrey80`、`Pink80` などの事前定義された色を使用して、
 * プライマリ、セカンダリ、ターシャリカラーなどを定義する。
 * このカラーパレットは、ダイナミックカラーが利用できない場合や、
 * 明示的にダークテーマが選択された場合に使用される。
 *
 * @see Purple80
 * @see PurpleGrey80
 * @see Pink80
 */
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

/**
 * アプリケーションのライトテーマで使用される固定のカラーパレット。
 * `Purple40`、`PurpleGrey40`、`Pink40` などの事前定義された色を使用して、
 * プライマリ、セカンダリ、ターシャリカラーなどを定義する。
 * このカラーパレットは、ダイナミックカラーが利用できない場合や、
 * 明示的にライトテーマが選択された場合（かつシステムがダークテーマでない場合）に使用される。
 *
 * @see Purple40
 * @see PurpleGrey40
 * @see Pink40
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * アプリケーション全体に適用されるMaterial Design 3のテーマ。
 * このコンポーザブル関数は、Compose UI階層のルート近くで使用され、
 * 子コンポーネントにカラースキーム、タイポグラフィ、シェイプなどを提供する。
 *
 * @param darkTheme ブール値。`true` の場合はダークテーマを、`false` の場合はライトテーマを強制する。
 *                  デフォルトではシステムのテーマ設定 (`isSystemInDarkTheme()`) に従う。
 * @param dynamicColor ブール値。`true` の場合、Android 12以降でダイナミックカラーを有効にする。
 *                     デフォルトは `true`。
 * @param content テーマを適用する対象のコンポーザブルコンテンツ。
 *                通常はアプリケーションのメイン画面やナビゲーションルートなど。
 */
@Composable
fun ThinkletVisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
