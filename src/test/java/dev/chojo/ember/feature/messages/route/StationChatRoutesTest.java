/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.SharedMemberCards;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.service.MessageFixtures;
import dev.chojo.ember.feature.messages.service.MessageTestBase;
import dev.chojo.ember.feature.messages.service.PersonCardService;
import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The station chat routes over HTTP: only holders of the permission open and handle the station's
 * chats, and members find them in their list but cannot leave them or start one themselves.
 */
class StationChatRoutesTest extends MessageTestBase {
    private RouteHarness harness;
    private Station station;
    private StationMember manager;
    private StationMember member;
    private Account memberAccount;

    @BeforeEach
    void serve() {
        var services = MessageFixtures.build();
        harness = RouteHarness.serving(
                        new ConversationRoutes(services.conversations(), services.groups()),
                        new MessageRoutes(services.messages(), services.reactions()),
                        new MessagePeopleRoutes(
                                services.settings(),
                                services.blocks(),
                                new PersonCardService(new MessagePeopleRepository(), mock(SharedMemberCards.class)),
                                services.instance()),
                        new StationChatRoutes(services.stationChats()),
                        new MessageReportRoutes(services.reports()))
                .withStations(stationRepo);
        station = station();
        manager = join(station, account("Mia"));
        memberAccount = account("Anna");
        member = join(station, memberAccount);
    }

    /** What a session holds, its granted permissions with everything they include, as the real session reads it. */
    private static Set<StationPermission> held(Set<StationPermission> granted) {
        var held = EnumSet.noneOf(StationPermission.class);
        held.addAll(granted);
        return StationPermission.expand(held);
    }

    private Consumer<Request.Builder> as(StationMember who, StationPermission... permissions) {
        var signed = signedIn(who, permissions);
        return harness.as(new UserSession(
                signed.account(),
                signed.sessionId(),
                who.stationId(),
                station.uid(),
                who,
                held(signed.permissions()),
                Set.of(),
                null));
    }

    @Test
    void aManagerOpensListsChangesClosesAndReopensAChat() {
        harness.run((server, client) -> {
            String base = PREFIX + "/messages/station-chats";
            var opened =
                    client.post(base, body("{\"name\":\"Alle\"}"), as(manager, StationPermission.MESSAGE_CHAT_MANAGE));
            assertEquals(201, opened.code());
            var chat = json(opened);
            assertEquals(0, chat.get("audience").get("groupIds").size(), "an audience naming nobody");
            assertEquals(2, chat.get("members").asInt());
            String uid = chat.get("uid").asString();

            var changed = client.put(
                    base + "/" + uid,
                    body("{\"name\":\"Wache\",\"includeGuardians\":true,\"audience\":{\"userTypes\":[],"
                            + "\"groupIds\":[],\"tagIds\":[],\"memberIds\":[" + member.id() + "],\"mode\":\"OR\"}}"),
                    as(manager, StationPermission.MESSAGE_CHAT_MANAGE));
            assertEquals(200, changed.code());
            var edited = json(changed);
            assertEquals(
                    member.id(), edited.get("audience").get("memberIds").get(0).asInt());
            assertTrue(edited.get("includeGuardians").asBoolean());
            assertEquals(1, edited.get("members").asInt());
            assertEquals(
                    204,
                    client.post(base + "/" + uid + "/close", null, as(manager, StationPermission.MESSAGE_CHAT_MANAGE))
                            .code());
            var list = json(client.get(base, as(manager, StationPermission.MESSAGE_CHAT_MANAGE)));
            assertEquals("Wache", list.get(0).get("name").asString());
            assertTrue(
                    list.get(0).has("closedAt") && !list.get(0).get("closedAt").isNull());
            assertEquals(
                    204,
                    client.post(base + "/" + uid + "/reopen", null, as(manager, StationPermission.MESSAGE_CHAT_MANAGE))
                            .code());
            assertEquals(
                    MessageRefusal.STATION_CHAT_AUDIENCE_NOT_HERE,
                    refusalOf(client.put(
                            base + "/" + uid,
                            body(
                                    "{\"name\":\"Wache\",\"includeGuardians\":false,\"audience\":{\"userTypes\":[],\"groupIds\":[],"
                                            + "\"tagIds\":[],\"memberIds\":[999999],\"mode\":\"AND\"}}"),
                            as(manager, StationPermission.MESSAGE_CHAT_MANAGE))));
            assertEquals(
                    MessageRefusal.STATION_CHAT_CHANGE_UNSAID,
                    refusalOf(client.put(
                            base + "/" + uid,
                            body("{\"name\":\"Alle\"}"),
                            as(manager, StationPermission.MESSAGE_CHAT_MANAGE))));
            assertEquals(
                    403,
                    client.put(base + "/" + uid, body("{\"name\":\"Meins\"}"), as(member))
                            .code());
        });
    }

