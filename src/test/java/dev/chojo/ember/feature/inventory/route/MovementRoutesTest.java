/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.Glyph;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.service.GlyphResolver;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.MovementExportService;
import dev.chojo.ember.feature.inventory.service.MovementGuards;
import dev.chojo.ember.feature.inventory.service.MovementTargeting;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.util.ExportedDocument;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The movement routes over HTTP: who may see and start a movement is the guards' answer, and the
 * names on a row come from the services that own them.
 */
class MovementRoutesTest {
    private static final int STATION = 3;
    private static final ItemMovement MOVEMENT = new ItemMovement(
            9,
            STATION,
            MovementPurpose.RETURN,
            1,
            null,
            MEMBER_ID,
            null,
            null,
            null,
            null,
            null,
            MovementState.OPEN,
            "zu klein",
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            null,
            null,
            false);

    private ItemMovementService movementService;
    private MovementGuards guards;
    private StationMemberService memberService;
    private MemberNameResolver names;
    private MovementExportService exportService;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        movementService = mock(ItemMovementService.class);
        guards = mock(MovementGuards.class);
        memberService = mock(StationMemberService.class);
        names = mock(MemberNameResolver.class);
        exportService = mock(MovementExportService.class);
        var targeting = mock(MovementTargeting.class);
        var glyphs = mock(GlyphResolver.class);
        when(targeting.belongsOn(any()))
                .thenReturn(new MovementTargeting.Target(ItemOwner.STATION, null, MovementParty.MEMBER, 1));
        when(glyphs.forInventoryId(any())).thenReturn(Glyph.NONE);
        when(glyphs.forItemId(anyInt())).thenReturn(Glyph.NONE);
        when(names.called(MEMBER_ID)).thenReturn("Mara");
        when(guards.actorOf(any(), any())).thenReturn(new ItemMovementService.Actor(MEMBER_ID, false, false));
        harness = RouteHarness.serving(new MovementRoutes(
                movementService,
                guards,
                memberService,
                names,
                mock(MemberIdentityFactory.class),
                mock(LossReportService.class),
                mock(InventoryService.class),
                targeting,
                glyphs,
                exportService,
                mock(SelfCheckService.class)));
    }

    private Response get(HttpClient client, String path) {
        return client.get(PREFIX + path, harness.as(TestSessions.member(STATION, StationPermission.USER)));
    }

    private Response post(HttpClient client, String path, String json) {
        return client.post(
                PREFIX + path,
                body(json),
                harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_MANAGER)));
    }

    @Test
    void theListsHoldWhatTheGuardsLetTheCallerSee() {
        when(movementService.findByStation(STATION)).thenReturn(List.of(MOVEMENT));
        when(movementService.findAtMemberByStation(STATION)).thenReturn(List.of(MOVEMENT));
        when(guards.visibleAmong(any(), eq(List.of(MOVEMENT)))).thenReturn(List.of(MOVEMENT));

        harness.run((server, client) -> {
            assertEquals(
                    "Mara",
                    json(get(client, "/movements")).path(0).path("memberName").asString());
            assertEquals(
                    9,
                    json(get(client, "/movements/at-member")).path(0).path("id").asInt());
        });
    }

    @Test
    void oneMovementIsOpenedThroughTheGuards() {
        when(guards.requireVisible(any(), eq(9))).thenReturn(MOVEMENT);
        when(guards.requireVisible(any(), eq(10))).thenThrow(Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS.raise());

        harness.run((server, client) -> {
            assertEquals(
                    "zu klein",
                    json(get(client, "/movements/9"))
                            .path("movement")
                            .path("reason")
                            .asString());
            assertEquals(Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS, refusalOf(get(client, "/movements/10")));
        });
    }

    @Test
    void aMovementIsStartedOnlyForSomebodyTheGuardsAllow() {
        when(movementService.create(anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(MOVEMENT);
        doThrow(Refusal.MEMBER_NOT_YOURS_TO_ACT_FOR.raise()).when(guards).requireMayStartFor(any(), eq(13));

        harness.run((server, client) -> {
            assertEquals(
                    201,
                    client.post(
                                    PREFIX + "/movements",
                                    body("{\"purpose\": \"RETURN\", \"memberId\": %d}".formatted(MEMBER_ID)),
                                    harness.as(TestSessions.member(STATION, StationPermission.USER)))
                            .code());
            assertEquals(
                    Refusal.MEMBER_NOT_YOURS_TO_ACT_FOR,
                    refusalOf(client.post(
                            PREFIX + "/movements",
                            body("{\"purpose\": \"RETURN\", \"memberId\": 13}"),
                            harness.as(TestSessions.member(STATION, StationPermission.USER)))));
        });

        verify(movementService)
                .create(
                        eq(STATION),
                        eq(MovementPurpose.RETURN),
                        eq(MEMBER_ID),
                        eq("Mara"),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(""),
                        any(),
                        any());
    }

    @Test
    void everythingIsAskedBackOnlyFromAMemberOfTheStation() {
        var member = new StationMember(
                MEMBER_ID, STATION, UUID.randomUUID(), 1, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH);
        var elsewhere = new StationMember(
                14, 4, UUID.randomUUID(), 2, false, null, "Olaf", StationUserType.MEMBER, LocalDate.EPOCH);
        when(memberService.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberService.findById(14)).thenReturn(Optional.of(elsewhere));
        when(movementService.requestEverythingBack(eq(STATION), eq(MEMBER_ID), eq("Mara"), any()))
                .thenReturn(List.of(MOVEMENT));

        harness.run((server, client) -> {
            var started = post(client, "/movements/return-everything", "{\"memberId\": %d}".formatted(MEMBER_ID));
            assertEquals(1, json(started).size());
            assertEquals(
                    Refusal.MEMBER_NOT_AT_THIS_STATION,
                    refusalOf(post(client, "/movements/return-everything", "{\"memberId\": 14}")));
            assertEquals(
                    Refusal.RETURN_OF_EVERYTHING_NEEDS_A_MEMBER,
                    refusalOf(post(client, "/movements/return-everything", "{}")));
        });
    }

    @Test
    void theSheetIsSignedWithTheRegisterNameOfWhoeverMadeIt() {
        when(names.official(MEMBER_ID)).thenReturn("Mara Nager");
        when(exportService.exportPdf(eq(STATION), any(), any(), eq("Mara Nager")))
                .thenReturn(Optional.of(new ExportedDocument(new byte[] {1, 2}, "bewegungen.pdf")));

        harness.run((server, client) -> {
            var pdf = post(client, "/movements/export", "{}");
            assertEquals(200, pdf.code());
            assertTrue(RouteHarness.header(pdf, "Content-Type").startsWith("application/pdf"));
        });
    }

    @Test
    void anEmptySheetIsRefusedAndAnUnknownAuthorSignsAsSomebody() {
        when(exportService.exportPdf(eq(STATION), any(), any(), eq("?"))).thenReturn(Optional.empty());

        harness.run((server, client) ->
                assertEquals(Refusal.MOVEMENT_LIST_EMPTY, refusalOf(post(client, "/movements/export", "{}"))));
    }
}
