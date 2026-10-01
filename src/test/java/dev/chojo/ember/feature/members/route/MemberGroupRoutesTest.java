/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.StepUpRequiredException;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.service.GroupMembershipService;
import dev.chojo.ember.feature.members.service.GroupRulesService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberGroupSetService;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * The group routes over HTTP: a member's groups written from their own page, members of another
 * station refused, joining a group treated like granting what it grants, sets and bindings.
 */
class MemberGroupRoutesTest extends RepositoryTestBase {
    private final MemberGroupSetRepository sets = new MemberGroupSetRepository();
    private StepUpGuard stepUp;
    private RouteHarness harness;
    private Station station;
    private Station elsewhere;
    private StationMember manager;
    private StationMember child;
    private StationMember stranger;

    @BeforeEach
    void freshStations() {
        stepUp = mock(StepUpGuard.class);
        var memberships = new GroupMembershipService(
                memberGroupRepo, stationMemberRepo, stepUp, () -> memberNameResolver, new DomainEventBus(Set.of()));
        var groupRoutes = new MemberGroupRoutes(
                new MemberGroupService(memberGroupRepo, stationMemberRepo, userTagRepo),
                memberships,
                newStationMemberService(accountRepo, mock(AuthService.class)),
                new MemberViewService(
                        accountRepo, stationMemberRepo, memberIdentityFactory, memberNameResolver, profileFieldService),
                new UserTypeChangeService(stationMemberRepo, memberships),
                new GroupRulesService(memberGroupRepo, sets, memberships));
        harness = RouteHarness.serving(groupRoutes, new MemberGroupSetRoutes(new MemberGroupSetService(sets)))
                .withStations(stationRepo);
        station = stationRepo.create("Group Routes " + System.nanoTime());
        elsewhere = stationRepo.create("Group Routes Elsewhere " + System.nanoTime());
        manager = member(station, StationUserType.MANAGER);
        child = member(station, StationUserType.MEMBER);
        stranger = member(elsewhere, StationUserType.MEMBER);
    }

    @AfterEach
    void removeStations() {
        stationRepo.delete(station.id());
        stationRepo.delete(elsewhere.id());
    }

    private StationMember member(Station at, StationUserType type) {
        var account = accountRepo.create("group-route-" + System.nanoTime() + "@test.com", "Gr", type.name());
        var created = stationMemberRepo.create(at.id(), account.id());
        stationMemberRepo.setUserType(created.id(), type);
        return stationMemberRepo.findById(created.id()).orElseThrow();
    }

    private UserSession managerHolding(StationPermission... permissions) {
        return signedIn(manager, permissions);
    }

    private List<Integer> groupIdsOf(StationMember member) {
        return memberGroupRepo.findGroupsForMember(member.id()).stream()
                .map(MemberGroup::id)
                .sorted()
                .toList();
    }

    @Test
    void anEditorMovesAMemberWithinASetFromTheirPage() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = memberGroupRepo.create(station.id(), "Anfänger");
        var advanced = memberGroupRepo.create(station.id(), "Fortgeschritten");
        memberGroupRepo.assignSet(beginners.id(), levels);
        memberGroupRepo.assignSet(advanced.id(), levels);
        memberGroupRepo.addMember(beginners.id(), child.id());