    @Test
    void withoutThePermissionNobodyHandlesTheStationsChats() {
        harness.run((server, client) -> {
            var refused = client.post(PREFIX + "/messages/station-chats", body("{\"name\":\"Alle\"}"), as(member));
            assertEquals(403, refused.code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/messages/station-chats", as(member)).code());
        });
    }

    @Test
    void editingTheChatsAndModeratingThemAreGrantedApart() {
        harness.run((server, client) -> {
            assertEquals(
                    403,
                    client.get(PREFIX + "/messages/reports", as(manager, StationPermission.MESSAGE_CHAT_EDIT))
                            .code(),
                    "an editor of the chats does not handle reports");
            assertEquals(
                    200,
                    client.get(PREFIX + "/messages/station-chats", as(manager, StationPermission.MESSAGE_CHAT_EDIT))
                            .code());
            assertEquals(
                    403,
                    client.post(
                                    PREFIX + "/messages/station-chats",
                                    body("{\"name\":\"Alle\"}"),
                                    as(manager, StationPermission.MESSAGE_CHAT_MODERATE))
                            .code(),
                    "a moderator does not open chats");
            assertEquals(
                    200,
                    client.get(PREFIX + "/messages/reports", as(manager, StationPermission.MESSAGE_CHAT_MODERATE))
                            .code());
        });
    }

    @Test
    void aMemberFindsTheChatButCannotLeaveItOrStartOne() {
        harness.run((server, client) -> {
            var opened = json(client.post(
                    PREFIX + "/messages/station-chats",
                    body("{\"name\":\"Alle\"}"),
                    as(manager, StationPermission.MESSAGE_CHAT_MANAGE)));
            String uid = opened.get("uid").asString();

            var list = json(client.get(PREFIX + "/messages/conversations", as(member)));
            var row = list.get(0);
            assertEquals("STATION", row.get("kind").asString());
            assertEquals(
                    station.uid().toString(),
                    row.get("station").get("stationUid").asString());
            assertEquals(
                    MessageRefusal.STATION_CHAT_NOT_LEFT,
                    refusalOf(client.post(PREFIX + "/messages/conversations/" + uid + "/leave", null, as(member))));
            assertEquals(
                    MessageRefusal.START_NOT_A_STATION_CHAT,
                    refusalOf(client.post(
                            PREFIX + "/messages/conversations",
                            body("{\"kind\":\"STATION\",\"people\":[\"%s\"]}".formatted(memberAccount.uid())),
                            as(member))));
        });
    }

    @Test
    void aManagerOfAnotherStationCannotReachTheChat() {
        harness.run((server, client) -> {
            String uid = json(client.post(
                            PREFIX + "/messages/station-chats",
                            body("{\"name\":\"Alle\"}"),
                            as(manager, StationPermission.MESSAGE_CHAT_MANAGE)))
                    .get("uid")
                    .asString();
            Station other = station();
            StationMember stranger = join(other, account("Sven"));
            var signed = signedIn(stranger, StationPermission.MESSAGE_CHAT_MANAGE);
            var asStranger = harness.as(new UserSession(
                    signed.account(),
                    signed.sessionId(),
                    other.id(),
                    other.uid(),
                    stranger,
                    held(signed.permissions()),
                    Set.of(),
                    null));

            assertEquals(
                    MessageRefusal.STATION_CHAT_NOT_HERE,
                    refusalOf(client.post(PREFIX + "/messages/station-chats/" + uid + "/close", null, asStranger)));
        });
    }

    @Test
    void aReportedMessageIsListedAndRemovedByThoseHandlingTheChats() {
        harness.run((server, client) -> {
            String uid = json(client.post(
                            PREFIX + "/messages/station-chats",
                            body("{\"name\":\"Alle\"}"),
                            as(manager, StationPermission.MESSAGE_CHAT_MANAGE)))
                    .get("uid")
                    .asString();
            String messages = PREFIX + "/messages/conversations/" + uid + "/messages";
            int id = json(client.post(messages, body("{\"text\":\"Unfug\"}"), as(member)))
                    .get("id")
                    .asInt();

            assertEquals(
                    204,
                    client.post(messages + "/" + id + "/report", body("{\"reason\":\"Spam\"}"), as(manager))
                            .code());
            assertEquals(
                    403, client.get(PREFIX + "/messages/reports", as(member)).code());
            var open =
                    json(client.get(PREFIX + "/messages/reports", as(manager, StationPermission.MESSAGE_CHAT_MANAGE)));
            assertEquals(1, open.size());
            assertEquals("Spam", open.get(0).get("reason").asString());
            assertEquals(id, open.get(0).get("messageId").asInt());
            int reportId = open.get(0).get("id").asInt();

            assertEquals(
                    204,
                    client.post(
                                    PREFIX + "/messages/reports/" + reportId + "/remove",
                                    null,
                                    as(manager, StationPermission.MESSAGE_CHAT_MANAGE))
                            .code());
            var removed = json(client.get(messages + "/" + id, as(member)));
            assertTrue(removed.get("removedByStation").asBoolean());
            assertTrue(removed.get("removedBy").isNull());
            assertEquals(
                    MessageRefusal.REPORT_NOT_TAKEN,
                    refusalOf(client.post(messages + "/" + id + "/report", body("{}"), as(manager))));
        });
    }

    @Test
    void aMemberCardFindsWhomToWriteTo() {
        harness.run((server, client) -> {
            var found = client.get(PREFIX + "/messages/members/" + member.uid(), as(manager));
            assertEquals(200, found.code());
            assertEquals(memberAccount.uid().toString(), json(found).get("uid").asString());
            assertEquals(
                    204,
                    client.get(PREFIX + "/messages/members/" + manager.uid(), as(manager))
                            .code());
        });
    }
}
