/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ManagedMemberService.ValueEntry;
import dev.chojo.ember.feature.members.service.ProfileFieldService.MergedValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedMemberServiceTest {
    private static final int GUARDIAN = 11;
    private static final int CHILD = 12;
    private static final int STATION_ID = 3;

    private StationMemberService memberService;
    private StationMemberRepository members;
    private AccountRepository accounts;
    private ProfileFieldService profileFields;
    private InventoryService inventory;
    private InventoryCheckService checks;
    private GdprExportService exports;
    private ManagedMemberService service;

    private static StationMember child(Integer accountId) {
        return new StationMember(CHILD, STATION_ID, null, accountId, false, null, "Kind", StationUserType.MEMBER, null);
    }

    private static InventoryItem item(Integer sizeId, Integer lostNoteBy) {
        return new InventoryItem(
                5,
                9,
                "J-1",
                "Jacke",
                sizeId,
                null,
                null,
                CHILD,
                null,
                "weg",
                lostNoteBy,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static ProfileField field(int id) {
        return new ProfileField(id, STATION_ID, "F" + id, ProfileFieldType.TEXT, null, false, false, null, false);
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        members = mock(StationMemberRepository.class);
        accounts = mock(AccountRepository.class);
        profileFields = mock(ProfileFieldService.class);
        inventory = mock(InventoryService.class);
        checks = mock(InventoryCheckService.class);
        exports = mock(GdprExportService.class);
        var access = mock(AccessManager.class);
        when(access.resolveExpandedMemberPermissions(anyInt())).thenReturn(Set.of());
        service = new ManagedMemberService(
                memberService,
                members,
                accounts,
                profileFields,
                inventory,
                checks,
                exports,
                access,
                mock(MemberIdentityFactory.class));
        when(memberService.findManaged(GUARDIAN)).thenReturn(List.of(child(1)));
        when(members.findById(CHILD)).thenReturn(Optional.of(child(1)));
        when(profileFields.findReadableBy(eq(STATION_ID), any())).thenReturn(List.of(field(1)));
    }

    @Test
    void theMembersLookedAfterAreNamedWithTheirAddress() {
        when(accounts.findById(1)).thenReturn(Optional.of(TestSessions.account()));

        var managed = service.managed(GUARDIAN);

        assertEquals("mara@test.com", managed.getFirst().email());
        assertTrue(managed.getFirst().name().startsWith("Mara"));
    }

    @Test
    void aMemberWhoseAccountIsGoneHasNoNameOrAddress() {
        var managed = service.managed(GUARDIAN);

        assertEquals("", managed.getFirst().name());
        assertEquals("", managed.getFirst().email());
    }

    @Test
    void somebodyNotLookedAfterIsRefusedEverywhere() {
        for (Runnable call : List.<Runnable>of(
                () -> service.profile(GUARDIAN, 99),
                () -> service.setProfile(GUARDIAN, 99, List.of()),
                () -> service.inventory(GUARDIAN, 99),
                () -> service.requirements(GUARDIAN, 99),
                () -> service.export(GUARDIAN, 99))) {
            var refused = assertThrows(RefusalResponse.class, call::run);
            assertEquals(Refusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER, refused.refusal());
        }
    }

    @Test
    void aMemberThatVanishedIsRefusedForTheProfile() {
        when(members.findById(CHILD)).thenReturn(Optional.empty());

        assertEquals(
                Refusal.MEMBER_NOT_HERE_ON_MANAGED_PROFILE,
                assertThrows(RefusalResponse.class, () -> service.profile(GUARDIAN, CHILD))
                        .refusal());
        assertEquals(
                Refusal.MEMBER_NOT_HERE_ON_MANAGED_PROFILE_CHANGE,
                assertThrows(RefusalResponse.class, () -> service.setProfile(GUARDIAN, CHILD, List.of()))
                        .refusal());
        assertEquals(
                Refusal.MEMBER_NOT_HERE_ON_MANAGED_EQUIPMENT,
                assertThrows(RefusalResponse.class, () -> service.requirements(GUARDIAN, CHILD))
                        .refusal());
    }

    @Test
    void theProfileHoldsOnlyTheStationsReadableQuestions() {
        when(profileFields.findValues(CHILD))
                .thenReturn(List.of(
                        new MergedValue(1, "ja", FieldOrigin.STATION),
                        new MergedValue(2, "verborgen", FieldOrigin.STATION),
                        new MergedValue(1, "cluster", FieldOrigin.CLUSTER)));

        var profile = service.profile(GUARDIAN, CHILD);

        assertEquals(List.of(field(1)), profile.fields());
        assertEquals(List.of(new ProfileFieldValue(CHILD, 1, "ja")), profile.values());
    }

    @Test
    void answersToQuestionsTheMemberMayNotReadAreDropped() {
        service.setProfile(GUARDIAN, CHILD, List.of(new ValueEntry(1, "ja"), new ValueEntry(2, "nein")));

        verify(profileFields).setValues(CHILD, List.of(new FieldValueEntry(1, "ja")), GUARDIAN);
    }

    @Test
    void equipmentCarriesItsInventoryAndSize() {
        when(inventory.findItemsByMember(CHILD)).thenReturn(List.of(item(2, null), item(null, 11)));
        when(inventory.findById(9))
                .thenReturn(Optional.of(new Inventory(
                        9, STATION_ID, "Jacken", InventoryType.INTERNAL, true, false, false, null, null)));
        when(inventory.findSizes(9)).thenReturn(List.of(new InventorySize(2, 9, "M", 0, null)));

        var items = service.inventory(GUARDIAN, CHILD);

        assertEquals("Jacken", items.getFirst().inventoryName());
        assertEquals("M", items.getFirst().sizeName());
        assertNull(items.get(1).sizeName());
    }

    @Test
    void equipmentOfAVanishedInventoryCountsAsExchangeable() {
        when(inventory.findItemsByMember(CHILD)).thenReturn(List.of(item(7, null)));
        when(inventory.findById(9)).thenReturn(Optional.empty());

        var item = service.inventory(GUARDIAN, CHILD).getFirst();

        assertEquals("", item.inventoryName());
        assertTrue(item.inventoryHomogeneous());
        assertNull(item.sizeName());
    }

    @Test
    void requirementsAreReadForTheMembersStation() {
        when(checks.getRequiredItems(STATION_ID, CHILD))
                .thenReturn(List.of(new RequiredInventoryItem(
                        9, "Jacken", InventoryType.INTERNAL, false, true, List.of(), 2, 1, 0)));

        var required = service.requirements(GUARDIAN, CHILD);

        assertEquals(2, required.getFirst().requiredQuantity());
    }

    @Test
    void theExportIsFiledUnderTheMembersName() {
        when(exports.exportMemberData(CHILD)).thenReturn(Map.<String, Object>of("at", Instant.EPOCH));

        var export = service.export(GUARDIAN, CHILD);

        assertEquals("Kind", export.name());
        assertEquals(Map.of("at", Instant.EPOCH), export.data());
    }
}
