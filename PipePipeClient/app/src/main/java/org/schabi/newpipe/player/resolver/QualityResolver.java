package org.schabi.newpipe.player.resolver;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public interface QualityResolver {
    int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

    int getOverrideResolutionIndex(List<VideoStream> sortedVideos, int selectedIndex);

    int getCurrentAudioQualityIndex(List<AudioStream> audioStreams);
}