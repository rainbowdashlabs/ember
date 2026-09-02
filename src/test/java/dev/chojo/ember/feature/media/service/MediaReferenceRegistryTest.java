/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

class MediaReferenceRegistryTest extends RepositoryTestBase {
    private static MediaReferenceRegistry registry;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int pageId;
    private static int containerId;

    /**
     * A media file is addressed by the hash of its bytes, so a body references one by carrying
     * that hash inside a URL. These stand in for real uploads.
     */
    private static final String CELL_HASH = "a".repeat(64);

    private static final String NEWS_HASH = "b".repeat(64);
    private static final String KB_HASH = "c".repeat(64);
    private static final String TICKET_HASH = "d".repeat(64);
    private static final String EVENT_HASH = "e".repeat(64);
    private static final String TEMPLATE_HASH = "f".repeat(64);

    @BeforeAll
    static void setup() {
        registry = new MediaReferenceRegistry(contentContainerRepo);
        station = stationRepo.create("MediaReferenceStation");
        account = accountRepo.create("media-ref@test.com", "Media", "Reference");
        member = stationMemberRepo.create(station.id(), account.id());
        pageId = pageRepo.create(station.id(), "Reference Page", "reference-page", null, member.id())
                .id();
        containerId = contentContainerRepo.create(station.id()).id();
        pageRepo.setContainer(pageId, containerId);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static String url(String hash) {
        return "![Bild](/api/v1/public/media/" + station.uid() + "/" + hash + ")";
    }

    @Test
    void everyRegisteredBodyContributesItsReferences() {
        int rowId = contentContainerRepo.insertRow(containerId, 0);
        contentContainerRepo.insertCell(rowId, 0, 100.0, CellContentType.IMAGE, CELL_HASH, CellConfig.EMPTY);

        var news = newsRepo.create(
                station.id(), "Mit Bild", url(NEWS_HASH), "<p></p>", new MemberIdentity(station.uid(), member.uid()));

        var board = boardRepo.create(station.id(), "Referenzboard", null, "REF");
        var lane = boardRepo.createLane(board.id(), "Offen", null, 0);
        var creator = new MemberIdentity(station.uid(), member.uid());
        var ticket = boardTicketRepo.createTicket(
                board.id(), lane.id(), 1, "Ticket", url(TICKET_HASH), null, TicketPriority.MEDIUM, null, 0, creator);

        query("INSERT INTO station_event(station_id, name, description, start_time, end_time)"
                        + " VALUES (:station_id, 'Übung', :description, now(), now());")
                .single(call().bind("station_id", station.id()).bind("description", url(EVENT_HASH)))
                .insert();
        query(
                        "INSERT INTO event_template(station_id, name, description) VALUES (:station_id, 'Vorlage', :description);")
                .single(call().bind("station_id", station.id()).bind("description", url(TEMPLATE_HASH)))
                .insert();

        var kbFile = knowledgeBaseRepo.createFile(
                station.id(), null, "Artikel", "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
        knowledgeBaseRepo.storeTextContent(kbFile.id(), url(KB_HASH));

        try {
            var referenced = new HashSet<>(registry.collect(station.id()));
            assertTrue(referenced.contains(CELL_HASH), "a cell of a page");
            assertTrue(referenced.contains(NEWS_HASH), "a news body");
            assertTrue(referenced.contains(TICKET_HASH), "a ticket description");
            assertTrue(referenced.contains(EVENT_HASH), "an event description");
            assertTrue(referenced.contains(TEMPLATE_HASH), "an event template description");
            assertTrue(referenced.contains(KB_HASH), "a knowledge-base article");
        } finally {
            knowledgeBaseRepo.purgeFile(kbFile.id());
            boardTicketRepo.deleteTicket(ticket.id());
            boardRepo.delete(board.id());
            newsRepo.delete(news.id());
            contentContainerRepo.deleteRows(containerId);
        }
    }

    @Test
    void aPagesSocialImageCountsAsAReference() {
        var file = mediaFileRepo.create(null, station.id(), "0".repeat(64), "og.png", "image/png", 8);
        try {
            pageRepo.updateMeta(pageId, "Reference Page", "reference-page", null, null, file.id());
            var referenced = registry.collect(station.id());
            assertTrue(
                    referenced.contains(file.contentHash()),
                    "pruning a page's social image would take the preview off every share of it");
        } finally {
            pageRepo.updateMeta(pageId, "Reference Page", "reference-page", null, null, null);
            mediaFileRepo.delete(file.id());
        }
    }

    @Test
    void anAttachedFileIsNamedOutrightRatherThanRead() {
        var file = mediaFileRepo.create(null, station.id(), "1".repeat(64), "flyer.pdf", "application/pdf", 8);
        var news = newsRepo.create(
                station.id(), "Ohne Bild", "Nur Text", "<p></p>", new MemberIdentity(station.uid(), member.uid()));
        query("INSERT INTO news_attachment(news_id, file_id) VALUES (:news_id, :file_id);")
                .single(call().bind("news_id", news.id()).bind("file_id", file.id()))
                .insert();
        try {
            var referenced = registry.collect(station.id());
            assertTrue(referenced.contains(file.contentHash()));
            assertTrue(referenced.contains(String.valueOf(file.id())));
            assertEquals(1, registry.handedOutBy(file.id()));
        } finally {
            query("DELETE FROM news_attachment WHERE file_id = :file_id;")
                    .single(call().bind("file_id", file.id()))
                    .delete();
            newsRepo.delete(news.id());
            mediaFileRepo.delete(file.id());
        }
    }

    @Test
    void aFileNothingHandsOutIsNotHandedOut() {
        assertEquals(0, registry.handedOutBy(99999));
    }

    @Test
    void cellConfigIsWalkedForImageFields() {
        var out = new HashSet<String>();
        var gallery = CellConfig.parse(
                CellContentType.IMAGE_GALLERY,
                CellConfig.MAPPER.readTree(
                        "{\"items\":[{\"imageHash\":\"%s\"},{\"imageHash\":\"%s\"}]}".formatted(CELL_HASH, NEWS_HASH)));
        registry.collectFromCell(CellContentType.IMAGE_GALLERY, "", gallery, out);
        assertTrue(out.contains(CELL_HASH), "a gallery item names its image inside the cell config");
        assertTrue(out.contains(NEWS_HASH));

        out.clear();
        registry.collectFromCell(CellContentType.IMAGE, "  ", null, out);
        assertTrue(out.isEmpty(), "a blank image cell references nothing");
    }

    @Test
    void nestedRowsAreRecursedInto() {
        var out = new HashSet<String>();
        var nested = CellConfig.parse(
                CellContentType.NESTED_ROWS,
                CellConfig.MAPPER.readTree(("{\"rows\":[{\"cells\":["
                                + "{\"contentType\":\"IMAGE\",\"content\":\"%s\"},"
                                + "{\"contentType\":\"IMAGE\",\"content\":null},"
                                + "{\"contentType\":\"MARKDOWN\",\"content\":\"text\"}]}]}")
                        .formatted(KB_HASH)));
        registry.collectFromCell(CellContentType.NESTED_ROWS, "", nested, out);
        assertTrue(
                out.contains(KB_HASH),
                "a nested row carries its cells inside the config, so the walk has to recurse into it");
        assertEquals(1, out.size(), "only the cells that name an image contribute");
    }
}
