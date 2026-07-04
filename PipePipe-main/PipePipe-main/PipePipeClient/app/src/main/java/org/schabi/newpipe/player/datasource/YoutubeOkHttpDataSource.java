package org.schabi.newpipe.player.datasource;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isAndroidStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isIosStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isTvHtml5SimplyEmbeddedPlayerStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import com.google.common.net.HttpHeaders;

import org.schabi.newpipe.DownloaderImpl;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class YoutubeOkHttpDataSource {
    private YoutubeOkHttpDataSource() {
    }

    public static final class Factory implements DataSource.Factory {
        private static final AtomicLong REQUEST_NUMBER = new AtomicLong();
        private final boolean rangeParameterEnabled;
        private final boolean rnParameterEnabled;

        public Factory(final boolean rangeParameterEnabled,
                       final boolean rnParameterEnabled) {
            this.rangeParameterEnabled = rangeParameterEnabled;
            this.rnParameterEnabled = rnParameterEnabled;
        }

        @Override
        public DataSource createDataSource() {
            final OkHttpClient client = DownloaderImpl.getInstance().getClient().newBuilder()
                    .addInterceptor(new YoutubeInterceptor())
                    .build();
            return new OkHttpDataSource.Factory(client)
                    .setUserAgent(DownloaderImpl.USER_AGENT)
                    .createDataSource();
        }

        private final class YoutubeInterceptor implements Interceptor {
            @Override
            public Response intercept(final Chain chain) throws IOException {
                final Request request = chain.request();
                final String requestUrl = request.url().toString();
                final boolean videoPlayback = request.url().encodedPath()
                        .startsWith("/videoplayback");
                final HttpUrl.Builder urlBuilder = request.url().newBuilder();
                final Request.Builder requestBuilder = request.newBuilder();

                if (videoPlayback && rnParameterEnabled
                        && request.url().queryParameter("rn") == null) {
                    urlBuilder.addQueryParameter("rn",
                            Long.toString(REQUEST_NUMBER.getAndIncrement()));
                }
                if (videoPlayback && rangeParameterEnabled) {
                    final String range = request.header(HttpHeaders.RANGE);
                    if (range != null && range.startsWith("bytes=")) {
                        urlBuilder.addQueryParameter("range", range.substring(6));
                        requestBuilder.removeHeader(HttpHeaders.RANGE);
                    }
                }

                requestBuilder.url(urlBuilder.build()).header(HttpHeaders.TE, "trailers");
                if (isWebStreamingUrl(requestUrl)
                        || isTvHtml5SimplyEmbeddedPlayerStreamingUrl(requestUrl)) {
                    requestBuilder.header(HttpHeaders.ORIGIN, "https://www.youtube.com")
                            .header(HttpHeaders.REFERER, "https://www.youtube.com")
                            .header(HttpHeaders.SEC_FETCH_DEST, "empty")
                            .header(HttpHeaders.SEC_FETCH_MODE, "cors")
                            .header(HttpHeaders.SEC_FETCH_SITE, "cross-site");
                }
                if (isAndroidStreamingUrl(requestUrl)) {
                    requestBuilder.header(HttpHeaders.USER_AGENT, getAndroidUserAgent(null))
                            .method("POST", request.body() == null
                                    ? RequestBody.create(null, new byte[0]) : request.body());
                } else if (isIosStreamingUrl(requestUrl)) {
                    requestBuilder.header(HttpHeaders.USER_AGENT, getIosUserAgent(null))
                            .method("POST", request.body() == null
                                    ? RequestBody.create(null, new byte[0]) : request.body());
                }
                return chain.proceed(requestBuilder.build());
            }
        }
    }
}
