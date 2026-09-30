/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicBlogServiceTest {
    private final StationRepository stations = mock(StationRepository.class);
    private final PublicBlogService blogs = new PublicBlogService(stations);

    private Refusal refusalOf(String address) {
        return assertThrows(
                        RefusalResponse.class,
                        () -> blogs.openBlog(
                                address,
                                Refusal.STATION_NOT_HERE_BEHIND_BLOG_LIST,
                                Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST))
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
                        "open", Refusal.STATION_NOT_HERE_BEHIND_BLOG_LIST, Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST));
        assertEquals(Refusal.PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST, refusalOf("closed"));
        assertEquals(Refusal.STATION_NOT_HERE_BEHIND_BLOG_LIST, refusalOf("vanished"));
        assertEquals(Refusal.STATION_NOT_HERE_BEHIND_BLOG, refusalOf("unknown"));
    }
}
