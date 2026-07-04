package us.shandian.giga.get

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrMediaSegment
import org.schabi.newpipe.extractor.services.youtube.sabr.SabrSegmentRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormat
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import java.io.IOException
import java.io.OutputStream

internal class SabrSegmentWriter(
    private val session: YoutubeSabrSession,
    private val targets: List<SabrDownloadTarget>,
    private val outputs: Map<Int, OutputStream>,
    private val onBytesWritten: (SabrDownloadTarget, Long) -> Unit,
) {
    @Throws(IOException::class)
    fun writeDirectInitializations() {
        for (target in targets) {
            writeDirectInitializationIfAvailable(target, outputs.getValue(target.resourceIndex))
        }
    }

    fun observeWrittenInitializations() {
        for (target in targets) {
            val data = target.initializationData ?: continue
            if (!target.initializationObserved) {
                target.initializationObserved =
                    session.streamState.ingestInitializationData(target.format, data)
            }
        }
    }

    @Throws(IOException::class)
    fun drainCachedInitializations(): Boolean {
        var wroteInitialization = false
        for (target in targets) {
            if (target.initializationWritten) {
                continue
            }
            val request = SabrSegmentRequest.initialization(target.format)
            val segment = session.getCachedSegment(request) ?: continue
            writeInitializationSegment(target, outputs.getValue(target.resourceIndex), segment.data)
            session.discardCachedSegment(request)
            wroteInitialization = true
        }
        return wroteInitialization
    }

    @Throws(IOException::class)
    fun drainCachedSegments(): Boolean {
        var wroteSegment = false
        for (target in targets) {
            while (true) {
                val request = SabrSegmentRequest.media(target.format, target.nextWriteSequence)
                val segment = session.getCachedSegment(request) ?: break
                if (segment.header.isInitSegment) {
                    session.discardCachedSegment(request)
                    continue
                }
                writeMediaSegment(target, outputs.getValue(target.resourceIndex), segment)
                session.discardCachedSegment(request)
                wroteSegment = true
            }
        }
        return wroteSegment
    }

    @Throws(IOException::class)
    fun fetchMissingInitializations(localization: Localization): Boolean {
        return fetchInitializations(localization, onlyWhenMediaIsPending = true)
    }

    @Throws(IOException::class)
    fun fetchUnwrittenInitializations(localization: Localization): Boolean {
        return fetchInitializations(localization, onlyWhenMediaIsPending = false)
    }

    @Throws(IOException::class)
    private fun fetchInitializations(
        localization: Localization,
        onlyWhenMediaIsPending: Boolean,
    ): Boolean {
        var wroteInitialization = false
        for (target in targets) {
            if (target.initializationWritten ||
                (onlyWhenMediaIsPending && target.pending.isEmpty())
            ) {
                continue
            }
            if (writeDirectInitializationIfAvailable(target, outputs.getValue(target.resourceIndex))) {
                wroteInitialization = true
                continue
            }
            val request = SabrSegmentRequest.initialization(target.format)
            val segment = session.fetchSegment(request, localization)
            writeInitializationSegment(target, outputs.getValue(target.resourceIndex), segment.data)
            session.discardCachedSegment(request)
            wroteInitialization = true
        }
        return wroteInitialization
    }

    @Throws(IOException::class)
    private fun writeDirectInitializationIfAvailable(
        target: SabrDownloadTarget,
        output: OutputStream,
    ): Boolean {
        val data = fetchDirectInitializationData(target.format) ?: return false
        return writeInitializationSegment(target, output, data)
    }

    @Throws(IOException::class)
    private fun writeInitializationSegment(
        target: SabrDownloadTarget,
        output: OutputStream,
        data: ByteArray,
    ): Boolean {
        if (target.initializationWritten) {
            return false
        }
        writeToStorage(output, data)
        target.initializationWritten = true
        target.initializationData = data
        onBytesWritten(target, data.size.toLong())
        flushPendingMedia(target, output)
        return true
    }

    @Throws(IOException::class)
    private fun fetchDirectInitializationData(format: YoutubeSabrFormat): ByteArray? {
        val url = format.initializationUrl
        val start = format.initRangeStart
        val end = format.initRangeEnd
        if (url.isNullOrBlank() || start < 0 || end < start) {
            return null
        }
        val range = "bytes=$start-$end"
        val response = NewPipe.getDownloader().get(url, mapOf("Range" to listOf(range)))
        if (response.responseCode() != 206) {
            if (response.responseCode() >= 500) {
                throw IOException(
                    "SABR initialization request failed: HTTP ${response.responseCode()}",
                )
            }
            throw SabrDownloadException(
                SabrDownloadException.Reason.INITIALIZATION,
                "SABR download failed: could not fetch initialization for itag ${format.itag}"
                    + " (HTTP ${response.responseCode()})",
            )
        }
        val data = response.rawResponseBody()
        if (data == null || data.isEmpty()) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.INITIALIZATION,
                "SABR download failed: empty initialization for itag ${format.itag}",
            )
        }
        return data
    }

    @Throws(IOException::class)
    private fun writeMediaSegment(
        target: SabrDownloadTarget,
        output: OutputStream,
        segment: SabrMediaSegment,
    ) {
        val sequence = segment.header.sequenceNumber
        if (sequence < target.nextWriteSequence) {
            return
        }
        if (!target.initializationWritten) {
            cachePendingMedia(target, sequence, segment.data, "waiting for initialization")
            return
        }
        if (sequence > target.nextWriteSequence) {
            cachePendingMedia(target, sequence, segment.data, "waiting for sequence ${target.nextWriteSequence}")
            return
        }
        writeMediaBytes(target, output, segment.data)
        flushPendingMedia(target, output)
    }

    private fun flushPendingMedia(target: SabrDownloadTarget, output: OutputStream) {
        while (true) {
            val pending = target.pending.remove(target.nextWriteSequence) ?: return
            target.pendingBytes = (target.pendingBytes - pending.size).coerceAtLeast(0)
            writeMediaBytes(target, output, pending)
        }
    }

    private fun cachePendingMedia(
        target: SabrDownloadTarget,
        sequence: Int,
        data: ByteArray,
        reason: String,
    ) {
        val previous = target.pending.put(sequence, data)
        if (previous != null) {
            target.pendingBytes -= previous.size.toLong()
        }
        target.pendingBytes += data.size.toLong()
        if (target.pending.size > MAX_PENDING_SEGMENTS
            || target.pendingBytes > MAX_PENDING_BYTES
        ) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.STALLED,
                "SABR download stalled while writing itag ${target.format.itag}: $reason"
                    + " (${target.pending.size} pending segments, ${target.pendingBytes} bytes)",
            )
        }
    }

    private fun writeMediaBytes(target: SabrDownloadTarget, output: OutputStream, data: ByteArray) {
        writeToStorage(output, data)
        target.nextWriteSequence++
        onBytesWritten(target, data.size.toLong())
    }

    private fun writeToStorage(output: OutputStream, data: ByteArray) {
        try {
            output.write(data)
        } catch (error: IOException) {
            throw SabrDownloadException(
                SabrDownloadException.Reason.STORAGE,
                "SABR download failed: could not write temporary media",
                error,
            )
        }
    }

    private companion object {
        private const val MAX_PENDING_SEGMENTS = 64
        private const val MAX_PENDING_BYTES = 24L * 1024L * 1024L
    }
}
