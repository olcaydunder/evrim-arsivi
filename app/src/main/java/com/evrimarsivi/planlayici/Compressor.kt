package com.evrimarsivi.planlayici

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Telefondaki videoyu yükleme sınırına (GitHub: dosya başına 100 MB) sığacak şekilde küçültür ve kapak üretir. */
@androidx.annotation.OptIn(UnstableApi::class)
object Compressor {
    const val LIMIT = 90L * 1024 * 1024          // bunun üstü yüklenemez
    const val COMPRESS_ABOVE = 60L * 1024 * 1024 // bunun üstü küçültülür

    data class Info(val size: Long, val durationMs: Long, val width: Int, val height: Int, val mime: String)

    fun info(c: Context, uri: Uri): Info {
        var size = -1L
        c.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst() && !cur.isNull(0)) size = cur.getLong(0)
        }
        if (size <= 0) size = c.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(c, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            return Info(size, d, w, h, c.contentResolver.getType(uri) ?: "video/mp4")
        } finally { r.release() }
    }

    /** Videonun 0,5. saniyesinden JPEG kapak. */
    fun cover(c: Context, uri: Uri): ByteArray? = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(c, uri)
            val bmp = r.getScaledFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 1280)
                ?: r.frameAtTime
            bmp?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
        } finally { r.release() }
    } catch (e: Exception) { null }

    /** Kısa kenarı en fazla 1080 olacak, toplam boyut ~85 MB'ı aşmayacak şekilde H.264'e çevirir. */
    fun compress(c: Context, uri: Uri, inf: Info, out: File) {
        val sec = (inf.durationMs / 1000.0).coerceAtLeast(1.0)
        val bitrate = ((85.0 * 1024 * 1024 * 8 / sec) - 160_000).toInt().coerceIn(1_200_000, 8_000_000)
        val short = minOf(inf.width, inf.height).coerceAtLeast(1)
        val scale = minOf(1.0, 1080.0 / short)
        val tw = ((inf.width * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val th = ((inf.height * scale).toInt() / 2 * 2).coerceAtLeast(2)

        val latch = CountDownLatch(1)
        var error: Exception? = null
        Handler(Looper.getMainLooper()).post {
            try {
                val enc = DefaultEncoderFactory.Builder(c.applicationContext)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .build()
                val t = Transformer.Builder(c.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(enc)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { latch.countDown() }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            error = exportException; latch.countDown()
                        }
                    })
                    .build()
                val effects = if (tw > 0 && th > 0 && scale < 1.0)
                    Effects(listOf(), listOf<Effect>(Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT)))
                else Effects.EMPTY
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(effects).build()
                t.start(item, out.path)
            } catch (e: Exception) {
                error = e; latch.countDown()
            }
        }
        if (!latch.await(45, TimeUnit.MINUTES)) throw RuntimeException("Küçültme zaman aşımına uğradı")
        error?.let { throw it }
    }
}
