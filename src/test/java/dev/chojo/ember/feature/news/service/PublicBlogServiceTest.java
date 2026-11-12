/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicBlogServiceTest {
    private final StationRepository stations = mock(StationRepository.class);
    private final ModuleAvailability modules = mock(ModuleAvailability.class);
    private final PublicBlogService blogs = new PublicBlogService(stations, modules);

    private Refusal refusalOf(String address) {
        return assertThrows(
                        RefusalResponse.class,
                        () -> blogs.openBlog(
                                address,
                                NewsRefusal.STATION_NOT_HERE_BEHIND_BLOG_LIST,
                                NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST))
                .refusal();
    }

    @Test
    void anOpenBlogAnswersAndEveryOtherAddressIsRefusedWithWhatThePageNames() {
        var open = mock(Station.class);
        when(open.publicBlogEnabled()).thenReturn(true);
        var closed = mock(Station.class);
        when(stations.resolveAddressedId("open")).thenReturn(Optional.of(3));
        when(stations.resolveAddressedId("closed")).thenReturn(Optional.of(4));
        when(stations.resolveAddressedId("vanished")).thenReturn(Optional.of(5));
        when(stations.findById(3)).thenReturn(Optional.of(open));
        when(stations.findById(4)).thenReturn(Optional.of(closed));

        assertEquals(
                open,
                blogs.openBlog(
                        "open",
                        NewsRefusal.STATION_NOT_HERE_BEHIND_BLOG_LIST,
                        NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST));
        assertEquals(NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST, refusalOf("closed"));
        assertEquals(NewsRefusal.STATION_NOT_HERE_BEHIND_BLOG_LIST, refusalOf("vanished"));
        assertEquals(NewsRefusal.STATION_NOT_HERE_BEHIND_BLOG, refusalOf("unknown"));
    }

    /** A blog whose news are switched off, by the station or its cluster, is closed however it is set. */
    @Test
    void aBlogWithoutNewsIsClosed() {
        var open = mock(Station.class);
        when(open.publicBlogEnabled()).thenReturn(true);
        when(stations.resolveAddressedId("off")).thenReturn(Optional.of(6));
        when(stations.findById(6)).thenReturn(Optional.of(open));
        when(modules.disabledAt(6)).thenReturn(Set.of(StationModule.NEWS));

        assertEquals(NewsRefusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST, refusalOf("off"));
    }
}
