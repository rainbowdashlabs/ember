/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KbTagServiceTest extends RepositoryTestBase {
    private static KbTagService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static KbFile file;
    private static int folderId;

    @BeforeAll
    static void setup() {
        service = new KbTagService(knowledgeBaseRepo);
        station = stationRepo.create("KbTagStation");
        account = accountRepo.create("kb-tag@test.com", "Kb", "TagTester");
        member = stationMemberRepo.create(station.id(), account.id());
        folderId = knowledgeBaseRepo
                .createFolder(station.id(), null, "Tagged Folder", "", member.id())
                .id();
        file = knowledgeBaseRepo.createFile(
                station.id(), folderId, "Tagged File", "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
    }

    @AfterAll
    static void cleanup() {
        knowledgeBaseRepo.purgeFile(file.id());
        knowledgeBaseRepo.purgeFolder(folderId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * Tagging a file adds the names to the station's vocabulary and makes the file findable under
     * each of them.
     */
    @Test
    void taggingAFileAddsTheNamesToTheStationVocabulary() {
        var tags = service.setFileTags(file.id(), List.of("safety", "guide"), station.id());
        assertEquals(2, tags.size());

        assertEquals(2, service.findFileTags(file.id()).size());
        assertTrue(service.findTagsByStation(station.id()).stream()
                .map(KbTag::name)
                .toList()
                .containsAll(List.of("safety", "guide")));
        assertTrue(service.findFilesByTag(station.id(), "safety").stream().anyMatch(f -> f.id() == file.id()));
        assertTrue(service.findFilesByTag(station.id(), "unused").isEmpty());
    }

    @Test
    void replacingTagsDropsTheOnesLeftOut() {
        service.setFileTags(file.id(), List.of("first", "second"), station.id());
        var remaining = service.setFileTags(file.id(), List.of("second"), station.id());

        assertEquals(1, remaining.size());
        assertEquals("second", remaining.getFirst().name());
    }

    @Test
    void foldersCarryTheirOwnTags() {
        var tags = service.setFolderTags(folderId, List.of("docs"), station.id());
        assertEquals(1, tags.size());
        assertEquals("docs", service.findFolderTags(folderId).getFirst().name());

        assertTrue(service.setFolderTags(folderId, List.of(), station.id()).isEmpty());
    }
}
