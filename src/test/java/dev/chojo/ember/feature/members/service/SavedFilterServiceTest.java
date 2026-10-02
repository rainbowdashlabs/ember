/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.repository.SavedFilterRepository;
import dev.chojo.ember.feature.members.service.SavedFilterService.CreateFilterRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedFilterServiceTest {
    private final SavedFilterRepository repository = mock(SavedFilterRepository.class);
    private final SavedFilterService service = new SavedFilterService(repository);

    private static SavedFilter filter(int id) {
        return new SavedFilter(id, 1, FilterTableType.MEMBERS, "Jugend", "{}", id);
    }

    @Test
    void aNewFilterGoesToTheEndOfTheList() {
        when(repository.findByAccountAndTable(1, FilterTableType.MEMBERS)).thenReturn(List.of(filter(0), filter(1)));
        when(repository.create(1, FilterTableType.MEMBERS, "Neu", "{}", 2)).thenReturn(filter(2));

        assertEquals(filter(2), service.create(1, new CreateFilterRequest(FilterTableType.MEMBERS, "Neu", "{}")));
        assertEquals(2, service.list(1, FilterTableType.MEMBERS).size());
    }

    @Test
    void aFilterWithoutItsDetailsIsRefused() {
        for (var request : List.of(
                new CreateFilterRequest(null, "a", "{}"),
                new CreateFilterRequest(FilterTableType.MEMBERS, null, "{}"),
                new CreateFilterRequest(FilterTableType.MEMBERS, "a", null))) {
            var refused = assertThrows(RefusalResponse.class, () -> service.create(1, request));
            assertEquals(MemberRefusal.SAVED_FILTER_DETAILS_MISSING, refused.refusal());
        }
    }

    @Test
    void aFilterOfSomebodyElseIsNotThere() {
        when(repository.delete(5, 1)).thenReturn(true);
        service.delete(1, 5);
        verify(repository).delete(5, 1);

        var refused = assertThrows(RefusalResponse.class, () -> service.delete(2, 5));
        assertEquals(MemberRefusal.SAVED_FILTER_NOT_HERE_ON_DELETE, refused.refusal());
    }
}
