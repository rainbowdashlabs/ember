/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberPickerService;
import dev.chojo.ember.feature.members.service.MemberSetupMailService;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.NicknameService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The register of a station over HTTP: each handler checks the member belongs to the station,
 * asks its service and answers with what came back.
 */
class StationMemberRoutesTest {
    private static final int STATION_ID = 3;
    private static final UUID MEMBER_UID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private StationMemberService memberService;
    private MemberViewService memberViews;
    private MemberPickerService pickerService;
    private MemberSetupMailService setupMail;
    private UserTypeChangeService userTypeChanges;
    private RouteHarness harness;

    private static StationMember member(int stationId, boolean former) {
        return new StationMember(7, stationId, MEMBER_UID, 1, former, null, "Mara", StationUserType.MEMBER, null);
    }

    private static MemberWithName named() {
        return new MemberWithName(
                7,
                STATION_ID,
                1,
                "Mara",
                "Mara",
                "Nager",
                null,
                "m@test.com",
                null,
                StationUserType.MEMBER,
                true,
                null,
                null,
                null);
    }

    @BeforeEach
    void setup() {
        memberService = mock(StationMemberService.class);
        memberViews = mock(MemberViewService.class);
        pickerService = mock(MemberPickerService.class);
        setupMail = mock(MemberSetupMailService.class);
        userTypeChanges = mock(UserTypeChangeService.class);
        when(memberService.findById(7)).thenReturn(Optional.of(member(STATION_ID, false)));
        when(memberService.findById(8)).thenReturn(Optional.of(member(99, false)));
        when(memberViews.withCompleteness(any(StationMember.class))).thenReturn(named());
        when(memberViews.withCompleteness(anyList())).thenReturn(List.of(named()));
        harness = RouteHarness.serving(new StationMemberRoutes(
                memberService,
                memberViews,
                pickerService,
                setupMail,
                mock(FormerMemberService.class),
                mock(GdprDeletionService.class),
                mock(MemberIdentityFactory.class),
                mock(RestrictionService.class),
                mock(NicknameService.class),
                userTypeChanges));
    }

    private Response get(HttpClient client, String path) {
        return client.get(PREFIX + path, harness.as(TestSessions.member(STATION_ID, StationPermission.MEMBER_READ)));
    }

    private Response put(HttpClient client, String path, String json) {
        return client.put(
                PREFIX + path,
                body(json),
                harness.as(TestSessions.member(
                        STATION_ID, StationPermission.MEMBER_EDIT, StationPermission.MEMBER_MANAGER)));
    }

    private Response post(HttpClient client, String path) {
        return client.post(
                PREFIX + path, null, harness.as(TestSessions.member(STATION_ID, StationPermission.MEMBER_EDIT)));
    }

    @Test
    void membersAreListedNamedThroughTheViews() {
        when(memberService.findByStation(STATION_ID, true)).thenReturn(List.of(member(STATION_ID, false)));
        when(memberService.findFormerByStation(STATION_ID)).thenReturn(List.of());
        when(memberService.findManaged(7)).thenReturn(List.of());
        when(memberService.findManagers(7)).thenReturn(List.of());
        when(memberViews.richMembers(any(Integer.class), any(Boolean.class), any()))
                .thenReturn(List.of());

        harness.run((server, client) -> {
            assertEquals(
                    "Mara",
                    json(get(client, "/station-members?includeFormer=true"))
                            .get(0)
                            .path("name")
                            .asString());
            assertEquals(200, get(client, "/station-members/former").code());
            assertEquals(
                    200, get(client, "/station-members/rich?includeFormer=true").code());
            assertEquals(200, get(client, "/station-members/7/managed").code());
            assertEquals(200, get(client, "/station-members/7/managers").code());
            assertEquals(
                    "Mara", json(get(client, "/station-members/7")).path("name").asString());
        });

        verify(memberViews).richMembers(STATION_ID, true, java.util.Set.of(StationPermission.MEMBER_READ));
        verify(memberService).findFormerByStation(STATION_ID);
    }

    @Test
    void onlyReadersOfTheRegisterAreSentTheEmail() {
        when(memberService.findByStation(STATION_ID, false)).thenReturn(List.of(member(STATION_ID, false)));
        var inventoryReader = harness.as(TestSessions.member(STATION_ID, StationPermission.INVENTORY_READ));

        harness.run((server, client) -> {
            assertEquals(
                    "m@test.com",
                    json(get(client, "/station-members")).get(0).path("email").asString());
            var picked = json(client.get(PREFIX + "/station-members", inventoryReader))
                    .get(0);
            assertEquals("Mara", picked.path("name").asString());
            assertTrue(picked.path("email").isNull());
        });
    }

