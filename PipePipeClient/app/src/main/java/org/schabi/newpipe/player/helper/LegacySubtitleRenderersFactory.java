package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Looper;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;

import java.util.ArrayList;

/**
 * A {@link DefaultRenderersFactory} that keeps legacy (renderer-side) subtitle decoding enabled.
 *
 * <p>
 * We side-load subtitles as {@code SingleSampleMediaSource} (raw ttml/vtt) merged via
 * {@code MergingMediaSource}. media3 1.4+ parses subtitles during extraction by default and the
 * {@link TextRenderer} then only accepts {@code application/x-media3-cues}, so sideloaded tracks
 * (which arrive as e.g. {@code application/ttml+xml}) made enabling a subtitle track throw
 * {@code IllegalStateException} ("Legacy decoding is disabled") and killed playback. Turning legacy
 * decoding back on lets those tracks parse again while still accepting the cue format.
 * </p>
 *
 * <p>
 * This is media3's supported path for sideloaded subtitles, so it is a deliberate choice, not a
 * workaround. The alternative (letting media3 parse subtitles during extraction, its default) would
 * be cleaner long term but needs the subtitle pipeline moved onto standard MediaItems, which does
 * not fit the custom SABR media source today. Revisit if that pipeline is ever reworked.
 * </p>
 */
public class LegacySubtitleRenderersFactory extends DefaultRenderersFactory {

    public LegacySubtitleRenderersFactory(final Context context) {
        super(context);
    }

    @Override
    protected void buildTextRenderers(final Context context,
                                      final TextOutput output,
                                      final Looper outputLooper,
                                      @ExtensionRendererMode final int extensionRendererMode,
                                      final ArrayList<Renderer> out) {
        final TextRenderer textRenderer = new TextRenderer(output, outputLooper);
        textRenderer.experimentalSetLegacyDecodingEnabled(true);
        out.add(textRenderer);
    }
}
