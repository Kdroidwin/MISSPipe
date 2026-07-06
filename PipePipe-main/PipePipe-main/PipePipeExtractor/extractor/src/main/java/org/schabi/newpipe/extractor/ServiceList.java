package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.services.bandcamp.BandcampService;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavService;
import org.schabi.newpipe.extractor.services.media_ccc.MediaCCCService;
import org.schabi.newpipe.extractor.services.missav.MissAvService;
import org.schabi.newpipe.extractor.services.peertube.PeertubeService;
import org.schabi.newpipe.extractor.services.pornhub.PornhubService;
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudService;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
import org.schabi.newpipe.extractor.services.niconico.NiconicoService;

import java.util.Collections;
import java.util.List;

/*
 * Copyright (C) Christian Schabesberger 2018 <chris.schabesberger@mailbox.org>
 * ServiceList.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * A list of supported services.
 */
@SuppressWarnings({"ConstantName", "InnerAssignment"}) // keep unusual names and inner assignments
public final class ServiceList {
    private ServiceList() {
        //no instance
    }

    public static final YoutubeService YouTube = new YoutubeService(0);
    public static final SoundcloudService SoundCloud = new SoundcloudService(1);
    public static final MediaCCCService MediaCCC = new MediaCCCService(2);
    public static final PeertubeService PeerTube = new PeertubeService(3);
    public static final BandcampService Bandcamp = new BandcampService(4);
    public static final BilibiliService BiliBili = new BilibiliService(5);
    public static final NiconicoService NicoNico = new NiconicoService(6);
    public static final MissAvService MissAV = new MissAvService(0);
    public static final KissJavService KissJAV = new KissJavService(1);
    public static final EightyFivePoService EightyFivePo = new EightyFivePoService(2);
    public static final PornhubService Pornhub = new PornhubService(3);
    /**
     * When creating a new service, put this service in the end of this list,
     * and give it the next free id.
     *
     * MISSPipe exposes MissAV, KissJAV, 85po and Pornhub. Legacy service constants stay initialized because the
     * client still contains service-specific branches that will be removed incrementally.
     */
    private static final List<StreamingService> SERVICES = Collections.unmodifiableList(
            java.util.Arrays.asList(MissAV, KissJAV, EightyFivePo, Pornhub));

    /**
     * Get all the supported services.
     *
     * @return a unmodifiable list of all the supported services
     */
    public static List<StreamingService> all() {
        return SERVICES;
    }
}
