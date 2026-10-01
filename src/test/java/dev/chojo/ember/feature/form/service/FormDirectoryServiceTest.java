/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormDirectoryServiceTest {
    private FormService forms;
    private FormDirectoryService directory;

    static Form form(
            int id,
            int stationId,
            String title,
            Form.FormStatus status,
            FormPurpose purpose,
            FormVisibility visibility) {
        return form(id, stationId, title, status, purpose, visibility, false);
    }

    private static Form form(
            int id,
            int stationId,
            String title,
            Form.FormStatus status,
            FormPurpose purpose,
            FormVisibility visibility,
            boolean restricted) {
        return new Form(
                id,
                stationId,
                title,
                "",
                status,
                false,
                false,
                false,
                null,
                null,
                null,
                1,
                null,
                null,
                null,
                null,
                restricted,
                purpose,
                visibility,
                UUID.nameUUIDFromBytes(("f" + id).getBytes()),
                0,
                null,
                null,
                null);
    }

    private static Form open(int id) {
        return form(id, 3, "Form " + id, Form.FormStatus.OPEN, FormPurpose.INTERNAL, FormVisibility.PUBLIC);
    }

    @BeforeEach
    void setup() {
        forms = mock(FormService.class);
        when(forms.isAcceptingResponses(any())).thenReturn(true);
        directory = new FormDirectoryService(forms);
    }

    @Test
    void theOwnOpenFormsComeFirstThenTheOnesOnlyAWardCanAnswer() {
        var draft = form(4, 3, "Draft", Form.FormStatus.DRAFT, FormPurpose.INTERNAL, FormVisibility.PUBLIC);
        when(forms.findByStationForMember(3, 11, false)).thenReturn(List.of(open(1), draft));
        when(forms.findByStationOnBehalfOf(3, 20)).thenReturn(List.of(open(1), open(2)));
        when(forms.findByStation(3)).thenReturn(List.of(open(1), open(2), open(5)));
        when(forms.countResponses(2)).thenReturn(7);
        when(forms.hasResponded(1, 11)).thenReturn(true);

        var available = directory.available(3, 11, false, List.of(20));

        assertEquals(
                List.of(1, 2),
                available.stream().map(FormDirectoryService.FormListEntry::id).toList());
        assertTrue(available.getFirst().hasResponded());
        assertEquals(7, available.get(1).responseCount());
    }

    @Test
    void aFormPutToPartOfTheStationIsListedAsRestricted() {
        var restricted = form(6, 3, "Nur Jugend", Form.FormStatus.OPEN, FormPurpose.INTERNAL, FormVisibility.PUBLIC, true);
        when(forms.findByStationForMember(3, 11, false)).thenReturn(List.of(open(1), restricted));

        var available = directory.available(3, 11, false, List.of());

        assertEquals(
                List.of(false, true),
                available.stream().map(FormDirectoryService.FormListEntry::restricted).toList());
    }

    @Test
    void withoutWardsOnlyTheOwnFormsAreListed() {
        when(forms.findByStationForMember(3, 11, true)).thenReturn(List.of(open(1)));

        assertEquals(1, directory.available(3, 11, true, List.of()).size());
    }

    @Test
    void thePickerOffersOpenlyAddressedFormsMatchingTheTitle() {
        var unlisted = form(6, 3, "Form hidden", Form.FormStatus.OPEN, FormPurpose.POLL, FormVisibility.UNLISTED);
        var poll = form(7, 3, "Sommerfest", Form.FormStatus.OPEN, FormPurpose.POLL, FormVisibility.PUBLIC);
        var other = form(8, 3, "Winter", Form.FormStatus.OPEN, FormPurpose.POLL, FormVisibility.PUBLIC);
        when(forms.findByStationAndPurpose(3, FormPurpose.POLL)).thenReturn(List.of(unlisted, poll, other));

        assertEquals(
                List.of("Sommerfest"),
                directory.pickable(3, FormPurpose.POLL, null, " sommer", 10).stream()
                        .map(FormDirectoryService.FormSearchResult::title)
                        .toList());
        assertEquals(1, directory.pickable(3, FormPurpose.POLL, " ", null, 0).size());
    }

    @Test
    void oneFormByAddressIsFoundOnlyWhereItBelongsAndFits() {
        var poll = form(7, 3, "Sommerfest", Form.FormStatus.OPEN, FormPurpose.POLL, FormVisibility.PUBLIC);
        when(forms.findByPublicUid(poll.publicUid())).thenReturn(Optional.of(poll));
        String uid = poll.publicUid().toString();

        assertEquals(1, directory.pickable(3, FormPurpose.POLL, uid, null, 10).size());
        assertTrue(directory.pickable(4, FormPurpose.POLL, uid, null, 10).isEmpty());
        assertTrue(directory.pickable(3, FormPurpose.CONTACT, uid, null, 10).isEmpty());
        assertTrue(
                directory.pickable(3, FormPurpose.POLL, "not-a-uid", null, 10).isEmpty());
    }
}
