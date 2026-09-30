/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.notifications.repository.NotificationScheduleRepository;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationSettingsService.UpdateStationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The station's own settings page: its general settings, its notification times and deleting the
 * copy of a station that moved away.
 */
class StationManageServicesTest {

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private static UpdateStationRequest named(String name) {
        return new UpdateStationRequest(
                name, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static UpdateStationRequest everything() {
        return new UpdateStationRequest(
                "Nord",
                "Europe/Berlin",
                "de-DE",
                "forest",
                null,
                "#fff",
                null,
                false,
                "ALLOW_ALL",
                DiscoveryVisibility.NONE,
                "Wir",
                null,
                true,
                false,
                " ",
                true,
                false,
                true,
                true);
    }

    @Test
    void everySettingTheRequestNamesIsWrittenAndTheRestLeftAlone() {
        var stations = mock(StationService.class);
        var station = mock(Station.class);
        when(stations.update(3, "Nord")).thenReturn(Optional.of(station));
        var service = new StationSettingsService(stations);

        assertSame(station, service.update(3, everything()));
        verify(stations).updateTimezone(3, "Europe/Berlin");
        verify(stations).updateLocale(3, "de-DE");
        verify(stations).updateThemeSettings(3, "forest", true, "#fff", ThemeFeel.ROUNDED, false);
        verify(stations).updatePublicKbMode(3, PublicKbMode.ALLOW_ALL);
        verify(stations).updateDiscoverySettings(3, DiscoveryVisibility.NONE, "Wir", false);
        verify(stations).updatePublicCalendarEnabled(3, true);
        verify(stations).updatePublicPagesEnabled(3, false);
        verify(stations).updatePublicSlug(3, null);
        verify(stations).updatePublicWaitlistEnabled(3, true);
        verify(stations).updatePublicBlogEnabled(3, false);
        verify(stations).updatePdfHidesInstanceUrl(3, true);
        verify(stations).updateNicknamesEnabled(3, true);

        service.update(3, named("Nord"));
        verify(stations, never()).updateLocale(3, "");
        verify(stations).updateTimezone(anyInt(), anyString());
    }

    @Test
    void aStationNeedsANameAKnownClockAndAUsableAddress() {
        var stations = mock(StationService.class);
        var service = new StationSettingsService(stations);
        var badZone = new UpdateStationRequest(
                "Nord",
                "Mars/Olympus",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        var takenSlug = new UpdateStationRequest(
                "Nord", null, null, null, null, null, null, null, null, null, null, null, null, null, "taken", null,
                null, null, null);
        doThrow(new IllegalArgumentException("taken")).when(stations).updatePublicSlug(3, "taken");

        assertEquals(Refusal.STATION_NEEDS_A_NAME_ON_CHANGE, refusalOf(() -> service.update(3, named(" "))));
        assertEquals(Refusal.STATION_NEEDS_A_NAME_ON_CHANGE, refusalOf(() -> service.update(3, named(null))));
        assertEquals(Refusal.STATION_TIME_ZONE_NOT_KNOWN, refusalOf(() -> service.update(3, badZone)));
        assertEquals(Refusal.STATION_ADDRESS_NOT_USABLE, refusalOf(() -> service.update(3, takenSlug)));
        assertEquals(Refusal.STATION_NOT_HERE_AFTER_CHANGE, refusalOf(() -> service.update(3, named("Nord"))));
        verify(stations, never()).updateThemeSettings(anyInt(), any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    void notificationTimesAreReadWithTheFloorAndOnlyRealTimesAreWritten() {
        var schedules = mock(NotificationScheduleRepository.class);
        var mailing = mock(Mailing.class);
        when(mailing.notificationDigestIntervalMinutes()).thenReturn(30);
        when(schedules.findDigestGroups(List.of(3), List.of()))
                .thenReturn(List.of(new DigestGroup(
                        new DigestGroup.Key(DigestGroup.Kind.STATION, 3),
                        "Nord",
                        UUID.randomUUID(),
                        ZoneId.of("UTC"),
                        "de",
                        List.of(LocalTime.of(7, 0)),
                        null)));
        var service = new StationNotificationTimesService(schedules, mailing);

        var times = service.times(3);
        service.update(3, List.of("08:30"));
        service.update(3, null);

        assertEquals(List.of("07:00"), times.sendTimes());
        assertEquals(30, times.floorMinutes());
        verify(schedules).setStationSendTimes(3, List.of(LocalTime.of(8, 30)));
        verify(schedules).setStationSendTimes(3, List.of());
        assertEquals(Refusal.NOTIFICATION_TIME_NOT_A_TIME, refusalOf(() -> service.update(3, List.of("soon"))));
        assertEquals(
                Refusal.NOTIFICATION_TIME_NOT_A_TIME, refusalOf(() -> service.update(3, Arrays.asList((String) null))));
        assertEquals(Refusal.NOTIFICATION_TIMES_NOT_SET, refusalOf(() -> service.times(4)));
    }

    @Test
    void onlyTheCopyOfAStationThatMovedAwayIsDeletedWithoutAsking() {
        var repository = mock(StationRepository.class);
        when(repository.isReadOnlyForTransfer(3)).thenReturn(true);
        when(repository.delete(3)).thenReturn(true);
        var service = new StationService(
                repository,
                mock(StationMemberRepository.class),
                mock(AccountRepository.class),
                mock(FederationService.class),
                mock(StationMemberInviteService.class),
                mock(ClusterRepository.class));

        service.deleteMoved(3);

        verify(repository).invalidateUidCache(3);
        assertEquals(Refusal.STATION_NOT_MOVED, refusalOf(() -> service.deleteMoved(4)));
        verify(repository, never()).delete(4);
    }
}
