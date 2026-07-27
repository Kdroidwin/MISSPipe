package us.shandian.giga.hls.transfer

import org.schabi.newpipe.DownloaderImpl
import us.shandian.giga.util.Utility
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class HttpTransferConnectionFactory(
    private val config: HttpTransferConfig,
) {
    @Throws(IOException::class)
    fun open(
        rawUrl: String,
        method: String,
        rangeStart: Long?,
        rangeEnd: Long?,
    ): HttpURLConnection {
        val requestUrl = stripTransferFragments(rawUrl)
        val cookie = extractCookie(rawUrl)
        val useMissAvHeaders = rawUrl.contains(MISSAV_MARKER) || isMissAvHost(requestUrl)
        val usePornhubHeaders = rawUrl.contains(PORNHUB_MARKER) || isPornhubHost(requestUrl)
        val useJavNoniHeaders = rawUrl.contains(JAVNONI_MARKER) || isJavNoniHost(requestUrl)
        val useJavSbHeaders = rawUrl.contains(JAVSB_MARKER) || isJavSbHost(requestUrl)
        val connection = URL(requestUrl).openConnection() as HttpURLConnection

        connection.instanceFollowRedirects = true
        connection.requestMethod = method
        connection.connectTimeout = config.connectTimeoutMillis
        connection.readTimeout = config.readTimeoutMillis
        connection.setRequestProperty("User-Agent", DownloaderImpl.USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Encoding", if (rangeStart == null) "identity" else "*")
        Utility.setRequestPropertyIfDownloadingBilibili(requestUrl, connection)
        if (useMissAvHeaders) {
            setMissAvRequestProperties(connection)
        }
        if (usePornhubHeaders) {
            setPornhubRequestProperties(connection)
        }
        if (useJavNoniHeaders) {
            setJavNoniRequestProperties(connection)
        }
        if (useJavSbHeaders) {
            setJavSbRequestProperties(connection)
        }
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie)
        }
        if (rangeStart != null) {
            connection.setRequestProperty("Range", formatRange(rangeStart, rangeEnd))
        }

        return connection
    }

    fun acceptsRanges(connection: HttpURLConnection): Boolean {
        return connection.getHeaderField("Accept-Ranges").equals("bytes", ignoreCase = true) ||
            connection.getHeaderField("Content-Range") != null
    }

    private fun formatRange(rangeStart: Long, rangeEnd: Long?): String {
        return buildString {
            append("bytes=")
            append(rangeStart)
            append('-')
            if (rangeEnd != null && rangeEnd >= 0) {
                append(rangeEnd)
            }
        }
    }

    private fun stripTransferFragments(rawUrl: String): String {
        return rawUrl.substringBefore("#cookie=")
            .substringBefore(MISSAV_MARKER)
            .substringBefore(PORNHUB_MARKER)
            .substringBefore(JAVNONI_MARKER)
            .substringBefore(JAVSB_MARKER)
    }

    private fun extractCookie(rawUrl: String): String? {
        val marker = "#cookie="
        val start = rawUrl.indexOf(marker)
        if (start < 0) {
            return null
        }
        val encoded = rawUrl.substring(start + marker.length).substringBefore('&')
        return URLDecoder.decode(encoded, Charsets.UTF_8.name())
    }

    private fun isMissAvHost(requestUrl: String): Boolean {
        val host = runCatching { URL(requestUrl).host.lowercase() }.getOrDefault("")
        return host.contains("missav") || host.contains("fourhoi")
    }

    private fun isPornhubHost(requestUrl: String): Boolean {
        val host = runCatching { URL(requestUrl).host.lowercase() }.getOrDefault("")
        return host.contains("pornhub") || host.contains("phncdn")
    }

    private fun isJavNoniHost(requestUrl: String): Boolean {
        val host = runCatching { URL(requestUrl).host.lowercase() }.getOrDefault("")
        return host.contains("jav-noni") || host.contains("luluvdo")
            || host.contains("lulucdn") || host.contains("tnmr")
    }

    private fun isJavSbHost(requestUrl: String): Boolean {
        val host = runCatching { URL(requestUrl).host.lowercase() }.getOrDefault("")
        return host == "jav.sb" || host.endsWith(".18av.mov")
    }

    private fun setMissAvRequestProperties(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", MISSAV_USER_AGENT)
        connection.setRequestProperty("Referer", "https://missav.ws/")
        connection.setRequestProperty("Origin", "https://missav.ws")
        connection.setRequestProperty("Accept-Language", "ja,en-US;q=0.8,en;q=0.6")
    }

    private fun setPornhubRequestProperties(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", PORNHUB_USER_AGENT)
        connection.setRequestProperty("Referer", "https://jp.pornhub.com/")
        connection.setRequestProperty("Origin", "https://jp.pornhub.com")
        connection.setRequestProperty("Accept-Language", "ja,en-US;q=0.8,en;q=0.6")
        connection.setRequestProperty(
            "Cookie",
            "age_verified=1; platform=pc; accessAgeDisclaimerPH=1; cookieConsent=3",
        )
    }

    private fun setJavNoniRequestProperties(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", JAVNONI_USER_AGENT)
        connection.setRequestProperty("Referer", "https://luluvdo.com/")
        connection.setRequestProperty("Origin", "https://luluvdo.com")
        connection.setRequestProperty("Accept-Language", "ja,en-US;q=0.8,en;q=0.6")
    }

    private fun setJavSbRequestProperties(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", JAVSB_USER_AGENT)
        connection.setRequestProperty("Referer", "https://jav.sb/")
        connection.setRequestProperty("Origin", "https://jav.sb")
        connection.setRequestProperty("Accept-Language", "ja,en-US;q=0.8,en;q=0.6")
    }

    companion object {
        private const val MISSAV_MARKER = "#missav=1"
        private const val PORNHUB_MARKER = "#pornhub=1"
        private const val JAVNONI_MARKER = "#javnoni=1"
        private const val JAVSB_MARKER = "#javsb=1"
        private const val MISSAV_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val PORNHUB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
        private const val JAVNONI_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
        private const val JAVSB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }
}

inline fun <T> HttpURLConnection.useTransferConnection(
    block: (HttpURLConnection) -> T,
): T {
    try {
        return block(this)
    } finally {
        disconnect()
    }
}
