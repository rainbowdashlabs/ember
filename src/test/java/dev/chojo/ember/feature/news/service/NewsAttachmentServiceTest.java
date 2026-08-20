/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.media.MediaTestSupport;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.repository.NewsAttachmentRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewsAttachmentServiceTest extends RepositoryTestBase {
    private static NewsAttachmentService service;
    private static MediaLibraryService media;
    private static Station station;
    private static Station otherStation;
    private static Account account;
    private static StationMember member;
    private static News entry;

    @BeforeAll
    static void setup() {
        media = MediaTestSupport.library(stationRepo, pageRepo, mediaFileRepo, mediaMetaRepo, storageUsageRepo);
        service = new NewsAttachmentService(new NewsAttachmentRepository(), media, stationRepo, new Api());
        station = stationRepo.create("NewsAttachmentStation");
        otherStation = stationRepo.create("NewsAttachmentOtherStation");
        account = accountRepo.create("news-attach@test.com", "News", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
        entry = newsRepo.create(
                station.id(),
                "Protokoll der Sitzung",
                "Text",
                "<p>Text</p>",
                new MemberIdentity(station.uid(), member.uid()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(account.id());
    }

    private static StationFile upload(int stationId, String seed) throws Exception {
        return media.upload(stationId, null, null, seed + ".pdf", "application/pdf", ("attach-" + seed).getBytes());
    }

    @Test
    void attachingCarriesTheFileMetadataAlong() throws Exception {
        var file = upload(station.id(), "minutes");
        var attachment = service.attach(entry.id(), station.id(), file.id(), null);
        try {
            assertEquals(file.id(), attachment.fileId());
            assertEquals("minutes.pdf", attachment.fileName());
            assertEquals("application/pdf", attachment.mimeType());
            assertEquals(file.contentHash(), attachment.contentHash());
            assertEquals("minutes.pdf", attachment.displayName(), "no label means the file name");
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }

    @Test
    void aLabelIsWhatTheReaderSees() throws Exception {
        var file = upload(station.id(), "labelled");
        var attachment = service.attach(entry.id(), station.id(), file.id(), "Protokoll");
        try {
            assertEquals("Protokoll", attachment.displayName());
            assertTrue(service.relabel(attachment.id(), "  "));
            assertEquals(
                    "labelled.pdf",
                    service.find(attachment.id()).orElseThrow().displayName(),
                    "a blank label falls back to the file name rather than showing nothing");
            assertFalse(service.relabel(99999, "X"));
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }

    @Test
    void aFileFromAnotherStationIsRefused() throws Exception {
        var foreign = upload(otherStation.id(), "foreign");
        try {
            assertThrows(BadRequestResponse.class, () -> service.attach(entry.id(), station.id(), foreign.id(), null));
            assertThrows(BadRequestResponse.class, () -> service.attach(entry.id(), station.id(), 99999, null));
        } finally {
            media.deleteFile(foreign.id());
        }
    }

    @Test
    void reorderingRecordsWhatTheAuthorArranged() throws Exception {
        var first = upload(station.id(), "first");
        var second = upload(station.id(), "second");
        var a = service.attach(entry.id(), station.id(), first.id(), null);
        var b = service.attach(entry.id(), station.id(), second.id(), null);
        try {
            assertEquals(
                    List.of(a.id(), b.id()),
                    service.list(entry.id()).stream().map(x -> x.id()).toList());
            service.reorder(entry.id(), List.of(b.id(), a.id()));
            assertEquals(
                    List.of(b.id(), a.id()),
                    service.list(entry.id()).stream().map(x -> x.id()).toList());
        } finally {
            service.detach(a.id());
            service.detach(b.id());
            media.deleteFile(first.id());
            media.deleteFile(second.id());
        }
    }

    @Test
    void anAttachedFileCannotBeDeletedOutFromUnderTheEntry() throws Exception {
        var file = upload(station.id(), "restricted");
        var attachment = service.attach(entry.id(), station.id(), file.id(), null);
        try {
            assertThrows(
                    BadRequestResponse.class,
                    () -> media.deleteFile(file.id()),
                    "the entry hands this file out; the delete has to say so rather than break it");
            assertTrue(media.findFile(file.id()).isPresent());
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }

    @Test
    void anAttachedFileIsNeverPruned() throws Exception {
        var file = upload(station.id(), "referenced");
        var attachment = service.attach(entry.id(), station.id(), file.id(), null);
        try {
            assertFalse(media.findUnusedFileIds(station.id()).contains(file.id()));
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }

    @Test
    void linksTravelInTheBodyRatherThanInAFieldOfTheirOwn() throws Exception {
        var file = upload(station.id(), "travelling");
        var attachment = service.attach(entry.id(), station.id(), file.id(), "Anmeldeformular");
        try {
            String markdown = service.withAttachmentLinks("Body", entry.id(), station.id());
            assertTrue(markdown.startsWith("Body"), "the body the author wrote is never rewritten");
            assertTrue(markdown.contains("[Anmeldeformular]"));
            assertTrue(markdown.contains("/api/v1/public/media/" + station.uid() + "/" + file.contentHash()));

            String html = service.withAttachmentLinksHtml("<p>Body</p>", entry.id(), station.id());
            assertTrue(html.startsWith("<p>Body</p>"));
            assertTrue(html.contains("<a href=\"" + service.absoluteUrl(station.id(), attachment) + "\">"));
            assertTrue(html.contains(">Anmeldeformular</a>"));
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }

    @Test
    void anEntryWithoutAttachmentsIsLeftExactlyAsItIs() {
        assertEquals("Body", service.withAttachmentLinks("Body", entry.id(), station.id()));
        assertEquals("<p>Body</p>", service.withAttachmentLinksHtml("<p>Body</p>", entry.id(), station.id()));
        assertTrue(service.list(entry.id()).isEmpty());
        assertTrue(service.listFor(List.of(entry.id())).isEmpty());
        assertTrue(service.find(99999).isEmpty());
        assertFalse(service.detach(99999));
    }

    @Test
    void attachmentsOfSeveralEntriesAreReadInOneGo() throws Exception {
        var file = upload(station.id(), "batched");
        var attachment = service.attach(entry.id(), station.id(), file.id(), null);
        try {
            var byNews = service.listFor(List.of(entry.id()));
            assertEquals(1, byNews.get(entry.id()).size());
            assertTrue(service.listFor(List.of()).isEmpty());
            assertTrue(service.listFor(null).isEmpty());
        } finally {
            service.detach(attachment.id());
            media.deleteFile(file.id());
        }
    }
}
