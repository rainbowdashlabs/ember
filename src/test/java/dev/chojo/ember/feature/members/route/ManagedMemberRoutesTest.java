/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.service.ManagedAccessService;
import dev.chojo.ember.feature.members.service.ManagedMemberService;
import dev.chojo.ember.feature.members.service.ManagedMemberService.DataExport;
import dev.chojo.ember.feature.members.service.ManagedMemberService.ManagedMember;
import dev.chojo.ember.feature.members.service.ManagedMemberService.MemberProfile;
import dev.chojo.ember.feature.members.service.ManagedMemberService.MemberRequirement;
import dev.chojo.ember.feature.members.service.ManagedMemberService.ValueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A guardian's view of the members they look after, over HTTP: every handler names the guardian
 * from the session and leaves whether they look after the member to the service.
 */
class ManagedMemberRoutesTest {
    private static final int GUARDIAN = TestSessions.MEMBER_ID;

    private ManagedMemberService managed;
    private RouteHarness harness;
    private UserSession guardian;

    @BeforeEach
    void setup() {
        managed = mock(ManagedMemberService.class);
        harness = RouteHarness.serving(new ManagedMemberRoutes(managed, mock(ManagedAccessService.class)));
        guardian = TestSessions.member(3, StationPermission.MEMBER_GUARDIAN);
    }

    @Test
    void theGuardiansMembersProfileAndEquipmentComeFromTheService() {
        when(managed.managed(GUARDIAN)).thenReturn(List.of(new ManagedMember(12, 3, 2, "Kind", "k@test.com")));
        when(managed.profile(GUARDIAN, 12)).thenReturn(new MemberProfile(List.of(), List.of()));
        when(managed.inventory(GUARDIAN, 12)).thenReturn(List.of());
        when(managed.requirements(GUARDIAN, 12)).thenReturn(List.of(new MemberRequirement(9, "Jacken", 2)));

        harness.run((server, client) -> {
            assertEquals(
                    "Kind",
                    json(client.get(PREFIX + "/managed-members", harness.as(guardian)))
                            .get(0)
                            .path("name")
                            .asString());
            assertEquals(
                    200,
                    client.get(PREFIX + "/managed-members/12/profile", harness.as(guardian))
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/managed-members/12/inventory-items", harness.as(guardian))
                            .code());
            var required = client.get(PREFIX + "/managed-members/12/inventory-requirements", harness.as(guardian));
            assertEquals(2, json(required).get(0).path("requiredQuantity").asInt());
        });
    }

    @Test
    void answersAreHandedOnForTheService() {
        when(managed.setProfile(GUARDIAN, 12, List.of(new ValueEntry(1, "ja")))).thenReturn(List.of());

        var saved = harness.request(client -> client.put(
                PREFIX + "/managed-members/12/profile",
                body("{\"values\": [{\"fieldId\": 1, \"value\": \"ja\"}]}"),
                harness.as(guardian)));

        assertEquals(200, saved.code());
        verify(managed).setProfile(GUARDIAN, 12, List.of(new ValueEntry(1, "ja")));
    }

    @Test
    void theExportIsAnAttachmentNamedAfterTheMember() {
        when(managed.export(GUARDIAN, 12)).thenReturn(new DataExport(Map.<String, Object>of("name", "Kind"), "Kind"));

        var export =
                harness.request(client -> client.get(PREFIX + "/managed-members/12/gdpr-export", harness.as(guardian)));

        assertTrue(header(export, "Content-Disposition").contains("Kind"));
        assertEquals("Kind", json(export).path("name").asString());
    }

    @Test
    void aMemberNotLookedAfterIsRefused() {
        when(managed.profile(GUARDIAN, 99)).thenThrow(MemberRefusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER.raise());

        var refused =
                harness.request(client -> client.get(PREFIX + "/managed-members/99/profile", harness.as(guardian)));

        assertEquals(MemberRefusal.MEMBER_NOT_YOURS_TO_LOOK_AFTER, refusalOf(refused));
    }
}
