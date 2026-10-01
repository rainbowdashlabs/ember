/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryValue;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What a waiting list entry takes as an answer, and the shape the list keeps it in.
 *
 * <p>First written down before the field types were brought together. A number is whole now, and
 * every answer is kept in the one shape its type is kept in, with nothing kept for a blank one.
 */
class WaitingListAnswersTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static WaitingListService service;
    private static Station station;
    private int listId;

    @BeforeAll
    static void setup() {
        var emailService = mock(EmailService.class);
        service = new WaitingListService(
                waitingListRepo,
                stationRepo,
                stationMemberRepo,
                newGroupMemberships(),
                new UserTypeChangeService(stationMemberRepo, newGroupMemberships()),
                accountRepo,
                emailService,
                mock(Notifier.class),
                new AccountInviteService(accountRepo, mock(AuthService.class)),
                new WaitlistInvitationMessage(eventRepo, eventFieldRepo, emailService),
                new DomainEventBus(Set.of()));
        station = stationRepo.create("WaitlistAnswersStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @BeforeEach
    void freshList() {
        listId = service.create(
                        station.id(),
                        "Antworten " + UUID.randomUUID(),
                        "",
                        null,
                        180,
                        null,
                        null,
                        5,
                        false,
                        true,
                        null,
                        null)
                .id();
    }

    private @Nullable JsonNode kept(FieldType type, String config, boolean required, JsonNode answer) {
        var field = service.createField(
                listId,
                type.name() + NAMES.incrementAndGet(),
                type,
                WaitingListFieldConfig.parse(config),
                0,
                required,
                true);
        var entry = service.createEntry(
                listId,
                "Kind",
                "Muster",
                List.of(new GuardianInput("", "", "warteliste" + NAMES.incrementAndGet() + "@test.com", "")),
                Map.of(field.id(), answer),
                "");
        return service.findEntryValues(entry.id()).stream()
                .filter(value -> value.fieldId() == field.id())
                .findFirst()
                .map(WaitingListEntryValue::value)
                .orElse(null);
    }

    private void refused(FieldType type, String config, JsonNode answer) {
        assertThrows(BadRequestResponse.class, () -> kept(type, config, false, answer), answer + " under " + type);
    }

    @Test
    void aNumberIsWholeAndRefusesText() {
        assertEquals("3", String.valueOf(kept(FieldType.NUMBER, "{}", false, StringNode.valueOf("3"))));
        refused(FieldType.NUMBER, "{}", DecimalNode.valueOf(new BigDecimal("2.5")));
        refused(FieldType.NUMBER, "{}", StringNode.valueOf("zwei"));
    }

    @Test
    void aDayIsAnIsoDayInAString() {
        assertEquals(
                StringNode.valueOf("2019-05-01"),
                kept(FieldType.BIRTH_DATE, "{}", false, StringNode.valueOf("2019-05-01")));
        refused(FieldType.DATE, "{}", StringNode.valueOf("01.05.2019"));
    }

    @Test
    void aYesOrNoIsKeptAsABoolean() {
        assertEquals(BooleanNode.TRUE, kept(FieldType.BOOLEAN, "{}", false, BooleanNode.TRUE));
        assertEquals(BooleanNode.TRUE, kept(FieldType.BOOLEAN, "{}", false, StringNode.valueOf("1")));
        refused(FieldType.BOOLEAN, "{}", StringNode.valueOf("ja"));
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals(
                StringNode.valueOf("Vormittag"),
                kept(
                        FieldType.CHOICE,
                        "{\"options\":[\"Vormittag\",\"Nachmittag\"]}",
                        false,
                        StringNode.valueOf("Vormittag")));
        refused(FieldType.CHOICE, "{\"options\":[\"Vormittag\",\"Nachmittag\"]}", StringNode.valueOf("Abend"));
    }

    @Test
    void aRequiredQuestionMayStayEmptyAndKeepsNothing() {
        assertNull(kept(FieldType.TEXT, "{}", true, StringNode.valueOf("")));
    }

    @Test
    void aClearedAnswerKeepsNothing() {
        var field = service.createField(
                listId,
                "Notiz" + NAMES.incrementAndGet(),
                FieldType.TEXT,
                WaitingListFieldConfig.parse("{}"),
                0,
                false,
                true);
        var guardians = List.of(new GuardianInput("", "", "warteliste" + NAMES.incrementAndGet() + "@test.com", ""));
        var entry = service.createEntry(
                listId, "Kind", "Muster", guardians, Map.of(field.id(), StringNode.valueOf("x")), "");

        service.updateEntry(entry.id(), "Kind", "Muster", guardians, "", Map.of(field.id(), StringNode.valueOf("")));

        assertTrue(service.findEntryValues(entry.id()).isEmpty());
    }
}
