/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormDirectoryService;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.page.entity.PageUsingForm;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.ExportedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Finding forms, changing how far one reaches and exporting its answers, over HTTP.
 */
class FormDirectoryRoutesTest {
    private FormService forms;
    private FormDirectoryService directory;
    private GuardianPolicy guardians;
    private FormResponseExportService exports;
    private StationService stations;
    private PageService pages;
    private RouteHarness harness;
    private Form form;

    @BeforeEach
    void setup() {
        forms = mock(FormService.class);
        directory = mock(FormDirectoryService.class);
        guardians = mock(GuardianPolicy.class);
        exports = mock(FormResponseExportService.class);
        stations = mock(StationService.class);
        pages = mock(PageService.class);
        form = new Form(
                7,
                3,
                "Umfrage",
                "",
                Form.FormStatus.OPEN,
                false,
                false,
                false,
                null,
                null,
                null,
                1,
                null,
                null,
                null,
                null,
                false,
                FormPurpose.POLL,
                FormVisibility.PUBLIC,
                UUID.randomUUID(),
                0,
                null,
                null,
                null);
        when(forms.findById(7)).thenReturn(Optional.of(form));
        harness = RouteHarness.serving(new FormRoutes(
                forms, directory, guardians, mock(FormAnalyticsAssembler.class), exports, stations, pages));
    }

    @Test
    void thePickerAsksTheDirectoryForTheKindNamed() {
        when(directory.pickable(anyInt(), any(), any(), any(), anyInt())).thenReturn(List.of());

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.PAGE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/forms/search?purpose=POLL&q=sommer&limit=5", editor)
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/forms/search?purpose=POLL&uid=abc", editor)
                            .code());
            assertEquals(FormRefusal.FORM_KIND_NOT_NAMED, refusalOf(client.get(PREFIX + "/forms/search", editor)));
        });

        verify(directory).pickable(3, FormPurpose.POLL, null, "sommer", 5);
        verify(directory).pickable(3, FormPurpose.POLL, "abc", null, 10);
    }

    @Test
    void aMemberSeesTheFormsTheyOrTheirWardsCanAnswer() {
        var ward = mock(StationMember.class);
        when(ward.id()).thenReturn(20);
        when(guardians.wards(any())).thenReturn(List.of(ward));
        when(directory.available(anyInt(), anyInt(), any(Boolean.class), any())).thenReturn(List.of());

        var answer = harness.request(client ->
                client.get(PREFIX + "/forms/available", harness.as(TestSessions.member(3, StationPermission.USER))));

        assertEquals(200, answer.code());
        verify(directory).available(3, TestSessions.MEMBER_ID, false, List.of(20));
    }

    @Test
    void closingAFormToItsLinkAnswersWithThePagesItStrands() {
        when(forms.setVisibility(7, FormVisibility.UNLISTED)).thenReturn(true);
        when(pages.pagesStrandedBy(form, FormVisibility.UNLISTED))
                .thenReturn(List.of(new PageUsingForm(1, "Start", PageVisibility.values()[0])));

        var answer = harness.request(client -> client.put(
                PREFIX + "/forms/7/visibility",
                body("{\"visibility\": \"UNLISTED\"}"),
                harness.as(TestSessions.member(3, StationPermission.POLL_CREATE))));

        assertEquals(
                "Start", json(answer).path("stillHeldBy").get(0).path("title").asString());
    }

    @Test
    void theAnswersAreExportedWithTheStationTheyBelongTo() throws Exception {
        var station = mock(Station.class);
        when(stations.findById(3)).thenReturn(Optional.of(station));
        when(exports.export(eq(7), eq("Umfrage"), eq(station), any(), any()))
                .thenReturn(new ExportedDocument("a;b".getBytes(), "antworten.csv"));

        harness.run((server, client) -> {
            var viewer = harness.as(TestSessions.member(3, StationPermission.POLL_VIEW_RESULTS));
            var exported = client.get(PREFIX + "/forms/7/responses/export", viewer);
            assertTrue(header(exported, "Content-Type").startsWith("text/csv"));
            when(stations.findById(3)).thenReturn(Optional.empty());
            assertEquals(
                    FormRefusal.STATION_NOT_HERE_FOR_FORM_EXPORT,
                    refusalOf(client.get(PREFIX + "/forms/7/responses/export", viewer)));
        });
    }
}
