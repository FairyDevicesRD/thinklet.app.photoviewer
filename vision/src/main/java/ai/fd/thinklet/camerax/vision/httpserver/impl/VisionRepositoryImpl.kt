package ai.fd.thinklet.camerax.vision.httpserver.impl

import ai.fd.thinklet.camerax.vision.camera.CameraRepository
import ai.fd.thinklet.camerax.vision.httpserver.VisionRepository
import android.content.Context
import android.util.Log
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock

/**
 * ファイル名のリストを要求として受け取るためのデータクラス。
 * 主にZIPダウンロードや一括削除などのPOSTリクエストのボディとして使用される。
 * @property filenames 操作対象のファイル名のリスト。
 */
@Serializable
data class FilenamesRequest(val filenames: List<String>)

/**
 * 削除操作の結果をクライアントに返すためのデータクラス。
 * @property message 操作全体の概要を示すメッセージ。
 * @property details 各ファイルに対する操作結果の詳細リスト。省略可能。
 */
@Serializable
data class DeletionResponse(
    val message: String,
    val details: List<String>? = null
)

/**
 * Ktorを利用してHTTPサーバー機能を提供する {@link VisionRepository} の実装クラスです。
 * 画像ストリーミング、静止画キャプチャ指示、保存画像のリスト表示、個別画像ダウンロード、
 * 複数画像のZIPダウンロード、および複数画像の削除機能を提供するエンドポイントを定義します。
 * 詳細な機能説明やパラメータについては、{@link VisionRepository} インターフェースの
 * ドキュメントを参照してください。
 *
 * @property context アプリケーションコンテキスト。アセットファイルへのアクセスなどに使用します。
 * @see VisionRepository
 */