    @Test
    void aMemberOfAnotherStationIsNotThere() {
        harness.run((server, client) -> {
            assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(get(client, "/station-members/8")));
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(post(client, "/station-members/8/resend-setup-mail")));
        });

        verify(setupMail, never()).resend(any());
    }

    @Test
    void aMemberIsFoundByUidUnlessFormer() {
        when(memberService.resolveId(STATION_ID, MEMBER_UID)).thenReturn(Optional.of(7));

        harness.run((server, client) -> {
            assertEquals(
                    200, get(client, "/station-members/by-uid/" + MEMBER_UID).code());
            when(memberService.findById(7)).thenReturn(Optional.of(member(STATION_ID, true)));
            assertEquals(
                    MemberRefusal.MEMBER_NOT_HERE_BY_UID,
                    refusalOf(get(client, "/station-members/by-uid/" + MEMBER_UID)));
        });
    }

    @Test
    void thePickerAsksItsServiceWithTheQuery() {
        when(pickerService.search(STATION_ID, "ma", "u", 5)).thenReturn(List.of());

        harness.run((server, client) -> assertEquals(
                200, get(client, "/members/search?q=ma&uid=u&limit=5").code()));

        verify(pickerService).search(STATION_ID, "ma", "u", 5);
    }

    @Test
    void aNewMemberIsAnsweredNamed() {
        when(memberService.create(STATION_ID, 1)).thenReturn(member(STATION_ID, false));

        harness.run((server, client) -> {
            var created = client.post(
                    PREFIX + "/station-members",
                    body("{\"accountId\": 1}"),
                    harness.as(TestSessions.member(STATION_ID, StationPermission.MEMBER_EDIT)));
            assertEquals(201, created.code());
        });
    }

    @Test
    void theSetupMailIsSentAgainThroughItsService() {
        harness.run((server, client) -> assertEquals(
                204, post(client, "/station-members/7/resend-setup-mail").code()));

        verify(setupMail).resend(member(STATION_ID, false));
    }

    @Test
    void typeJoinDateAndManagedAreWrittenThroughTheService() {
        when(memberService.setManaged(eq(7), eq(List.of()), any())).thenReturn(List.of());

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    put(client, "/station-members/7/user-type", "{\"userType\": \"TEAM\"}")
                            .code());
            assertEquals(
                    MemberRefusal.MEMBER_USER_TYPE_NOT_NAMED,
                    refusalOf(put(client, "/station-members/7/user-type", "{}")));
            assertEquals(
                    204,
                    put(client, "/station-members/7/join-date", "{\"joinDate\": \"2019-03-04\"}")
                            .code());
            assertEquals(
                    MemberRefusal.MEMBER_JOIN_DATE_NOT_NAMED,
                    refusalOf(put(client, "/station-members/7/join-date", "{}")));
            assertEquals(
                    200,
                    put(client, "/station-members/7/managed", "{\"managedIds\": []}")
                            .code());
        });

        verify(userTypeChanges).change(7, StationUserType.TEAM);
        verify(memberService).setJoinDate(7, LocalDate.of(2019, 3, 4));
    }

    @Test
    void userTypePermissionsGoThroughTheService() {
        when(memberService.findAllPermissions()).thenReturn(List.of());
        when(memberService.findUserTypePermissions(STATION_ID, StationUserType.TEAM))
                .thenReturn(List.of());
        when(memberService.setUserTypePermissions(STATION_ID, StationUserType.TEAM, List.of(4)))
                .thenReturn(List.of());
        when(memberService.effectiveUserTypePermissions(STATION_ID, StationUserType.TEAM))
                .thenReturn(List.of("LOGIN"));

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.get(
                                    PREFIX + "/permissions",
                                    harness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN)))
                            .code());
            assertEquals(200, get(client, "/user-type-permissions/TEAM").code());
            assertEquals(
                    200,
                    put(client, "/user-type-permissions/TEAM", "{\"permissionIds\": [4]}")
                            .code());
            assertEquals(
                    "LOGIN",
                    json(get(client, "/user-type-permissions/TEAM/effective"))
                            .get(0)
                            .asString());
        });

        verify(memberService).setUserTypePermissions(STATION_ID, StationUserType.TEAM, List.of(4));
    }
}
