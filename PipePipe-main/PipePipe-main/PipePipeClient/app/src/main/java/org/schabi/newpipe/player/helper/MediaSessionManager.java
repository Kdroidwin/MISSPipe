package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.NotificationUtil;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlaybackPreparer;
import org.schabi.newpipe.player.playback.PlayerMediaSession;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.schabi.newpipe.player.PlayerService.ACTION_CHANGE_PLAY_MODE;
import static org.schabi.newpipe.player.PlayerService.ACTION_CLOSE;

public class MediaSessionManager {
    private static final String TAG = MediaSessionManager.class.getSimpleName();
    public static final boolean DEBUG = MainActivity.DEBUG;

    private static final int MAX_QUEUE_SIZE = 10;

    private static final long PLAYBACK_ACTIONS = PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
            | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final Player exoPlayer;
    @NonNull
    private final MediaSessionCallback callback;
    @Nullable
    private final PlaybackPreparer playbackPreparer;
    private final boolean isExternalSession;

    @Nullable
    private PlaybackStateCompat.CustomAction errorAction;

    private org.schabi.newpipe.player.Player player;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback) {
        this(context, player, callback, null);
    }

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback,
                               @Nullable final MediaSessionCompat existingSession) {
        this(context, player, callback, existingSession, null);
    }

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback,
                               @Nullable final MediaSessionCompat existingSession,
                               @Nullable final PlaybackPreparer playbackPreparer) {
        this.exoPlayer = player;
        this.callback = callback;
        this.playbackPreparer = playbackPreparer;
        mediaSession = existingSession != null ? existingSession
                : new MediaSessionCompat(context, TAG);
        isExternalSession = existingSession != null;
        mediaSession.setActive(true);
        mediaSession.setCallback(sessionCallback);

        exoPlayer.addListener(playerListener);
        updatePlaybackState();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull final Player p, @NonNull final Player.Events events) {
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                updatePlaybackState();
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED)) {
                updateMetadata();
                publishQueue();
            }
        }
    };

    private final MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            callback.play();
        }

        @Override
        public void onPause() {
            callback.pause();
        }

        @Override
        public void onSkipToNext() {
            callback.playNext();
        }

        @Override
        public void onSkipToPrevious() {
            callback.playPrevious();
        }

        @Override
        public void onSkipToQueueItem(final long id) {
            callback.playItemAtIndex((int) id);
        }

        @Override
        public void onSeekTo(final long pos) {
            exoPlayer.seekTo(pos);
        }

        @Override
        public void onStop() {
            callback.close();
        }

        @Override
        public void onCustomAction(final String action, final Bundle extras) {
            if (ACTION_CHANGE_PLAY_MODE.equals(action)) {
                callback.changePlayMode();
            } else if (ACTION_CLOSE.equals(action)) {
                callback.close();
            }
        }

        @Override
        public void onPrepare() {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepare(false);
            }
        }

        @Override
        public void onPlayFromMediaId(final String mediaId, final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromMediaId(mediaId, true, extras);
            }
        }

        @Override
        public void onPrepareFromMediaId(final String mediaId, final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromMediaId(mediaId, false, extras);
            }
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromSearch(query, true, extras);
            }
        }

        @Override
        public void onPlayFromUri(final Uri uri, final Bundle extras) {
            if (playbackPreparer != null) {
                playbackPreparer.onPrepareFromUri(uri, true, extras);
            }
        }
    };

    private void updatePlaybackState() {
        final PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(PLAYBACK_ACTIONS
                        | (playbackPreparer != null
                        ? playbackPreparer.getSupportedPrepareActions() : 0))
                .setState(toPlaybackStateCompat(), exoPlayer.getCurrentPosition(),
                        exoPlayer.getPlaybackParameters().speed);
        builder.addCustomAction(changePlayModeAction());
        builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                ACTION_CLOSE, "Close", R.drawable.ic_close).build());
        if (errorAction != null) {
            builder.addCustomAction(errorAction);
        }
        mediaSession.setPlaybackState(builder.build());
    }

    @PlaybackStateCompat.State
    private int toPlaybackStateCompat() {
        switch (exoPlayer.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return PlaybackStateCompat.STATE_BUFFERING;
            case Player.STATE_READY:
                return exoPlayer.getPlayWhenReady()
                        ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            case Player.STATE_IDLE:
            default:
                return PlaybackStateCompat.STATE_NONE;
        }
    }

    @NonNull
    private PlaybackStateCompat.CustomAction changePlayModeAction() {
        final int mode = callback instanceof PlayerMediaSession
                ? ((PlayerMediaSession) callback).mode : 0;
        switch (mode) {
            case 1:
                return new PlaybackStateCompat.CustomAction.Builder(ACTION_CHANGE_PLAY_MODE,
                        "Repeat all", R.drawable.exo_icon_shuffle_on).build();
            case 2:
                return new PlaybackStateCompat.CustomAction.Builder(ACTION_CHANGE_PLAY_MODE,
                        "Repeat none", R.drawable.exo_icon_repeat_one).build();
            case 3:
                return new PlaybackStateCompat.CustomAction.Builder(ACTION_CHANGE_PLAY_MODE,
                        "Repeat one", R.drawable.exo_icon_repeat_all).build();
            case 0:
            default:
                return new PlaybackStateCompat.CustomAction.Builder(ACTION_CHANGE_PLAY_MODE,
                        "Shuffle", R.drawable.shuffle_disabled).build();
        }
    }

    private void publishQueue() {
        final int windowCount = callback.getQueueSize();
        if (windowCount == 0) {
            mediaSession.setQueue(Collections.emptyList());
            return;
        }
        final int currentWindowIndex = callback.getCurrentPlayingIndex();
        final int queueSize = Math.min(MAX_QUEUE_SIZE, windowCount);
        final int startIndex = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
                windowCount - queueSize);

        final List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = startIndex; i < startIndex + queueSize; i++) {
            queue.add(new MediaSessionCompat.QueueItem(callback.getQueueMetadata(i), i));
        }
        mediaSession.setQueue(queue);
    }

    /** Show an error on the session (used by the Android Auto media browser preparer). */
    public void setCustomErrorMessage(@Nullable final CharSequence message, final int code) {
        errorAction = null;
        final PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(PLAYBACK_ACTIONS)
                .setState(PlaybackStateCompat.STATE_ERROR, 0, 1)
                .setErrorMessage(code, message == null ? "" : message);
        mediaSession.setPlaybackState(builder.build());
    }

    public void clearCustomErrorMessage() {
        updatePlaybackState();
    }

    @Nullable
    @SuppressWarnings("UnusedReturnValue")
    public KeyEvent handleMediaButtonIntent(final Intent intent) {
        return MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    public void setPlayer(final org.schabi.newpipe.player.Player player) {
        this.player = player;
        updateMetadata();
    }

    private void updateMetadata() {
        mediaSession.setMetadata(buildMediaMetadata());
    }

    private MediaMetadataCompat buildMediaMetadata() {
        if (DEBUG) {
            Log.d(TAG, "buildMediaMetadata called");
        }

        if (player == null) {
            return new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
                    .build();
        }

        // set title and artist
        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName());

        // set duration (-1 for livestreams or if unknown, see the METADATA_KEY_DURATION docs)
        final long duration = player.getCurrentStreamInfo()
                .filter(info -> !StreamTypeUtil.isLiveStream(info.getStreamType()))
                .map(info -> info.getDuration() * 1000L)
                .orElse(-1L);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        // set album art, unless the user asked not to, or there is no thumbnail available
        final boolean showThumbnail = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.show_thumbnail_key), true);
        Optional.ofNullable(player.getThumbnail())
                .filter(bitmap -> showThumbnail)
                .ifPresent(bitmap -> {
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap);
                });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            final org.schabi.newpipe.player.Player currentPlayer = player;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentPlayer != null
                        && currentPlayer.getMediaSessionManager() == MediaSessionManager.this) {
                    NotificationUtil.getInstance()
                            .createNotificationIfNeededAndUpdate(currentPlayer, false);
                }
            }, 100);
        }
        return builder.build();
    }

    /**
     * Should be called on player destruction to prevent leakage.
     */
    public void dispose() {
        exoPlayer.removeListener(playerListener);
        mediaSession.setCallback(null);
        if (!isExternalSession) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }
}