        harness.run((server, client) -> {
            var response = client.put(
                    "/api/v1/station-members/" + child.id() + "/groups",
                    body("{\"groupIds\":[" + advanced.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_EDIT)));
            assertEquals(200, response.code());
            assertEquals(advanced.id(), json(response).get(0).path("id").asInt());
        });
        assertEquals(List.of(advanced.id()), groupIdsOf(child));
    }

    @Test
    void aMemberOfAnotherStationHasNoGroupsWrittenFromHere() {
        var crew = memberGroupRepo.create(station.id(), "Crew");

        harness.run((server, client) -> {
            var response = client.put(
                    "/api/v1/station-members/" + stranger.id() + "/groups",
                    body("{\"groupIds\":[" + crew.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_EDIT)));
            assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(response));
        });
        assertEquals(List.of(), groupIdsOf(stranger));
    }

    @Test
    void theGroupPageRefusesMemberIdsOfAnotherStation() {
        var crew = memberGroupRepo.create(station.id(), "Crew");

        harness.run((server, client) -> {
            var response = client.put(
                    "/api/v1/groups/" + crew.id() + "/members",
                    body("{\"memberIds\":[" + stranger.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
            assertEquals(MemberRefusal.GROUP_MEMBER_NOT_HERE, refusalOf(response));
        });
        assertTrue(memberGroupRepo.findMembers(crew.id()).isEmpty());
    }

    @Test
    void aGroupManagerCannotJoinAGroupGrantingMoreThanTheyHold() {
        var admins = memberGroupRepo.create(station.id(), "Verwaltung");
        memberGroupRepo.addGroupPermission(
                admins.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                        .orElseThrow()
                        .id());

        harness.run((server, client) -> {
            var own = client.put(
                    "/api/v1/groups/" + admins.id() + "/members",
                    body("{\"memberIds\":[" + manager.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
            assertEquals(MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_ADD, refusalOf(own));

            var viaPage = client.put(
                    "/api/v1/station-members/" + manager.id() + "/groups",
                    body("{\"groupIds\":[" + admins.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
            assertEquals(MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_FOR_MEMBER, refusalOf(viaPage));
        });
        assertEquals(List.of(), groupIdsOf(manager));
    }

    @Test
    void joiningAGroupThatGrantsPermissionsAsksForAFreshProof() {
        var readers = memberGroupRepo.create(station.id(), "Leser");
        memberGroupRepo.addGroupPermission(
                readers.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.MEMBER_READ)
                        .orElseThrow()
                        .id());
        doThrow(new StepUpRequiredException(StepUpCategory.ROLE_CHANGE, Set.of()))
                .when(stepUp)
                .require(any(), any());

        harness.run((server, client) -> {
            var response = client.put(
                    "/api/v1/station-members/" + child.id() + "/groups",
                    body("{\"groupIds\":[" + readers.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_EDIT, StationPermission.MEMBER_READ)));
            assertEquals(401, response.code());
            assertEquals("step_up_required", json(response).path("error").asString());
        });
        assertEquals(List.of(), groupIdsOf(child));
    }

    @Test
    void theGroupPageMovesAMemberOnlyWhenAsked() {
        int levels = sets.create(station.id(), "Stufen").id();
        var beginners = memberGroupRepo.create(station.id(), "Anfänger");
        var advanced = memberGroupRepo.create(station.id(), "Fortgeschritten");
        memberGroupRepo.assignSet(beginners.id(), levels);
        memberGroupRepo.assignSet(advanced.id(), levels);
        memberGroupRepo.addMember(beginners.id(), child.id());

        harness.run((server, client) -> {
            var refused = client.put(
                    "/api/v1/groups/" + advanced.id() + "/members",
                    body("{\"memberIds\":[" + child.id() + "]}"),
                    harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
            assertEquals(MemberRefusal.GROUP_SET_ALREADY_IN, refusalOf(refused));
            var conflict = json(refused).path("conflicts").get(0);
            assertEquals(child.id(), conflict.path("memberId").asInt());
            assertEquals("Anfänger", conflict.path("groups").get(0).asString());

            var moved = client.put(
                    "/api/v1/groups/" + advanced.id() + "/members",
                    body("{\"memberIds\":[" + child.id() + "],\"move\":true}"),
                    harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP)));
            assertEquals(200, moved.code());
        });
        assertEquals(List.of(advanced.id()), groupIdsOf(child));
    }

    @Test
    void aGroupIsBoundAndPutIntoASetWithItsMembersChecked() {
        var parent = member(station, StationUserType.GUARDIAN);
        var crew = memberGroupRepo.create(station.id(), "Crew");
        memberGroupRepo.addMember(crew.id(), child.id());
        memberGroupRepo.addMember(crew.id(), parent.id());
        int levels = sets.create(station.id(), "Stufen").id();

        harness.run((server, client) -> {
            var session = harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP));
            String bound = "{\"name\":\"Crew\",\"position\":0,\"rules\":{\"groupSetId\":" + levels
                    + ",\"userTypes\":[\"MEMBER\"]}";
            var refused = client.put("/api/v1/groups/" + crew.id(), body(bound + "}"), session);
            assertEquals(MemberRefusal.GROUP_BINDING_EXCLUDES_MEMBERS, refusalOf(refused));
            assertEquals(
                    parent.id(),
                    json(refused).path("conflicts").get(0).path("memberId").asInt());

            var saved =
                    client.put("/api/v1/groups/" + crew.id(), body(bound + ",\"removeNonMatching\":true}"), session);
            assertEquals(200, saved.code());
            assertEquals(levels, json(saved).path("groupSetId").asInt());
            assertEquals("MEMBER", json(saved).path("userTypes").get(0).asString());

            var created = client.post(
                    "/api/v1/groups",
                    body("{\"name\":\"Neu\",\"position\":0,\"rules\":{\"groupSetId\":null,\"userTypes\":[\"TEAM\"]}}"),
                    session);
            assertEquals(201, created.code());
            assertEquals("TEAM", json(created).path("userTypes").get(0).asString());
        });
        assertEquals(
                List.of(child.id()),
                memberGroupRepo.findMembers(crew.id()).stream()
                        .map(StationMember::id)
                        .toList());
    }

    @Test
    void aTypeChangeIsAskedAboutFirst() {
        var children = memberGroupRepo.create(station.id(), "Kinder");
        memberGroupRepo.replaceUserTypes(children.id(), List.of(StationUserType.MEMBER));
        memberGroupRepo.addMember(children.id(), child.id());

        harness.run((server, client) -> {
            var session = harness.as(managerHolding(StationPermission.MEMBER_EDIT));
            var asked = client.get("/api/v1/station-members/" + child.id() + "/user-type/TEAM/consequences", session);
            assertEquals(200, asked.code());
            assertEquals("Kinder", json(asked).get(0).path("name").asString());

            var unknown = client.get("/api/v1/station-members/" + child.id() + "/user-type/KING/consequences", session);
            assertEquals(MemberRefusal.USER_TYPE_UNKNOWN_FOR_CONSEQUENCES, refusalOf(unknown));
        });
    }

    @Test
    void setsAreKeptOnTheGroupsPage() {
        harness.run((server, client) -> {
            var session = harness.as(managerHolding(StationPermission.MEMBER_MANAGE_GROUP));
            var created = client.post("/api/v1/group-sets", body("{\"name\":\"Stufen\"}"), session);
            assertEquals(201, created.code());
            int id = json(created).path("id").asInt();

            var renamed = client.put("/api/v1/group-sets/" + id, body("{\"name\":\"Level\"}"), session);
            assertEquals("Level", json(renamed).path("name").asString());
            var listed = client.get("/api/v1/group-sets", session);
            assertEquals("Level", json(listed).get(0).path("name").asString());

            assertEquals(
                    204,
                    client.delete("/api/v1/group-sets/" + id, null, session).code());
            assertEquals(0, json(client.get("/api/v1/group-sets", session)).size());
        });
    }
}
