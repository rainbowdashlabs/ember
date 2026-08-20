/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MediaFileRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static Account otherAccount;
    private static StationMember member;
    private static StationMember otherMember;
    private static int pageId;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("MediaFileStation");
        account = accountRepo.create("media-file@test.com", "Media", "File");
        otherAccount = accountRepo.create("media-file-2@test.com", "Media", "Second");
        member = stationMemberRepo.create(station.id(), account.id());
        otherMember = stationMemberRepo.create(station.id(), otherAccount.id());
        pageId = pageRepo.create(station.id(), "File Page", "file-page", null, member.id())
                .id();
    }

    @AfterAll
    static void cleanupClass() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        accountRepo.delete(otherAccount.id());
    }

    @Test
    void createAndFind() {
        var file = mediaFileRepo.create(pageId, station.id(), "hash-create", "a.png", "image/png", 1024);
        try {
            assertEquals("a.png", file.fileName());
            assertEquals(1024, file.fileSize());
            assertTrue(mediaFileRepo.findById(file.id()).isPresent());
            assertTrue(mediaFileRepo.findById(99999).isEmpty());
            assertEquals(
                    file.id(),
                    mediaFileRepo
                            .findByStationAndHash(station.id(), "hash-create")
                            .orElseThrow()
                            .id());
            assertTrue(mediaFileRepo
                    .findByStationAndHash(station.id(), "hash-missing")
                    .isEmpty());
            assertTrue(mediaFileRepo.findByPage(pageId).stream().anyMatch(f -> f.id() == file.id()));
            assertTrue(mediaFileRepo.findByStation(station.id()).stream().anyMatch(f -> f.id() == file.id()));
        } finally {
            mediaFileRepo.delete(file.id());
        }
        assertFalse(mediaFileRepo.delete(99999));
    }

    @Test
    void updateMeta() {
        var file = mediaFileRepo.create(null, station.id(), "hash-meta", "meta.png", "image/png", 8);
        try {
            assertTrue(mediaFileRepo.updateMeta(file.id(), "alt text", "description text"));
            var fetched = mediaFileRepo.findById(file.id()).orElseThrow();
            assertEquals("alt text", fetched.defaultAltText());
            assertEquals("description text", fetched.defaultDescription());
            assertFalse(mediaFileRepo.updateMeta(99999, "x", "y"));
        } finally {
            mediaFileRepo.delete(file.id());
        }
    }

    @Test
    void uploaderSetIsPerMember() {
        var file = mediaFileRepo.create(null, station.id(), "hash-owner", "own.png", "image/png", 4);
        try {
            assertFalse(mediaFileRepo.hasAnyUploader(file.id()));

            mediaFileRepo.addUploader(file.id(), member.id());
            mediaFileRepo.addUploader(file.id(), member.id());
            mediaFileRepo.addUploader(file.id(), otherMember.id());

            assertTrue(mediaFileRepo.hasUploader(file.id(), member.id()));
            assertTrue(mediaFileRepo.hasUploader(file.id(), otherMember.id()));
            assertTrue(mediaFileRepo.hasAnyUploader(file.id()));

            assertTrue(mediaFileRepo.findByUploader(station.id(), member.id()).stream()
                    .anyMatch(f -> f.id() == file.id()));
            assertTrue(mediaFileRepo.findOwnedFileIds(station.id()).contains(file.id()));
            assertEquals(
                    member.id(),
                    mediaFileRepo.findFirstUploaders(List.of(file.id())).get(file.id()),
                    "the earliest row is who first brought the file in");

            assertTrue(mediaFileRepo.removeUploader(file.id(), member.id()));
            assertFalse(mediaFileRepo.removeUploader(file.id(), member.id()));
            assertFalse(mediaFileRepo.hasUploader(file.id(), member.id()));
            assertTrue(mediaFileRepo.hasAnyUploader(file.id()));

            assertTrue(mediaFileRepo.removeUploader(file.id(), otherMember.id()));
            assertFalse(mediaFileRepo.hasAnyUploader(file.id()));
        } finally {
            mediaFileRepo.delete(file.id());
        }
    }

    @Test
    void firstUploadersOfNothing() {
        assertTrue(mediaFileRepo.findFirstUploaders(null).isEmpty());
        assertTrue(mediaFileRepo.findFirstUploaders(List.of()).isEmpty());
    }
}
