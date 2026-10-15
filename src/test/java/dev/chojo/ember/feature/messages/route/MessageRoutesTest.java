/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.service.MessageFixtures;
import dev.chojo.ember.feature.messages.service.MessageTestBase;
import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every message route over HTTP, through the real access gate, with the real services behind it:
 * a conversation is started, written in, read, reacted to, changed and left, and the account-wide
 * routes answer for the signed-in account and nobody else.
 */
class MessageRoutesTest extends MessageTestBase {
    private RouteHarness harness;
    private Account anna;
    private Account ben;
    private Account cora;

    @BeforeEach
    void serve() {
        var services = MessageFixtures.build();
        harness = RouteHarness.serving(
                new ConversationRoutes(services.conversations(), services.groups()),
                new MessageRoutes(services.messages(), services.reactions()),
                new MessagePeopleRoutes(services.settings(), services.blocks()),
                new MessageEventRoutes(services.signals()));
        Station station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        cora = memberOf(station, "Cora");
    }

    private Consumer<Request.Builder> as(Account account) {
        return harness.as(new UserSession(account, 1, null, null, null, Set.of(), Set.of(), null));
    }

    @Test
    void aDirectConversationIsStartedWrittenInAndRead() {
        harness.run((server, client) -> {
            var started = client.post(
                    PREFIX + "/messages/conversations",
                    body("{\"kind\":\"DIRECT\",\"people\":[\"%s\"]}".formatted(ben.uid())),
                    as(anna));
            assertEquals(201, started.code());
            String uid = json(started).get("uid").asString();
            String base = PREFIX + "/messages/conversations/" + uid;

            var sent = client.post(base + "/messages", body("{\"text\":\"Hallo Ben\"}"), as(anna));
            assertEquals(201, sent.code());
            int id = json(sent).get("id").asInt();

            var list = json(client.get(PREFIX + "/messages/conversations", as(ben)));
            assertEquals(1, list.get(0).get("unread").asInt());
            assertEquals("Hallo Ben", list.get(0).get("newest").get("text").asString());
            assertEquals(
                    1,
                    json(client.get(PREFIX + "/messages/status", as(ben)))
                            .get("unread")
                            .asInt());

            var page = json(client.get(base + "/messages", as(ben)));
            assertEquals("Hallo Ben", page.get("messages").get(0).get("content").asString());
            assertFalse(page.get("hasMore").asBoolean());
            assertEquals(
                    1,
                    json(client.get(base + "/messages?before=" + (id + 1), as(ben)))
                            .get("messages")
                            .size());
            assertEquals(
                    0,
                    json(client.get(base + "/messages?after=" + id, as(ben)))
                            .get("messages")
                            .size());

            assertEquals(
                    204,
                    client.put(base + "/read", body("{\"messageId\":%d}".formatted(id)), as(ben))
                            .code());
            assertEquals(
                    0,
                    json(client.get(PREFIX + "/messages/status", as(ben)))
                            .get("unread")
                            .asInt());

            var detail = json(client.get(base, as(ben)));
            assertEquals("DIRECT", detail.get("kind").asString());
            assertEquals(id, detail.get("lastReadMessageId").asInt());

            assertEquals(MessageRefusal.CONVERSATION_NOT_HERE, refusalOf(client.get(base, as(cora))));
        });
    }

    @Test
    void aMessageIsAnsweredReactedToChangedAndDeleted() {
        harness.run((server, client) -> {
            String uid = json(client.post(
                            PREFIX + "/messages/conversations",
                            body("{\"kind\":\"DIRECT\",\"people\":[\"%s\"]}".formatted(ben.uid())),
                            as(anna)))
                    .get("uid")
                    .asString();
            String messages = PREFIX + "/messages/conversations/" + uid + "/messages";
            int question = json(client.post(messages, body("{\"text\":\"Kommst du?\"}"), as(ben)))
                    .get("id")
                    .asInt();

            var answer = json(
                    client.post(messages, body("{\"text\":\"Ja\",\"replyToId\":%d}".formatted(question)), as(anna)));
            assertEquals(question, answer.get("replyTo").get("id").asInt());

            var reacted =
                    json(client.post(messages + "/" + question + "/reactions", body("{\"emoji\":\"👍\"}"), as(anna)));
            var group = reacted.get("reactions").get(0);
            assertEquals("👍", group.get("emoji").asString());
            assertTrue(group.get("mine").asBoolean());
            assertEquals(
                    1,
                    json(client.get(messages + "/" + question, as(ben)))
                            .get("reactions")
                            .size());

            String emoji = URLEncoder.encode("👍", StandardCharsets.UTF_8);
            var takenBack =
                    json(client.delete(messages + "/" + question + "/reactions?emoji=" + emoji, null, as(anna)));
            assertEquals(0, takenBack.get("reactions").size());

            var changed = json(client.put(messages + "/" + question, body("{\"text\":\"Kommst du mit?\"}"), as(ben)));
            assertEquals("Kommst du mit?", changed.get("content").asString());
            assertFalse(changed.get("editedAt").isNull());
            assertEquals(
                    MessageRefusal.EDIT_NOT_YOURS,
                    refusalOf(client.put(messages + "/" + question, body("{\"text\":\"Meins\"}"), as(anna))));

            assertEquals(
                    204, client.delete(messages + "/" + question, null, as(ben)).code());
            assertTrue(json(client.get(messages + "/" + question, as(anna)))
                    .get("deleted")
                    .asBoolean());
            assertEquals(MessageRefusal.MESSAGE_EMPTY, refusalOf(client.post(messages, body("{}"), as(anna))));
        });
    }

