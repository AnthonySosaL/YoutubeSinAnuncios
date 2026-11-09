package com.vancedclean.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Une video y audio en MP4 y convierte audio a MP3, todo con las APIs de Android (sin FFmpeg). */
object MediaTools {

    /** Junta una pista de video (H.264) y una de audio (AAC) en un MP4, sin recodificar. */
    fun muxMp4(video: File, audio: File, output: File) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val aEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        try {
            val vTrack = findTrack(vEx, "video/") ?: error("El archivo de video no tiene imagen")
            val aTrack = findTrack(aEx, "audio/") ?: error("El archivo de audio no tiene sonido")
            vEx.selectTrack(vTrack)
            aEx.selectTrack(aTrack)
            val outV = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val outA = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()
            copySamples(vEx, muxer, outV)
            copySamples(aEx, muxer, outA)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vEx.release()
            aEx.release()
        }
    }

    private fun copySamples(ex: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = ex.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, ex.sampleTime, toCodecFlags(ex.sampleFlags))
            muxer.writeSampleData(track, buffer, info)
            ex.advance()
        }
    }

    @Suppress("WrongConstant")
    private fun toCodecFlags(extractorFlags: Int): Int =
        if (extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

    private fun findTrack(ex: MediaExtractor, prefix: String): Int? =
        (0 until ex.trackCount).firstOrNull {
            ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
        }

    /**
     * Decodifica cualquier audio que entienda Android (AAC/M4A, Opus/WebM, o el audio de un MP4)
     * y lo codifica a MP3 a [bitrateKbps]. Añade una etiqueta ID3 con título y artista.
     */
    fun toMp3(
        input: File,
        output: File,
        bitrateKbps: Int,
        title: String,
        artist: String,
        album: String = "",
        cover: ByteArray? = null,
        onProgress: (Float) -> Unit,
    ) {
        val ex = MediaExtractor().apply { setDataSource(input.absolutePath) }
        val track = findTrack(ex, "audio/") ?: run { ex.release(); error("El archivo no tiene audio") }
        ex.selectTrack(track)
        val inFormat = ex.getTrackFormat(track)
        val durationUs = if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFormat, null, null, 0)
        codec.start()

        val out = BufferedOutputStream(FileOutputStream(output), 256 * 1024)
        out.write(id3v2(title, artist, album, cover))
        var encoder: Mp3Encoder? = null
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var pcm = ShortArray(0)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val enc = encoder ?: Mp3Encoder(minOf(channels, 2), sampleRate, bitrateKbps, out)
                                .also { encoder = it }
                            val buf = codec.getOutputBuffer(outIndex)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val count = shorts.remaining()
                            if (pcm.size < count) pcm = ShortArray(count)
                            shorts.get(pcm, 0, count)
                            val usable = if (channels > 2) downmix(pcm, count, channels) else count
                            enc.encode(pcm, usable)
                            if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            encoder?.finish() ?: error("No se pudo decodificar el audio")
        } finally {
            out.close()
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
    }

    /** Más de 2 canales → estéreo (toma los dos primeros), en el mismo arreglo. */
    private fun downmix(pcm: ShortArray, count: Int, channels: Int): Int {
        val frames = count / channels
        for (i in 0 until frames) {
            pcm[i * 2] = pcm[i * channels]
            pcm[i * 2 + 1] = pcm[i * channels + 1]
        }
        return frames * 2
    }

    /** Etiqueta ID3v2.3: título, artista, álbum (marca "Zaza · YouTube") y portada JPEG. */
    private fun id3v2(title: String, artist: String, album: String, cover: ByteArray?): ByteArray {
        fun frame(id: String, text: String): ByteArray {
            val body = byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
            val size = body.size
            return id.toByteArray() + byteArrayOf(
                (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte(), 0, 0
            ) + body
        }
        fun rawFrame(id: String, body: ByteArray): ByteArray {
            val size = body.size
            return id.toByteArray() + byteArrayOf(
                (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte(), 0, 0
            ) + body
        }
        var frames = frame("TIT2", title) + frame("TPE1", artist)
        if (album.isNotBlank()) frames += frame("TALB", album)
        if (cover != null && cover.isNotEmpty()) {
            // APIC: codificación 0, MIME, tipo 3 (portada), descripción vacía, imagen.
            frames += rawFrame("APIC", byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + cover)
        }
        val s = frames.size
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0,
            ((s shr 21) and 0x7F).toByte(), ((s shr 14) and 0x7F).toByte(),
            ((s shr 7) and 0x7F).toByte(), (s and 0x7F).toByte())
        return header + frames
    }
}
