/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BoardRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldDefinition;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardAttachmentService;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What a board ticket's custom field takes when it is filled in over the route: a date is a day, a
 * choice one of the options written down, a number a whole one, and a required field something.
 */
class BoardFieldValueRouteTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static BoardTicketService tickets;
    private static int boardId;
    private static int laneId;
    private static Map<String, Integer> fieldIds;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Board Field Values");
        account = accountRepo.create("board-field-values@test.com", "Frieda", "Feld");
        member = stationMemberRepo.create(station.id(), account.id());

        var memberService = newStationMemberService(null, null);
        var boards = new BoardService(
                boardRepo,
                memberService,
                newMemberGroupService(),
                new UserTagService(userTagRepo, memberGroupRepo),
                privateTags);
        var backend = localStorage();
        tickets = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                boards,
                mock(DomainEventBus.class),
                memberService,
                memberIdentityFactory,
                memberNameResolver,
                new BoardAttachmentService(
                        new StorageService(new StorageBackendResolver(backend), backend), stationRepo, backend));

        boardId = boards.createWithPreset(station.id(), "Feldwerte", "", "BFV", LanePreset.SIMPLE)
                .id();
        laneId = boards.findLanes(boardId).getFirst().id();
        boards.replaceFields(
                boardId,
                List.of(
                        field("Tag", FieldType.DATE, new BoardFieldConfig.Simple(false)),
                        field("Größe", FieldType.CHOICE, new BoardFieldConfig.Enum(false, List.of("S", "M"))),
                        field("Aufwand", FieldType.NUMBER, new BoardFieldConfig.Simple(false)),
                        field("Pflicht", FieldType.TEXT, new BoardFieldConfig.Simple(true)),
                        field("Prüfer", FieldType.LANE_ASSIGNEE, new BoardFieldConfig.LaneAssignee(false, laneId))));
        fieldIds = boards.findFields(boardId).stream()
                .collect(Collectors.toMap(BoardFieldDefinition::name, BoardFieldDefinition::id, (a, b) -> a));

        harness = RouteHarness.serving(new BoardTicketDetailRoutes(
                        tickets,
                        boards,
                        newCommentService(mock(DomainEventBus.class)),
                        memberNameResolver,
                        new BoardRouteGuards(boards, tickets, memberIdentityFactory)))
                .withStations(stationRepo);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static BoardFieldDefinition field(String name, FieldType type, BoardFieldConfig config) {
        return new BoardFieldDefinition(0, 0, name, type, config, 0);
    }

    private static BoardTicket ticket() {
        return tickets.createTicket(
                boardId,
                laneId,
                "Aufgabe",
                "",
                null,
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
    }

    private static UserSession user() {
        return stationSession(member, StationPermission.BOARD_USE).user();
    }

    private static String fieldPath(BoardTicket ticket, String field) {
        return PREFIX + "/boards/BFV/tickets/%d/fields/%d".formatted(ticket.ticketNumber(), fieldIds.get(field));
    }

    private static Response fill(BoardTicket ticket, String field, String value) {
        return harness.request(client -> client.put(fieldPath(ticket, field), body(value), harness.as(user())));
    }

    private static Response fill(String field, String value) {
        return fill(ticket(), field, value);
    }

    private static Response clear(BoardTicket ticket, String field) {
        return harness.request(client -> client.delete(fieldPath(ticket, field), null, harness.as(user())));
    }

    private static boolean holds(BoardTicket ticket, String field) {
        return tickets.findFieldValues(ticket.id()).stream()
                .anyMatch(value -> value.fieldId() == fieldIds.get(field) && value.value() != null);
    }

    private static void accepted(String field, String value) {
        assertEquals(200, fill(field, value).code(), value + " in " + field);
    }

    private static void refused(String field, String value) {
        assertEquals(
                BoardRefusal.TICKET_FIELD_VALUE_NOT_ACCEPTED, refusalOf(fill(field, value)), value + " in " + field);
    }

    @Test
    void aDateIsADay() {
        accepted("Tag", "{\"value\":\"2026-10-01\"}");
        refused("Tag", "{\"value\":\"irgendwann\"}");
    }

    @Test
    void aChoiceIsOneOfItsOptions() {
        accepted("Größe", "{\"value\":\"M\"}");
        refused("Größe", "{\"value\":\"XL\"}");
    }

    @Test
    void aNumberIsWhole() {
        accepted("Aufwand", "{\"value\":3}");
        refused("Aufwand", "{\"value\":2.5}");
    }

    @Test
    void aRequiredFieldTakesNoEmptyText() {
        accepted("Pflicht", "{\"value\":\"da\"}");
        refused("Pflicht", "{\"value\":\"\"}");
    }

    @Test
    void aLaneAssigneeIsAMember() {
        accepted("Prüfer", "{\"memberId\":" + member.id() + "}");
    }

    @Test
    void whatDoesNotReadAsTheFieldsRecordStaysRefused() {
        refused("Aufwand", "{\"value\":\"drei\"}");
    }

    /** Taking the value away is emptying the field, which a required one does not allow either. */
    @Test
    void aRequiredFieldIsNotCleared() {
        var ticket = ticket();
        assertEquals(200, fill(ticket, "Pflicht", "{\"value\":\"da\"}").code());

        assertEquals(BoardRefusal.TICKET_FIELD_VALUE_REQUIRED, refusalOf(clear(ticket, "Pflicht")));
        assertTrue(holds(ticket, "Pflicht"), "the value stays");
    }

    @Test
    void aFieldThatIsNotRequiredIsCleared() {
        var ticket = ticket();
        assertEquals(200, fill(ticket, "Aufwand", "{\"value\":3}").code());

        assertEquals(204, clear(ticket, "Aufwand").code());
        assertFalse(holds(ticket, "Aufwand"), "the value is gone");
    }
}
