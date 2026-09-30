/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.feature.members.repository.MemberTablePresetRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberTablePresetServiceTest {
    private static final MemberTableColumn NAME = MemberTableColumn.builtin("name");
    private static final MemberTableColumn BLANK = MemberTableColumn.builtin(" ");

    private final MemberTablePresetRepository repository = mock(MemberTablePresetRepository.class);
    private final MemberTablePresetService service = new MemberTablePresetService(repository);

    @Test
    void aSelectionIsSavedTrimmedWithoutColumnsThatNameNothing() {
        var saved = new MemberTablePreset(4, 3, "Jugend", List.of(NAME));
        when(repository.save(3, "Jugend", List.of(NAME))).thenReturn(saved);

        assertEquals(saved, service.save(3, "  Jugend ", List.of(NAME, BLANK)));
    }

    @Test
    void aSelectionNeedsAName() {
        for (String name : new String[] {null, " "}) {
            var refused = assertThrows(RefusalResponse.class, () -> service.save(3, name, List.of(NAME)));
            assertEquals(Refusal.MEMBER_TABLE_PRESET_NAME_MISSING, refused.refusal());
        }
    }

    @Test
    void noColumnsAreNoColumns() {
        assertTrue(MemberTablePresetService.wellFormed(null).isEmpty());
    }

    @Test
    void selectionsAreListedFoundAndDeletedForTheStation() {
        var preset = new MemberTablePreset(4, 3, "Jugend", List.of(NAME));
        when(repository.findByStation(3)).thenReturn(List.of(preset));
        when(repository.findById(4)).thenReturn(Optional.of(preset));

        assertEquals(List.of(preset), service.list(3));
        assertEquals(Optional.of(preset), service.findById(4));
        service.delete(4, 3);
        verify(repository).delete(4, 3);
    }
}