class VisionRepositoryImpl(private val context: Context) :
    VisionRepository {
    private val cameraRepository: CameraRepository by inject(CameraRepository::class.java)
    private var ktorServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private val serverLock = Any()
    private val cacheLock = ReentrantLock()
    private var cacheImage: ByteArray? = null

    companion object {
        private const val TAG = "VisionRepositoryImpl"
    }

    /**
     * HTTPサーバーで画像リストを表示する際に、各画像の情報をクライアントに送信するためのデータクラス。
     * @property filename 画像のファイル名。
     * @property timestamp 画像の最終更新日時（エポックミリ秒）。
     * @property formattedDate 人間が読みやすい形式にフォーマットされた最終更新日時文字列。
     */
    @Serializable
    data class ImageInfo(val filename: String, val timestamp: Long, val formattedDate: String)


    override fun start(port: Int) {
        synchronized(serverLock) {
            if (ktorServer != null) {
                Log.i(TAG, "Ktor server is already considered active or starting on port $port.")
                return
            }

            Log.i(TAG, "Attempting to start Ktor server on port $port...")
            try {
                ktorServer = embeddedServer(
                    factory = CIO,
                    port = port,
                    module = { appModuleKtor3() }
                ).apply {
                    start(wait = false)
                }
                Log.i(TAG, "Ktor server started successfully on port $port.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Ktor server on port $port", e)
                ktorServer = null
            }
        }
    }


    override fun stop() {
        synchronized(serverLock) {
            if (ktorServer == null) {
                Log.i(TAG, "Ktor server is not running or already stopped.")
                return
            }
            Log.i(TAG, "Attempting to stop Ktor server...")
            try {
                ktorServer?.stop(1000, 5000)
                Log.i(TAG, "Ktor server stopped successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Ktor server", e)
            } finally {
                ktorServer = null
            }
        }
    }


    override fun updateJpeg(bytes: ByteArray) {
        if (ktorServer == null) {
            Log.w(TAG, "Ktor server is not running, cannot update JPEG.")
            return
        }
        cacheLock.withLock {
            cacheImage = bytes.copyOf()
        }
    }

    /**
     * Ktorアプリケーションモジュールを定義する拡張関数。
     * ここでサーバーのルーティング、コンテンツネゴシエーション（JSONシリアライズなど）、
     * および静的コンテンツの配信設定を行う。
     * この関数は`embeddedServer`の`module`ブロック内で呼び出される。
     */
    private fun Application.appModuleKtor3() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        // ルーティング設定
        routing {
            /**
             * ルートパス ("/") へのGETリクエスト。
             * アプリケーションのアセットフォルダ内にある`index.html`をレスポンスとして返す。
             * ファイルの読み込みに失敗した場合はInternalServerErrorを返す。
             */
            get("/") {
                val currentContext = this@VisionRepositoryImpl.context
                try {
                    currentContext.assets.open("index.html").use { inputStream ->
                        call.respondBytes(
                            inputStream.readBytes(),
                            ContentType.Text.Html,
                            HttpStatusCode.OK
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error serving index.html", e)
                    call.respond(HttpStatusCode.InternalServerError, "Could not serve the page.")
                }
            }

            /**
             * 静的リソース ("/static") へのアクセスをアセットフォルダにマッピングする。
             * 例えば、"/static/css/style.css" へのリクエストは、
             * アセットフォルダ内の "css/style.css" ファイルに対応する。
             */
            staticResources("/static", "assets")

            /**
             * "/image" エンドポイントへのGETリクエスト。
             * `cacheImage`にキャッシュされている最新のJPEG画像をレスポンスとして返す。
             * キャッシュがない場合はNotFoundを返す。
             */
            get("/image") {
                val img = this@VisionRepositoryImpl.cacheLock.withLock {
                    this@VisionRepositoryImpl.cacheImage?.copyOf()
                }
                if (img != null) {
                    call.respondBytes(
                        bytes = img,
                        contentType = ContentType.Image.JPEG,
                        status = HttpStatusCode.OK
                    )
                } else {
                    call.respond(HttpStatusCode.NotFound, "No image cached.")
                }
            }

            /**
             * "/capture" エンドポイントへのGETリクエスト。
             * `CameraRepository`を通じて静止画の撮影を指示する。
             * カメラが初期化されていない場合はServiceUnavailableを返す。
             * 撮影リクエストが受け付けられた場合はAcceptedを返す。
             */
            get("/capture") {
                Log.i(TAG, "HTTP /capture request received")
                if (!this@VisionRepositoryImpl.cameraRepository.isCameraInitialized()) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "Camera not ready.")
                    return@get
                }
                this@VisionRepositoryImpl.cameraRepository.captureStillImage()
                call.respond(
                    HttpStatusCode.Accepted,
                    "Capture request accepted. Photo will be saved."
                )
            }

            /**
             * "/latest_image" エンドポイントへのGETリクエスト。
             * `CameraRepository`で指定された写真出力ディレクトリ内にある、
             * 最終更新日時が最も新しいJPEG画像をレスポンスとして返す。
             * 画像ディレクトリが存在しない、画像がない、または最新画像が見つからない場合はNotFoundを返す。
             * その他のエラーの場合はInternalServerErrorを返す。
             */
            get("/latest_image") {
                try {
                    val outputDir =
                        this@VisionRepositoryImpl.cameraRepository.getPhotoOutputDirectory()
                    if (!outputDir.exists() || !outputDir.isDirectory) {
                        call.respond(HttpStatusCode.NotFound, "Image directory not found.")
                        return@get
                    }
                    val imageFiles = outputDir.listFiles { _, name -> name.endsWith(".jpg", true) }
                    if (imageFiles.isNullOrEmpty()) {
                        call.respond(HttpStatusCode.NotFound, "No images found.")
                        return@get
                    }
                    val latestImageFile = imageFiles.maxByOrNull { it.lastModified() }
                    if (latestImageFile != null && latestImageFile.exists()) {
                        call.respondFile(latestImageFile)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Latest image not found.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error serving latest image", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error serving image.")
                }
            }

            /**
             * "/images_list" エンドポイントへのGETリクエスト。
             * 写真出力ディレクトリ内の全てのJPEG画像の情報をJSON配列として返す。
             * 画像は最終更新日時の降順（新しいものが先頭）でソートされる。
             * 各画像の情報には、ファイル名、タイムスタンプ、フォーマットされた日付が含まれる。
             * エラー発生時はInternalServerErrorを返す。
             */
            get("/images_list") {
                try {
                    val outputDir =
                        this@VisionRepositoryImpl.cameraRepository.getPhotoOutputDirectory()
                    if (!outputDir.exists() || !outputDir.isDirectory) {
                        call.respond(HttpStatusCode.NotFound, "Image directory not found.")
                        return@get
                    }
                    val imageFiles = outputDir.listFiles { _, name -> name.endsWith(".jpg", true) }
                        ?: emptyArray()
                    val sortedImageFiles = imageFiles.sortedByDescending { it.lastModified() }
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                    val imageInfoList = sortedImageFiles.map { file ->
                        ImageInfo(
                            filename = file.name,
                            timestamp = file.lastModified(),
                            formattedDate = dateFormat.format(Date(file.lastModified()))
                        )
                    }
                    call.respond(imageInfoList)
                } catch (e: Exception) {
                    Log.e(TAG, "Error serving images list", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error serving images list.")
                }
            }

            /**
             * "/image_file/{filename}" エンドポイントへのGETリクエスト。
             * パスパラメータで指定されたファイル名の画像を写真出力ディレクトリから探し、レスポンスとして返す。
             * ファイル名には英数字、ドット、アンダースコア、ハイフンのみを許可する。
             * ファイルが見つからない場合やアクセスが許可されない場合はNotFoundを返す。
             * ファイル名パラメータが不足している場合や不正な場合はBadRequestを返す。
             * ディレクトリトラバーサル攻撃を防ぐために、ファイルパスの正規化と比較を行う。
             *
             * @param filename ダウンロードする画像のファイル名。
             */
            get("/image_file/{filename}") {
                val filename = call.parameters["filename"]
                if (filename == null) {
                    call.respond(HttpStatusCode.BadRequest, "Filename parameter is missing.")
                    return@get
                }
                val allowedFilenamePattern = Regex("^[a-zA-Z0-9._-]+$")
                if (!allowedFilenamePattern.matches(filename)) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid filename.")
                    return@get
                }
                val outputDir = this@VisionRepositoryImpl.cameraRepository.getPhotoOutputDirectory()
                val imageFile = File(outputDir, filename)
                if (imageFile.exists() &&
                    imageFile.isFile &&
                    imageFile.parentFile?.canonicalPath == outputDir.canonicalPath) {
                    call.respondFile(imageFile)
                } else {
                    Log.w(
                        TAG,
                        "Image file not found or access denied: $filename (Resolved: ${imageFile.absolutePath}, Expected parent: ${outputDir.canonicalPath})"
                    )
                    call.respond(HttpStatusCode.NotFound, "Image not found or access denied.")
                }
            }

            /**
             * "/download_selected_zip" エンドポイントへのPOSTリクエスト。
             * リクエストボディで指定されたファイル名リスト（`FilenamesRequest`）に基づいて、
             * 対応する画像をZIPアーカイブにまとめてダウンロードさせる。
             * ZIPファイル名は "selected_images.zip" となる。
             * ファイル名には英数字、ドット、アンダースコア、ハイフンのみを許可する。
             * 対象ファイルが見つからない、またはZIPに追加するファイルが一つもない場合はNotFoundを返す。
             * リクエストが不正な場合はBadRequestを返す。
             * その他のエラーの場合はInternalServerErrorを返す。
             */
            post("/download_selected_zip") {
                try {
                    val request = call.receive<FilenamesRequest>()
                    val filenamesToZip = request.filenames
                    if (filenamesToZip.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "No filenames provided for download."
                        )
                        return@post
                    }
                    val outputDir =
                        this@VisionRepositoryImpl.cameraRepository.getPhotoOutputDirectory()
                    val zipByteArrayOutputStream = ByteArrayOutputStream()
                    ZipOutputStream(zipByteArrayOutputStream).use { zipOutputStream ->
                        var filesZipped = 0
                        for (filenameInZip in filenamesToZip) {
                            val allowedFilenamePattern = Regex("^[a-zA-Z0-9._-]+$")
                            if (!allowedFilenamePattern.matches(filenameInZip)) {
                                Log.w(TAG, "Skipping invalid filename for zipping: $filenameInZip")
                                continue
                            }
                            val imageFile = File(outputDir, filenameInZip)
                            if (imageFile.exists() &&
                                imageFile.isFile &&
                                imageFile.parentFile?.canonicalPath == outputDir.canonicalPath
                            ) {
                                try {
                                    FileInputStream(imageFile).use { fis ->
                                        val zipEntry = ZipEntry(imageFile.name)
                                        zipOutputStream.putNextEntry(zipEntry)
                                        fis.copyTo(zipOutputStream)
                                        zipOutputStream.closeEntry()
                                    }
                                    filesZipped++
                                    Log.i(TAG, "Added to ZIP: ${imageFile.name}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error adding file to ZIP: ${imageFile.name}", e)
                                }
                            } else {
                                Log.w(
                                    TAG,
                                    "File not found or access denied for zipping: $filenameInZip"
                                )
                            }
                        }
                        if (filesZipped > 0) {
                            val zipBytes = zipByteArrayOutputStream.toByteArray()
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    "selected_images.zip"
                                ).toString()
                            )
                            call.respondBytes(
                                bytes = zipBytes,
                                contentType = ContentType.Application.Zip,
                                status = HttpStatusCode.OK
                            )
                            Log.i(TAG, "$filesZipped files zipped and sent.")
                        } else {
                            Log.w(TAG, "No valid files were found to create a ZIP.")
                            call.respond(
                                HttpStatusCode.NotFound,
                                "No valid files found to create a ZIP archive."
                            )
                        }
                    }
                } catch (e: io.ktor.server.plugins.BadRequestException) {
                    Log.e(TAG, "Bad request for ZIP download: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        DeletionResponse(message = "Invalid request for ZIP download: ${e.cause?.message ?: e.message}")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating ZIP file", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeletionResponse(message = "Error creating ZIP file: ${e.message}")
                    )
                }
            }

            /**
             * "/delete_selected_images" エンドポイントへのPOSTリクエスト。
             * リクエストボディで指定されたファイル名リスト（`FilenamesRequest`）に基づいて、
             * 対応する画像を写真出力ディレクトリから削除する。
             * ファイル名には英数字、ドット、アンダースコア、ハイフンのみを許可する。
             * 各ファイルの削除結果を含む`DeletionResponse`を返す。
             * リクエストが不正な場合はBadRequestを返す。
             * その他のエラーの場合はInternalServerErrorを返す。
             */
            post("/delete_selected_images") {
                try {
                    val request = call.receive<FilenamesRequest>()
                    val filenamesToDelete = request.filenames
                    if (filenamesToDelete.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            DeletionResponse(message = "No filenames provided for deletion.")
                        )
                        return@post
                    }
                    val outputDir =
                        this@VisionRepositoryImpl.cameraRepository.getPhotoOutputDirectory()
                    val results = mutableListOf<String>()
                    var allSucceeded = true
                    for (filenameToDelete in filenamesToDelete) {
                        val allowedFilenamePattern = Regex("^[a-zA-Z0-9._-]+$")
                        if (!allowedFilenamePattern.matches(filenameToDelete)) {
                            results.add("Skipped invalid filename: $filenameToDelete")
                            allSucceeded = false
                            continue
                        }
                        val imageFile = File(outputDir, filenameToDelete)
                        if (imageFile.exists() &&
                            imageFile.isFile &&
                            imageFile.parentFile?.canonicalPath == outputDir.canonicalPath) {
                            if (imageFile.delete()) {
                                results.add("Deleted: $filenameToDelete")
                                Log.i(TAG, "Image deleted (batch): ${imageFile.absolutePath}")
                            } else {
                                results.add("Failed to delete: $filenameToDelete")
                                Log.e(
                                    TAG,
                                    "Failed to delete image (batch): ${imageFile.absolutePath}"
                                )
                                allSucceeded = false
                            }
                        } else {
                            results.add("Not found or access denied: $filenameToDelete")
                            Log.w(TAG, "Image file not found for batch deletion: $filenameToDelete (Expected parent: ${outputDir.canonicalPath})")
                            allSucceeded = false
                        }
                    }
                    val responseMessage =
                        if (allSucceeded) "All selected images processed successfully." else "Batch deletion process completed with some issues."
                    call.respond(
                        HttpStatusCode.OK,
                        DeletionResponse(message = responseMessage, details = results)
                    )
                } catch (e: io.ktor.server.plugins.BadRequestException) {
                    Log.e(TAG, "Bad request for batch delete: ${e.message}", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        DeletionResponse(message = "Invalid request for batch delete: ${e.cause?.message ?: e.message}")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error during batch image deletion", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        DeletionResponse(message = "Error during batch image deletion: ${e.message}")
                    )
                }
            }
        }
    }
}
