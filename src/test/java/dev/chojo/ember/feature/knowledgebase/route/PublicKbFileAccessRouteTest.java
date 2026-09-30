/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTagService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.HttpStatus;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The door in front of a single file of a public knowledge base, which every route serving one
 * stands behind: the picture of a file and its PDF rendering alike.
 *
 * <p>A picture is the file, a rendering is the file, and a reader outside the station holds no
 * session to be judged by, so the only thing between a file and the open web is what the station
 * published. One rule says so, and these tests hold every route to it rather than to its own copy.
 */
class PublicKbFileAccessRouteTest {
    private static final int STATION_ID = 4;
    private static final UUID STATION_UID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final int FILE_ID = 17;
    private static final byte[] PICTURE = {1, 2, 3};
    private static final byte[] PDF = {4, 5, 6};

    private KnowledgeBaseService kbService;
    private KbAccessService accessService;
    private KbFilePictureService pictureService;
    private KbPdfExportService pdfExportService;
    private PublicKnowledgeBaseRoutes routes;

    private static Station station() {
        return new Station(
                STATION_ID,
                STATION_UID,
                "Wache",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.DENY_ALL,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }

    private static KbFile file(int stationId, KbFileType fileType) {
        return new KbFile(
                FILE_ID,
                stationId,
                3,
                fileType == KbFileType.MARKDOWN ? "Handbuch" : "Einsatzplan.pdf",
                null,
                fileType,
                fileType == KbFileType.MARKDOWN ? "text/markdown" : "application/pdf",
                1024,
                null,
                null,
                null,
                0,
                1,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                false,
                null,
                ContentMode.SIMPLE,
                null);
    }

    @BeforeEach
    void setup() {
        kbService = mock(KnowledgeBaseService.class);
        accessService = mock(KbAccessService.class);
        pictureService = mock(KbFilePictureService.class);
        pdfExportService = mock(KbPdfExportService.class);
        var stationService = mock(StationService.class);
        when(stationService.findByUid(STATION_UID)).thenReturn(Optional.of(station()));
        routes = new PublicKnowledgeBaseRoutes(
                kbService,
                mock(KbContentService.class),
                mock(KbSearchService.class),
                accessService,
                mock(KbTagService.class),
                stationService,
                mock(KbIconService.class),
                mock(KbImageService.class),
                pictureService,
                pdfExportService);
    }

    /**
     * Asks for one rendering of the file, the way a stranger's browser does.
     *
     * @param rendering the last segment of the address: {@code picture} or {@code pdf}
     * @return the response
     */
    private Response ask(String rendering) {
        return RouteHarness.serving(routes)
                .request(client -> client.get(RouteHarness.PREFIX
                        + "/public/kb/%s/files/%d/%s?size=256".formatted(STATION_UID, FILE_ID, rendering)));
    }

    private void published(boolean visible) {
        when(accessService.isPubliclyVisible(PublicKbMode.DENY_ALL, null, FILE_ID))
                .thenReturn(visible);
    }

    private void nothingWasServed() throws Exception {
        verify(pictureService, never()).read(anyInt(), anyInt(), any(), anyInt());
        verify(pdfExportService, never()).renderPublic(any(), any());
    }

    /**
     * Which refusal an endpoint answered a stranger with, having checked it is a {@code 404}.
     *
     * <p>Returned rather than asserted here so each case can also say <em>which</em> refusal it
     * expects. That is the part worth pinning: the three ways a file can be out of reach all have
     * to answer with one code, or the code itself tells a stranger which of the three it was.
     *
     * @param rendering the rendering to ask for
     * @return the refusal it answered with
     */
    private Refusal absent(String rendering) {
        var refused = ask(rendering);
        assertEquals(HttpStatus.NOT_FOUND.getCode(), refused.code());
        return refusalOf(refused);
    }

    @ParameterizedTest
    @ValueSource(strings = {"picture", "pdf"})
    void aFileTheStationKeepsToItselfIsAbsent(String rendering) throws Exception {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.of(file(STATION_ID, KbFileType.MARKDOWN)));
        published(false);

        assertEquals(Refusal.PUBLIC_KB_ENTRY_NOT_HERE, absent(rendering));
        nothingWasServed();
    }

    /**
     * A file of another station reached through this station's address is as good as absent, whatever
     * that other station published it as.
     */
    @ParameterizedTest
    @ValueSource(strings = {"picture", "pdf"})
    void aFileOfAnotherStationIsAbsent(String rendering) throws Exception {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.of(file(STATION_ID + 1, KbFileType.MARKDOWN)));
        published(true);

        assertEquals(Refusal.PUBLIC_KB_ENTRY_NOT_HERE, absent(rendering));
        nothingWasServed();
    }

    @ParameterizedTest
    @ValueSource(strings = {"picture", "pdf"})
    void anUnknownFileIsAbsent(String rendering) throws Exception {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.empty());

        assertEquals(Refusal.PUBLIC_KB_ENTRY_NOT_HERE, absent(rendering));
        nothingWasServed();
    }

    @Test
    void aPublishedFileShowsItsPicture() {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.of(file(STATION_ID, KbFileType.PDF)));
        published(true);
        when(pictureService.read(STATION_ID, FILE_ID, "application/pdf", 256))
                .thenReturn(Optional.of(new MediaContent(PICTURE, "image/webp")));

        var served = ask("picture");

        assertEquals(200, served.code());
        assertTrue(header(served, "Content-Type").startsWith("image/webp"));
        assertTrue(header(served, "Cache-Control").startsWith("public"));
        assertEquals(new String(PICTURE, StandardCharsets.UTF_8), served.body().string());
    }

    @Test
    void aPublishedFileIsRenderedAsPdf() throws Exception {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.of(file(STATION_ID, KbFileType.MARKDOWN)));
        published(true);
        when(pdfExportService.renderPublic(any(), any())).thenReturn(PDF);

        var served = ask("pdf");

        assertEquals(200, served.code());
        assertTrue(header(served, "Content-Type").startsWith("application/pdf"));
        assertTrue(header(served, "Content-Disposition").contains("Handbuch.pdf"));
        assertEquals(new String(PDF, StandardCharsets.UTF_8), served.body().string());
    }

    /**
     * A published file of a kind no picture can be made of falls back to the icon of its kind, which
     * is what the 404 tells the tile to draw.
     */
    @Test
    void aPublishedFileWithoutAPictureShowsNothing() {
        when(kbService.findFile(FILE_ID)).thenReturn(Optional.of(file(STATION_ID, KbFileType.PDF)));
        published(true);
        when(pictureService.read(STATION_ID, FILE_ID, "application/pdf", 256)).thenReturn(Optional.empty());

        assertEquals(Refusal.PUBLIC_KB_ARTICLE_PICTURE_NOT_HERE, absent("picture"));
    }
}
