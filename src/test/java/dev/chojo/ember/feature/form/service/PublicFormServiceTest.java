/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormServiceTest {
    private FormService forms;
    private StationRepository stations;
    private PublicFormService service;
    private Station station;

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private static Form form(int stationId, FormPurpose purpose, FormVisibility visibility) {
        return FormDirectoryServiceTest.form(7, stationId, "Umfrage", Form.FormStatus.OPEN, purpose, visibility);
    }

    @BeforeEach
    void setup() {
        forms = mock(FormService.class);
        stations = mock(StationRepository.class);
        station = mock(Station.class);
        when(station.id()).thenReturn(3);
        when(stations.findByAddress("wache")).thenReturn(Optional.of(station));
        service = new PublicFormService(forms, stations);
    }

    @Test
    void aLinkNamesItsFormAndTheStationAroundIt() {
        var form = form(3, FormPurpose.POLL, FormVisibility.UNLISTED);
        when(forms.findByShareToken("t")).thenReturn(Optional.of(form));
        when(stations.findById(3)).thenReturn(Optional.of(station));

        assertEquals(form, service.sharedForm("t"));
        assertEquals(station, service.stationOf(form));
        assertEquals(Refusal.FORM_LINK_UNKNOWN, refusalOf(() -> service.sharedForm("x")));
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_FORM_LINK,
                refusalOf(() -> service.stationOf(form(4, FormPurpose.POLL, FormVisibility.PUBLIC))));
    }

    @Test
    void anAddressAnswersOnlyAnOpenlyAddressedOutsideFormOfThatStation() {
        var poll = form(3, FormPurpose.POLL, FormVisibility.PUBLIC);
        when(forms.findByPublicUid(poll.publicUid())).thenReturn(Optional.of(poll));

        assertEquals(poll, service.resolveFormOfStation("wache", poll.publicUid()));
        assertEquals(
                Refusal.STATION_NOT_HERE_BEHIND_PUBLIC_FORM,
                refusalOf(() -> service.resolveFormOfStation("gone", poll.publicUid())));
    }

    @Test
    void everyOtherFormIsRefusedByTheAddress() {
        var foreign = form(4, FormPurpose.POLL, FormVisibility.PUBLIC);
        var internal = FormDirectoryServiceTest.form(
                8, 3, "Intern", Form.FormStatus.OPEN, FormPurpose.INTERNAL, FormVisibility.PUBLIC);
        var linkOnly = FormDirectoryServiceTest.form(
                9, 3, "Link", Form.FormStatus.OPEN, FormPurpose.CONTACT, FormVisibility.UNLISTED);
        when(forms.findByPublicUid(foreign.publicUid())).thenReturn(Optional.of(foreign));
        when(forms.findByPublicUid(internal.publicUid())).thenReturn(Optional.of(internal));
        when(forms.findByPublicUid(linkOnly.publicUid())).thenReturn(Optional.of(linkOnly));

        assertEquals(
                Refusal.PUBLIC_FORM_NOT_HERE,
                refusalOf(() -> service.resolveFormOfStation("wache", foreign.publicUid())));
        assertEquals(
                Refusal.FORM_NOT_ANSWERED_FROM_OUTSIDE,
                refusalOf(() -> service.resolveFormOfStation("wache", internal.publicUid())));
        assertEquals(
                Refusal.FORM_NOT_OPENLY_ADDRESSED,
                refusalOf(() -> service.resolveFormOfStation("wache", linkOnly.publicUid())));
        assertEquals(
                Refusal.PUBLIC_FORM_NOT_HERE,
                refusalOf(() -> service.resolveFormOfStation("wache", UUID.randomUUID())));
    }
}
