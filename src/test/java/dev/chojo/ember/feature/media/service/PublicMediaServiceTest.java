/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMediaServiceTest {
    private static final MediaContent PICTURE = new MediaContent(new byte[] {1, 2}, "image/webp");

    private MediaLibraryService media;
    private PublicMediaService service;

    @BeforeEach
    void setup() {
        media = mock(MediaLibraryService.class);
        var stations = mock(StationRepository.class);
        when(stations.resolveAddressedId("wache-nord")).thenReturn(Optional.of(4));
        service = new PublicMediaService(media, stations);
    }

    @Test
    void aStationIsFoundByItsAddressOrRefused() {
        assertEquals(4, service.resolveStation("wache-nord"));
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_FILE,
                assertThrows(RefusalResponse.class, () -> service.resolveStation("gone"))
                        .refusal());
    }

    @Test
    void aStationsFileIsReadAtTheWidthAskedFor() {
        when(media.readVariant(4, "abc", 320, "image/webp")).thenReturn(Optional.of(PICTURE));

        assertSame(PICTURE, service.read(4, "abc", "320", "image/webp"));
    }

    @Test
    void aWidthThatIsNoPositiveNumberIsIgnored() {
        when(media.readVariant(4, "abc", null, null)).thenReturn(Optional.of(PICTURE));

        assertSame(PICTURE, service.read(4, "abc", "wide", null));
        assertSame(PICTURE, service.read(4, "abc", "-5", null));
    }

    @Test
    void theInstancesOwnFileBelongsToNoStation() {
        when(media.readVariant(isNull(), anyString(), isNull(), isNull())).thenReturn(Optional.of(PICTURE));

        assertSame(PICTURE, service.readInstance("abc", null, null));
    }

    @Test
    void aFileThatIsNotHereIsRefused() {
        when(media.readVariant(any(), anyString(), any(), any())).thenReturn(Optional.empty());

        assertEquals(
                Refusal.PUBLIC_FILE_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.read(4, "abc", null, null))
                        .refusal());
    }
}
