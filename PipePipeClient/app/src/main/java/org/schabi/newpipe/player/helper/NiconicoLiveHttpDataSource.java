package org.schabi.newpipe.player.helper;

import androidx.annotation.Nullable;

import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import com.google.common.base.Predicate;
import org.schabi.newpipe.DownloaderImpl;

import java.util.HashMap;
import java.util.Map;

public class NiconicoLiveHttpDataSource extends PurifiedHttpDataSource {
    private static final long FETCH_INTERVAL = 50000000; //disable
    private final String liveUrl;
    private static final Map<String, Long> fetchHistory = new HashMap<>();
    private static String currentKey;
    private boolean isFetching = false;

    public static class Factory implements HttpDataSource.Factory {
        private final DefaultHttpDataSource.Factory inner = new DefaultHttpDataSource.Factory();
        private final OkHttpDataSource.Factory okHttp = new OkHttpDataSource.Factory(
                DownloaderImpl.getInstance().getClient());
        private final String url;

        /** Creates an instance. */
        public Factory(final String url) {
            if (url.equals("")) {
                throw new RuntimeException(
                        "Build NicoNico live source failed. This should never happen.");
            }
            this.url = url;
        }

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
        public NiconicoLiveHttpDataSource createDataSource() {
            return new NiconicoLiveHttpDataSource(DownloaderImpl.getInstance()
                    .isDnsOverHttpsFallbackEnabled()
                    ? okHttp.createDataSource() : inner.createDataSource(), url);
        }
    }

    NiconicoLiveHttpDataSource(final HttpDataSource delegate, final String liveUrl) {
        super(delegate);
        this.liveUrl = liveUrl;
    }

    @Override
    public long open(final DataSpec dataSpec) throws HttpDataSourceException {
//        String fetchUrl = dataSpec.uri.toString();
//        int type = 0;
//        List<String> anonStrings = Arrays.asList("anonymous-user-", "anonymous_user_", "ht2_nicolive=");
////        System.out.println("Start fetching: " + new Date().toString() + " " + fetchUrl + " isFetching: " + isFetching);
////        System.out.println("Current key: " + currentKey + " " + fetchHistory.entrySet().toString());
//        String fetchKey;
//        if (fetchUrl.contains(anonStrings.get(0))){
//            fetchKey = fetchUrl.split(anonStrings.get(0))[1].split("&")[0];
//        } else if (fetchUrl.contains(anonStrings.get(1))){
//            fetchKey = fetchUrl.split(anonStrings.get(1))[1].split("&")[0];
//            type = 1;
//        } else{
//            fetchKey = fetchUrl.split(anonStrings.get(2))[1].split("&")[0];
//            type = 2;
//        }
//
//        if(currentKey == null){
//            currentKey = fetchKey;
//        }
//        Long currentTime = new Date().getTime();
//        if(!fetchHistory.containsKey(currentKey)){
//            fetchHistory.put(currentKey, currentTime);
//        } else if (!isFetching && currentTime - fetchHistory.get(currentKey) > FETCH_INTERVAL && !fetchUrl.contains("playlist.m3u8")) {
//            // start a new thread and fetch the new key
////            int finalType = type;
////            new Thread(() -> {
////                try {
//////                    System.out.println("Start fetching new key: " + new Date().toString());
////                    isFetching = true;
////                    currentKey = PlayerDataSource.getNicoLiveUrl(liveUrl).split(anonStrings.get(finalType))[1].split("&")[0];
////                    fetchHistory.put(String.valueOf(currentKey), currentTime);
////                    isFetching = false;
//////                    System.out.println("End fetching new key: " + new Date().toString());
////                } catch (ParsingException | IOException | ReCaptchaException | JsonParserException e) {
////                    throw new RuntimeException(e);
////                }
////            }).start();
//        }
//        String newUrl = fetchUrl.replace(fetchKey, currentKey);
//        System.out.println("End fetching: " + new Date().toString() + " " + newUrl);
        return super.open(dataSpec);
    }
}
