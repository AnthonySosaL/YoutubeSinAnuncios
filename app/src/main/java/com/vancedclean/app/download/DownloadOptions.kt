package com.vancedclean.app.download

import com.vancedclean.app.data.NewPipeSetup
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

/** Una calidad de video para MP4: video (H.264) y, si viene aparte, su audio AAC. */
data class VideoChoice(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?,
    val approxBytes: Long,
)

/** Una calidad de audio de origen y el bitrate MP3 al que se convertirá. */
data class AudioChoice(
    val label: String,
    val url: String,
    val sourceKbps: Int,
    val mp3Kbps: Int,
    val approxBytes: Long,
)

/** Todo lo que se puede descargar de un enlace. */
data class DownloadOptions(
    val source: DlSource,
    val pageUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val videos: List<VideoChoice>,
    val audios: List<AudioChoice>,
)

object DownloadResolver {

    fun isInstagram(url: String) = Regex("""(^|\.)instagram\.com/""").containsMatchIn(url) || url.contains("instagr.am/")

    fun isYouTube(url: String) = Regex("""(youtube\.com|youtu\.be)/""").containsMatchIn(url)

    /** Busca las calidades disponibles del enlace (YouTube o Instagram). */
    fun resolve(url: String): DownloadOptions = when {
        isInstagram(url) -> InstagramResolver.resolve(url)
        isYouTube(url) -> youtube(url)
        else -> error("Pega un enlace de YouTube o de Instagram")
    }

    private fun youtube(url: String): DownloadOptions {
        NewPipeSetup.ensure()
        val info = StreamInfo.getInfo(NewPipe.getService(0), url)
        val m4a = info.audioStreams
            .filter { it.content.isNotBlank() && it.format == MediaFormat.M4A && isOriginal(it) }
            .maxByOrNull { it.averageBitrate }

        // Para MP4 solo H.264 + AAC: es lo que Android junta sin recodificar y se ve en cualquier lado.
        val adaptive = if (m4a == null) emptyList() else info.videoOnlyStreams
            .filter { it.content.isNotBlank() && it.format == MediaFormat.MPEG_4 && isAvc(it) }
            .groupBy { height(it) }
            .map { (h, list) ->
                val best = list.maxByOrNull { it.fps }!!
                VideoChoice(label(best), h, best.content, m4a.content, size(best) + size(m4a))
            }
        val muxed = info.videoStreams
            .filter { !it.isVideoOnly && it.content.isNotBlank() && it.format == MediaFormat.MPEG_4 }
            .map { VideoChoice(label(it), height(it), it.content, null, size(it)) }
        val videos = (adaptive + muxed).groupBy { it.height }
            .map { (_, l) -> l.firstOrNull { it.audioUrl == null } ?: l.first() }
            .filter { it.height > 0 }
            .sortedByDescending { it.height }

        val audios = info.audioStreams
            .filter { it.content.isNotBlank() && isOriginal(it) }
            .sortedByDescending { it.averageBitrate }
            .distinctBy { "${it.format?.name}-${kbps(it)}" }
            .map {
                val k = kbps(it)
                val codec = if (it.format == MediaFormat.M4A) "AAC" else "Opus"
                val mp3 = when { k >= 150 -> 256; k >= 100 -> 192; k >= 60 -> 128; else -> 96 }
                AudioChoice("$codec $k kbps → MP3 $mp3 kbps", it.content, k, mp3,
                    if (info.duration > 0) info.duration * mp3 * 125 else 0)
            }

        return DownloadOptions(
            source = DlSource.YOUTUBE,
            pageUrl = url,
            title = info.name.orEmpty(),
            author = info.uploaderName.orEmpty(),
            thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url,
            durationSec = info.duration,
            videos = videos,
            audios = audios,
        )
    }

    private fun isOriginal(s: AudioStream) = s.audioTrackType == null || s.audioTrackType?.name == "ORIGINAL"

    private fun isAvc(s: VideoStream): Boolean {
        val c = s.codec.orEmpty().lowercase(Locale.US)
        return c.isBlank() || c.startsWith("avc")
    }

    private fun height(s: VideoStream) = s.resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    private fun label(s: VideoStream) = height(s).let { h -> if (s.fps > 30) "${h}p${s.fps}" else "${h}p" }

    private fun kbps(s: AudioStream) = (s.averageBitrate.takeIf { it > 0 } ?: s.bitrate).let { if (it > 1000) it / 1000 else it }

    private fun size(s: org.schabi.newpipe.extractor.stream.Stream): Long =
        runCatching { (s as? VideoStream)?.itagItem?.contentLength ?: (s as? AudioStream)?.itagItem?.contentLength }
            .getOrNull()?.takeIf { it > 0 } ?: 0L
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
}
