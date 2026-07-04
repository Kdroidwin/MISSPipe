package us.shandian.giga.hls.manifest

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist as ExoHlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.Segment as ExoHlsSegment
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.SegmentBase as ExoHlsSegmentBase
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist as ExoHlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser as ExoHlsPlaylistParser
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI

class HlsPlaylistParser {
    @Throws(IOException::class)
    fun parse(sourceUrl: String, body: String): HlsPlaylist {
        val input = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        return when (val playlist = ExoHlsPlaylistParser().parse(Uri.parse(sourceUrl), input)) {
            is ExoHlsMediaPlaylist -> playlist.toInternalMediaPlaylist(sourceUrl)
            is ExoHlsMultivariantPlaylist -> playlist.toInternalMasterPlaylist(sourceUrl)
            else -> throw IOException("Unsupported HLS playlist type: ${playlist.javaClass.name}")
        }
    }

    private fun ExoHlsMultivariantPlaylist.toInternalMasterPlaylist(sourceUrl: String): HlsMasterPlaylist {
        return HlsMasterPlaylist(
            sourceUrl = sourceUrl,
            variants = variants.map { variant ->
                val format = variant.format
                HlsVariant(
                    url = resolveUrl(sourceUrl, variant.url.toString()),
                    bandwidth = positiveLong(format.peakBitrate) ?: positiveLong(format.bitrate),
                    averageBandwidth = positiveLong(format.averageBitrate),
                    codecs = format.codecs,
                    resolution = resolutionOf(format),
                    frameRate = format.frameRate.takeIf { it > 0 }?.toDouble(),
                    audioGroupId = variant.audioGroupId,
                )
            },
        )
    }

    private fun ExoHlsMediaPlaylist.toInternalMediaPlaylist(sourceUrl: String): HlsMediaPlaylist {
        val initSegment = segments
            .firstOrNull { it.initializationSegment != null }
            ?.initializationSegment
            ?.toInternalInitSegment(sourceUrl)
        return HlsMediaPlaylist(
            sourceUrl = sourceUrl,
            targetDurationSeconds = secondsOf(targetDurationUs),
            mediaSequence = mediaSequence,
            segments = segments.map { it.toInternalSegment(sourceUrl) },
            isEndList = hasEndTag,
            initSegment = initSegment,
            hasEncryption = hasAes128Encryption(),
            hasUnsupportedEncryption = hasUnsupportedEncryption(),
            hasDiscontinuity = hasDiscontinuity(),
        )
    }

    private fun ExoHlsSegment.toInternalSegment(sourceUrl: String): HlsSegment {
        return HlsSegment(
            url = resolveUrl(sourceUrl, url),
            durationSeconds = secondsOf(durationUs),
            title = title.ifBlank { null },
            byteRange = byteRange(),
            discontinuity = relativeDiscontinuitySequence > 0,
            encryptionKey = encryptionKey(sourceUrl),
        )
    }

    private fun ExoHlsSegment.toInternalInitSegment(sourceUrl: String): HlsInitSegment {
        return HlsInitSegment(
            url = resolveUrl(sourceUrl, url),
            byteRange = byteRange(),
            encryptionKey = encryptionKey(sourceUrl),
        )
    }

    private fun ExoHlsMediaPlaylist.hasAes128Encryption(): Boolean {
        return segments.any { it.fullSegmentEncryptionKeyUri != null }
            || segments.any { it.initializationSegment?.fullSegmentEncryptionKeyUri != null }
    }

    private fun ExoHlsMediaPlaylist.hasUnsupportedEncryption(): Boolean {
        return protectionSchemes != null || tags.any { tag ->
            if (!tag.startsWith(EXT_X_KEY)) {
                false
            } else {
                val method = keyMethod(tag)
                method != null
                    && !method.equals("NONE", ignoreCase = true)
                    && !method.equals("AES-128", ignoreCase = true)
            }
        }
    }

    private fun ExoHlsMediaPlaylist.hasDiscontinuity(): Boolean {
        return tags.any { it == EXT_X_DISCONTINUITY }
            || segments.any { it.relativeDiscontinuitySequence > 0 }
    }

    private fun ExoHlsSegmentBase.byteRange(): HlsByteRange? {
        if (byteRangeLength <= 0) {
            return null
        }
        return HlsByteRange(byteRangeLength, byteRangeOffset.takeIf { it >= 0 })
    }

    private fun ExoHlsSegmentBase.encryptionKey(sourceUrl: String): HlsEncryptionKey? {
        val keyUri = fullSegmentEncryptionKeyUri ?: return null
        return HlsEncryptionKey(
            method = "AES-128",
            url = resolveUrl(sourceUrl, keyUri),
            iv = encryptionIV,
        )
    }

    private fun keyMethod(tag: String): String? {
        return Regex("(?:^|,)METHOD=([^,]+)")
            .find(tag.substringAfter(':', ""))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"')
    }

    private fun resolutionOf(format: Format): String? {
        return if (format.width > 0 && format.height > 0) {
            "${format.width}x${format.height}"
        } else {
            null
        }
    }

    private fun positiveLong(value: Int): Long? {
        return value.takeIf { it > 0 }?.toLong()
    }

    private fun secondsOf(valueUs: Long): Double? {
        return valueUs.takeIf { it != C.TIME_UNSET && it >= 0 }?.let { it / 1_000_000.0 }
    }

    private fun resolveUrl(sourceUrl: String, reference: String): String {
        val missAvMarked = sourceUrl.contains(MISSAV_MARKER)
        val resolved = URI(sourceUrl.substringBefore(MISSAV_MARKER)).resolve(reference).toString()
        return if (missAvMarked && !resolved.contains(MISSAV_MARKER)) {
            resolved.substringBefore('#') + MISSAV_MARKER
        } else {
            resolved
        }
    }

    private companion object {
        const val MISSAV_MARKER = "#missav=1"
        const val EXT_X_KEY = "#EXT-X-KEY:"
        const val EXT_X_DISCONTINUITY = "#EXT-X-DISCONTINUITY"
    }
}
