package org.schabi.newpipe.extractor;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ServiceIdentityTest {
    @Test
    public void registeredServiceNamesAndIdsResolveToTheSameService() throws Exception {
        for (final StreamingService service : ServiceList.all()) {
            assertSame(service, NewPipe.getService(service.getServiceInfo().getName()));
            assertSame(service, NewPipe.getService(service.getServiceId()));
        }
    }

    @Test
    public void newerServicesExposeTheirOwnLatestKiosk() throws Exception {
        final StreamingService[] services = {
                ServiceList.SpankBang,
                ServiceList.XHamster,
                ServiceList.XVideos,
                ServiceList.Eporner,
                ServiceList.Ohentai,
                ServiceList.XNXX
        };
        for (final StreamingService service : services) {
            assertEquals("latest", service.getKioskList().getDefaultKioskId());
            assertTrue(service.getKioskList().getAvailableKiosks().contains("latest"));
        }
    }

    @Test
    public void xvideosAndXnxxUrlsResolveToTheirOwnServices() throws Exception {
        assertSame(ServiceList.XVideos,
                NewPipe.getServiceByUrl("https://www.xvideos.com/video.123456/example"));
        assertSame(ServiceList.XNXX,
                NewPipe.getServiceByUrl("https://www.xnxx.com/video-abcdef/example"));
    }

    @Test
    public void xvideosAndXnxxDefaultKioskFactoriesProduceTheirOwnUrls() throws Exception {
        assertEquals("https://www.xvideos.com/", ServiceList.XVideos.getKioskList()
                .getListLinkHandlerFactoryByType("latest").fromId("latest").getUrl());
        assertEquals("https://www.xnxx.com/best/", ServiceList.XNXX.getKioskList()
                .getListLinkHandlerFactoryByType("latest").fromId("latest").getUrl());
    }
}
