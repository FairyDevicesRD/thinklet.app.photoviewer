package ai.fd.thinklet.app.photoviewer

import android.content.Context
import android.content.SharedPreferences
import android.view.Surface

/**
 * アプリケーションの設定値を永続化するためのヘルパーオブジェクト。
 * 主にSharedPreferencesを利用して、キーと値のペアで設定を保存・読み込みする。
 * このオブジェクトはシングルトンとして機能する。
 */
object AppPreferences {
    /** SharedPreferencesファイルの名前。 */
    private const val PREFS_NAME = "camera_settings_prefs"

    /** カメラの初期回転設定を保存するためのキー。 */
    private const val KEY_INITIAL_ROTATION = "initial_camera_rotation"

    /**
     * カメラのデフォルト回転値。
     * `Surface.ROTATION_0` (通常はポートレート) をデフォルト値として使用する。
     * この値は、設定がまだ保存されていない場合や、読み込みに失敗した場合のフォールバックとして使用される。
     */
    internal val DEFAULT_ROTATION = Surface.ROTATION_0

    /**
     * 指定されたコンテキストを使用して、プライベートモードのSharedPreferencesインスタンスを取得する。
     *
     * @param context SharedPreferencesインスタンスを取得するためのコンテキスト。
     * @return SharedPreferencesのインスタンス。
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * カメラの初期回転値をSharedPreferencesに保存する。
     *
     * @param context SharedPreferencesにアクセスするためのコンテキスト。
     * @param rotation 保存する回転値。`Surface.ROTATION_0`、`Surface.ROTATION_90` などの定数を期待する。
     */
    fun setInitialCameraRotation(context: Context, rotation: Int) {
        getSharedPreferences(context).edit()
            .putInt(KEY_INITIAL_ROTATION, rotation)
            .apply()
    }

    /**
     * SharedPreferencesからカメラの初期回転値を取得する。
     * 保存されている値がない場合は、このオブジェクト内で定義されている `DEFAULT_ROTATION` を返す。
     *
     * @param context SharedPreferencesにアクセスするためのコンテキスト。
     * @param defaultValue 取得できなかった場合に返すデフォルト値。
     *                     このパラメータは将来的に削除される可能性があるコメントがあるが、現在は機能している。
     *                     デフォルトでは `ai.fd.thinklet.app.photoviewer.AppPreferences.DEFAULT_ROTATION` が使用される。
     * @return 保存されている回転値、またはデフォルト値。
     */
    fun getInitialCameraRotation(context: Context, defaultValue: Int = DEFAULT_ROTATION): Int {
        return getSharedPreferences(context).getInt(KEY_INITIAL_ROTATION, defaultValue)
    }
}
