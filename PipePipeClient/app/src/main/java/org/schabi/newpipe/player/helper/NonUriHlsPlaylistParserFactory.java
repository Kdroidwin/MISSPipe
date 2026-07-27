package org.schabi.newpipe.player.helper;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory;
import androidx.media3.exoplayer.upstream.ParsingLoadable;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link HlsPlaylistParserFactory} for non-URI HLS sources.
 */
public final class NonUriHlsPlaylistParserFactory implements HlsPlaylistParserFactory {

    private final HlsPlaylist hlsPlaylist;

    public NonUriHlsPlaylistParserFactory(final HlsPlaylist hlsPlaylist) {
        this.hlsPlaylist = hlsPlaylist;
    }

    private final class NonUriHlsPlayListParser implements ParsingLoadable.Parser<HlsPlaylist> {

        @Override
        public HlsPlaylist parse(final Uri uri,
                                 final InputStream inputStream) throws IOException {
            return hlsPlaylist;
        }
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        return new NonUriHlsPlayListParser();
    }

    @NonNull
    @Override
    public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
            @NonNull final HlsMultivariantPlaylist multivariantPlaylist,
            @Nullable final HlsMediaPlaylist previousMediaPlaylist) {
        return new NonUriHlsPlayListParser();
    }
}
