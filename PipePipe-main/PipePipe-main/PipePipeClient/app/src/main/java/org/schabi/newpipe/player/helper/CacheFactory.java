package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import org.schabi.newpipe.player.datasource.YoutubeHttpDataSource;
import org.schabi.newpipe.DownloaderImpl;

import java.io.File;
import java.lang.reflect.Method;

/* package-private */ final class CacheFactory implements DataSource.Factory {
    private static final String TAG = CacheFactory.class.getSimpleName();

    private static final String CACHE_FOLDER_NAME = "exoplayer";
    private static final int CACHE_FLAGS = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR;
    private static SimpleCache cache;

    private final long maxFileSize;
    private final Context context;
    private final String userAgent;
    private final TransferListener transferListener;
    private final DataSource.Factory upstreamDataSourceFactory;

    public static class Builder {
        private final Context context;
        private final String userAgent;
        private final TransferListener transferListener;
        private DataSource.Factory upstreamDataSourceFactory;

        Builder(@NonNull final Context context,
                @NonNull final String userAgent,
                @NonNull final TransferListener transferListener) {
            this.context = context;
            this.userAgent = userAgent;
            this.transferListener = transferListener;
        }

        public void setUpstreamDataSourceFactory(
                @Nullable final DataSource.Factory upstreamDataSourceFactory) {
            this.upstreamDataSourceFactory = upstreamDataSourceFactory;
        }

        public CacheFactory build() {
            return new CacheFactory(context, userAgent, transferListener,
                    upstreamDataSourceFactory);
        }
    }

    private CacheFactory(@NonNull final Context context,
                         @NonNull final String userAgent,
                         @NonNull final TransferListener transferListener,
                         @Nullable final DataSource.Factory upstreamDataSourceFactory) {
        this.context = context;
        this.userAgent = userAgent;
        this.transferListener = transferListener;
        this.upstreamDataSourceFactory = upstreamDataSourceFactory;

        final File cacheDir = new File(context.getExternalCacheDir(), CACHE_FOLDER_NAME);
        if (!cacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdir();
        }

        if (cache == null) {
            final LeastRecentlyUsedCacheEvictor evictor
                    = new LeastRecentlyUsedCacheEvictor(PlayerHelper.getPreferredCacheSize());
            try {
                cache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
                Log.d(TAG, "initExoPlayerCache: cacheDir = " + cacheDir.getAbsolutePath());
            } catch (Exception e) {
                clearCacheFolderLock(cacheDir);
                cache = new SimpleCache(cacheDir, evictor, new StandaloneDatabaseProvider(context));
            }

        }

        maxFileSize = PlayerHelper.getPreferredFileSize();
    }

    @NonNull
    @Override
    public DataSource createDataSource() {

        final DataSource.Factory upstreamDataSourceFactoryToUse;
        if (upstreamDataSourceFactory == null) {
            upstreamDataSourceFactoryToUse = DownloaderImpl.getInstance()
                    .isDnsOverHttpsFallbackEnabled()
                    ? new OkHttpDataSource.Factory(DownloaderImpl.getInstance().getClient())
                            .setUserAgent(userAgent)
                    : new DefaultHttpDataSource.Factory().setUserAgent(userAgent);
        } else {
            if (upstreamDataSourceFactory instanceof DefaultHttpDataSource.Factory) {
                upstreamDataSourceFactoryToUse =
                        ((DefaultHttpDataSource.Factory) upstreamDataSourceFactory)
                                .setUserAgent(userAgent);
            } else if (upstreamDataSourceFactory instanceof YoutubeHttpDataSource.Factory) {
                upstreamDataSourceFactoryToUse =
                        ((YoutubeHttpDataSource.Factory) upstreamDataSourceFactory)
                                .setUserAgentForNonMobileStreams(userAgent);
            } else {
                upstreamDataSourceFactoryToUse = upstreamDataSourceFactory;
            }
        }

        final DefaultDataSource dataSource = new DefaultDataSource.Factory(context,
                upstreamDataSourceFactoryToUse)
                .setTransferListener(transferListener)
                .createDataSource();

        final FileDataSource fileSource = new FileDataSource();
        final CacheDataSink dataSink = new CacheDataSink(cache, maxFileSize);
        return new CacheDataSource(cache, dataSource, fileSource, dataSink, CACHE_FLAGS, null);
    }

    private static void clearCacheFolderLock(File cacheDir) {
        try {
            Method method = SimpleCache.class.getDeclaredMethod("unlockFolder", File.class);
            method.setAccessible(true);
            method.invoke(null, cacheDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
