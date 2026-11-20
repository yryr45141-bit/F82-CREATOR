package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

data class CompressedVideoResult(
    val file: File,
    val durationSec: Long,
    val autoCoverBase64: String?
)

object VideoCompressor {

    /**
     * Compresses an MP4/video Uri to 720p H.264/AAC using Jetpack Media3 Transformer
     * to minimize mobile data usage while preserving crisp gameplay visuals.
     */
    @OptIn(UnstableApi::class)
    suspend fun compressVideo(
        context: Context,
        sourceUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<CompressedVideoResult> {
        val outputDir = File(context.filesDir, "compressed_videos").apply { mkdirs() }
        val outputFile = File(outputDir, "ff_${UUID.randomUUID()}.mp4")

        val metadata = withContext(Dispatchers.IO) { extractMetadata(context, sourceUri) }

        return suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                try {
                    val mediaItem = MediaItem.fromUri(sourceUri)
                    val videoEffect = Presentation.createForHeight(720)
                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setEffects(Effects(emptyList(), listOf(videoEffect)))
                        .build()

                    val progressHolder = ProgressHolder()
                    var transformerRef: Transformer? = null

                    val progressRunnable = object : Runnable {
                        override fun run() {
                            val t = transformerRef ?: return
                            val state = t.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                            }
                            if (cont.isActive) {
                                mainHandler.postDelayed(this, 150L)
                            }
                        }
                    }

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                mainHandler.removeCallbacks(progressRunnable)
                                onProgress(1f)
                                if (cont.isActive) {
                                    cont.resume(
                                        Result.success(
                                            CompressedVideoResult(
                                                file = outputFile,
                                                durationSec = metadata.first,
                                                autoCoverBase64 = metadata.second
                                            )
                                        )
                                    )
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                mainHandler.removeCallbacks(progressRunnable)
                                // Fallback: copy original stream if device codec rejects transcoding
                                val fallbackSuccess = copyUriFallback(context, sourceUri, outputFile)
                                if (cont.isActive) {
                                    if (fallbackSuccess) {
                                        onProgress(1f)
                                        cont.resume(
                                            Result.success(
                                                CompressedVideoResult(
                                                    file = outputFile,
                                                    durationSec = metadata.first,
                                                    autoCoverBase64 = metadata.second
                                                )
                                            )
                                        )
                                    } else {
                                        cont.resume(Result.failure(exportException))
                                    }
                                }
                            }
                        })
                        .build()

                    transformerRef = transformer
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                    mainHandler.postDelayed(progressRunnable, 150L)

                    cont.invokeOnCancellation {
                        mainHandler.post {
                            mainHandler.removeCallbacks(progressRunnable)
                            transformer.cancel()
                        }
                    }
                } catch (e: Exception) {
                    val fallbackSuccess = copyUriFallback(context, sourceUri, outputFile)
                    if (cont.isActive) {
                        if (fallbackSuccess) {
                            onProgress(1f)
                            cont.resume(
                                Result.success(
                                    CompressedVideoResult(
                                        file = outputFile,
                                        durationSec = metadata.first,
                                        autoCoverBase64 = metadata.second
                                    )
                                )
                            )
                        } else {
                            cont.resume(Result.failure(e))
                        }
                    }
                }
            }
        }
    }

    private fun copyUriFallback(context: Context, uri: Uri, dest: File): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.exists() && dest.length() > 0L
    } catch (_: Exception) {
        false
    }

    private fun extractMetadata(context: Context, uri: Uri): Pair<Long, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val cover = frame?.let { encodeBitmapToDataUri(it, maxWidth = 360, quality = 65) }
            (durationMs / 1000L).coerceAtLeast(1L) to cover
        } catch (_: Exception) {
            15L to null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun compressImageUriToDataUri(context: Context, uri: Uri, maxWidth: Int = 360, quality: Int = 70): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val original = stream.use { BitmapFactory.decodeStream(it) } ?: return null
            encodeBitmapToDataUri(original, maxWidth, quality)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBitmapToDataUri(bitmap: Bitmap, maxWidth: Int = 360, quality: Int = 70): String? {
        return try {
            val ratio = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width.toFloat() else 1f
            val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) else bitmap
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            var bytes = out.toByteArray()
            if (bytes.size > 75_000) {
                out.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, 45, out)
                bytes = out.toByteArray()
            }
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
