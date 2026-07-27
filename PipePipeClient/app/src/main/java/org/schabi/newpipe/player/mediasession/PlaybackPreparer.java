package org.schabi.newpipe.player.mediasession;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Replacement for ExoPlayer's removed {@code MediaSessionConnector.PlaybackPreparer}: lets the
 * media browser (Android Auto) turn a media id / search / uri into actual playback.
 */
public interface PlaybackPreparer {
    long getSupportedPrepareActions();

    void onPrepare(boolean playWhenReady);

    void onPrepareFromMediaId(String mediaId, boolean playWhenReady, @Nullable Bundle extras);

    void onPrepareFromSearch(String query, boolean playWhenReady, @Nullable Bundle extras);

    void onPrepareFromUri(Uri uri, boolean playWhenReady, @Nullable Bundle extras);
}