    @Test
    void aGroupIsStartedRenamedChangedAndLeft() {
        harness.run((server, client) -> {
            var started = json(client.post(
                    PREFIX + "/messages/conversations",
                    body("{\"kind\":\"GROUP\",\"people\":[\"%s\"],\"name\":\"Runde\"}".formatted(ben.uid())),
                    as(anna)));
            assertEquals("GROUP", started.get("kind").asString());
            assertEquals(anna.uid().toString(), started.get("ownerUid").asString());
            String base =
                    PREFIX + "/messages/conversations/" + started.get("uid").asString();

            assertEquals(
                    204,
                    client.put(base + "/name", body("{\"name\":\"Neue Runde\"}"), as(anna))
                            .code());
            assertEquals(
                    204,
                    client.post(base + "/people", body("{\"people\":[\"%s\"]}".formatted(cora.uid())), as(ben))
                            .code());
            assertEquals(3, json(client.get(base, as(cora))).get("members").size());
            assertEquals(
                    204,
                    client.delete(base + "/people/" + cora.uid(), null, as(anna))
                            .code());
            assertEquals(
                    204,
                    client.put(base + "/mute", body("{\"muted\":true}"), as(ben))
                            .code());
            assertTrue(json(client.get(base, as(ben))).get("muted").asBoolean());
            assertEquals(204, client.post(base + "/leave", null, as(anna)).code());
            assertEquals(
                    ben.uid().toString(),
                    json(client.get(base, as(ben))).get("ownerUid").asString());
            assertEquals(
                    MessageRefusal.GROUP_NEEDS_SOMEBODY,
                    refusalOf(client.post(PREFIX + "/messages/conversations", body("{\"kind\":\"GROUP\"}"), as(anna))));
        });
    }

    @Test
    void peopleAreFoundBlockedAndTheMailTurnedOff() {
        harness.run((server, client) -> {
            var found = json(client.get(PREFIX + "/messages/people?search=Ben", as(anna)));
            assertEquals(1, found.size());
            assertEquals(ben.uid().toString(), found.get(0).get("uid").asString());
            assertFalse(found.get(0).get("stationName").asString().isBlank());

            assertEquals(
                    204,
                    client.post(PREFIX + "/messages/blocks", body("{\"person\":\"%s\"}".formatted(ben.uid())), as(anna))
                            .code());
            assertEquals(
                    1, json(client.get(PREFIX + "/messages/blocks", as(anna))).size());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/messages/blocks/" + ben.uid(), null, as(anna))
                            .code());
            assertEquals(
                    0, json(client.get(PREFIX + "/messages/blocks", as(anna))).size());
            assertEquals(
                    MessageRefusal.BLOCK_PERSON_NOT_HERE,
                    refusalOf(client.post(PREFIX + "/messages/blocks", body("{}"), as(anna))));

            var status = json(client.put(PREFIX + "/messages/settings", body("{\"mail\":false}"), as(anna)));
            assertTrue(status.get("available").asBoolean());
            assertFalse(status.get("mail").asBoolean());
        });
    }

    @Test
    void theStreamNeedsASignInAndAnswersAPlainRequestWithNothing() {
        harness.run((server, client) -> {
            assertEquals(401, client.get(PREFIX + "/messages/events").code());
            assertEquals(200, client.get(PREFIX + "/messages/events", as(anna)).code());
        });
    }
}
