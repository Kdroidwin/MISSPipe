package org.schabi.newpipe.player.helper;

import android.net.Uri;

import androidx.annotation.Nullable;

import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.extractor.metadata.icy.IcyHeaders;
import com.google.common.base.Predicate;
import org.schabi.newpipe.DownloaderImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * media3 makes {@link DefaultHttpDataSource}'s constructor private, so we can no longer subclass it.
 * We wrap one instead and only intercept {@link #open(DataSpec)} to drop the ICY metadata-enable
 * header. Subclasses (NiconicoLive) override {@code open} and call {@code super.open}.
 */
public class PurifiedHttpDataSource implements HttpDataSource {
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS =
            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;
    public static final int DEFAULT_READ_TIMEOUT_MILLIS =
            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS;

    protected final HttpDataSource delegate;

    protected PurifiedHttpDataSource(final HttpDataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public long open(final DataSpec dataSpec) throws HttpDataSourceException {
        final Map<String, String> headers = new HashMap<>(dataSpec.httpRequestHeaders);
        headers.remove(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME);
        return delegate.open(dataSpec.withRequestHeaders(headers));
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length)
            throws HttpDataSourceException {
        return delegate.read(buffer, offset, length);
    }

    @Override
    public void addTransferListener(final TransferListener transferListener) {
        delegate.addTransferListener(transferListener);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return delegate.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return delegate.getResponseHeaders();
    }

    @Override
    public void close() throws HttpDataSourceException {
        delegate.close();
    }

    @Override
    public void setRequestProperty(final String name, final String value) {
        delegate.setRequestProperty(name, value);
    }

    @Override
    public void clearRequestProperty(final String name) {
        delegate.clearRequestProperty(name);
    }

    @Override
    public void clearAllRequestProperties() {
        delegate.clearAllRequestProperties();
    }

    @Override
    public int getResponseCode() {
        return delegate.getResponseCode();
    }

    public static class Factory implements HttpDataSource.Factory {
        protected final DefaultHttpDataSource.Factory inner = new DefaultHttpDataSource.Factory();
        protected final OkHttpDataSource.Factory okHttp = new OkHttpDataSource.Factory(
                DownloaderImpl.getInstance().getClient());

        @Override
        public final Factory setDefaultRequestProperties(
                final Map<String, String> defaultRequestProperties) {
            inner.setDefaultRequestProperties(defaultRequestProperties);
            okHttp.setDefaultRequestProperties(defaultRequestProperties);
            return this;
        }

        public Factory setUserAgent(@Nullable final String userAgent) {
            inner.setUserAgent(userAgent);
            okHttp.setUserAgent(userAgent);
            return this;
        }

        public Factory setConnectTimeoutMs(final int connectTimeoutMs) {
            inner.setConnectTimeoutMs(connectTimeoutMs);
            return this;
        }

        public Factory setReadTimeoutMs(final int readTimeoutMs) {
            inner.setReadTimeoutMs(readTimeoutMs);
            return this;
        }

        public Factory setAllowCrossProtocolRedirects(final boolean allowCrossProtocolRedirects) {
            inner.setAllowCrossProtocolRedirects(allowCrossProtocolRedirects);
            return this;
        }

        public Factory setContentTypePredicate(@Nullable final Predicate<String> predicate) {
            inner.setContentTypePredicate(predicate);
            okHttp.setContentTypePredicate(predicate);
            return this;
        }

        public Factory setTransferListener(@Nullable final TransferListener transferListener) {
            inner.setTransferListener(transferListener);
            okHttp.setTransferListener(transferListener);
            return this;
        }

        public Factory setKeepPostFor302Redirects(final boolean keepPostFor302Redirects) {
            inner.setKeepPostFor302Redirects(keepPostFor302Redirects);
            return this;
        }

        @Override
        public PurifiedHttpDataSource createDataSource() {
            return new PurifiedHttpDataSource(DownloaderImpl.getInstance()
                    .isDnsOverHttpsFallbackEnabled()
                    ? okHttp.createDataSource() : inner.createDataSource());
        }
    }
}
