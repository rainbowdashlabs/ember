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
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.waitinglist.entity.GuardianInput;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldConfig;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListFieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * What a waiting list entry takes as an answer today, and the shape the list keeps it in.
 *
 * <p>Written down before the field types are brought together, so that every later change can show
 * which of these it meant to change and that it left the rest alone.
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

    private JsonNode kept(WaitingListFieldType type, String config, boolean required, JsonNode answer) {
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
                .orElseThrow()
                .value();
    }

    private void refused(WaitingListFieldType type, String config, JsonNode answer) {
        assertThrows(BadRequestResponse.class, () -> kept(type, config, false, answer), answer + " under " + type);
    }

    @Test
    void aNumberTakesAFractionAndRefusesText() {
        assertEquals(
                "2.5",
                kept(WaitingListFieldType.NUMBER, "{}", false, DecimalNode.valueOf(new BigDecimal("2.5")))
                        .toString());
        refused(WaitingListFieldType.NUMBER, "{}", StringNode.valueOf("zwei"));
    }

    @Test
    void aDayIsAnIsoDayInAString() {
        assertEquals(
                StringNode.valueOf("2019-05-01"),
                kept(WaitingListFieldType.BIRTH_DATE, "{}", false, StringNode.valueOf("2019-05-01")));
        refused(WaitingListFieldType.DATE, "{}", StringNode.valueOf("01.05.2019"));
    }

    @Test
    void aYesOrNoIsKeptAsABooleanOrAsText() {
        assertEquals(BooleanNode.TRUE, kept(WaitingListFieldType.BOOLEAN, "{}", false, BooleanNode.TRUE));
        assertEquals(StringNode.valueOf("1"), kept(WaitingListFieldType.BOOLEAN, "{}", false, StringNode.valueOf("1")));
        refused(WaitingListFieldType.BOOLEAN, "{}", StringNode.valueOf("ja"));
    }

    @Test
    void aChoiceTakesOnlyItsOptions() {
        assertEquals(
                StringNode.valueOf("Vormittag"),
                kept(
                        WaitingListFieldType.ENUM,
                        "{\"options\":[\"Vormittag\",\"Nachmittag\"]}",
                        false,
                        StringNode.valueOf("Vormittag")));
        refused(WaitingListFieldType.ENUM, "{\"options\":[\"Vormittag\",\"Nachmittag\"]}", StringNode.valueOf("Abend"));
    }

    @Test
    void aRequiredQuestionMayStayEmpty() {
        assertEquals(StringNode.valueOf(""), kept(WaitingListFieldType.TEXT, "{}", true, StringNode.valueOf("")));
    }
}
