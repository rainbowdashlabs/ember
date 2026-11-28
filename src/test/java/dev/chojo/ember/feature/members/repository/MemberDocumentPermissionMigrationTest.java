/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one-off grant that keeps member documents with whoever reached them through a member permission,
 * run over grants of members, groups and member types written before the two were apart. It runs twice
 * to show that a second run changes nothing.
 */
class MemberDocumentPermissionMigrationTest extends RepositoryTestBase {
    private static final String PATCH = "database/postgresql/1/patch_93.sql";
    private static final String FROM = "-- Reading and editing members no longer bring member documents along.";
    private static final String UNTIL = "-- A place question can be the location the calendar shows";

    private static Station station;
    private static int notesTaker;
    private static int memberManager;
    private static int selfUploader;
    private static int severalParents;
    private static int exportGroup;

    @BeforeAll
    static void grantAndMigrate() throws IOException, SQLException {
        station = stationRepo.create("Member Documents " + UUID.randomUUID());
        notesTaker = memberWith(StationPermission.MEMBER_NOTES);
        memberManager = memberWith(StationPermission.MEMBER_MANAGER);
        selfUploader = memberWith(StationPermission.MEMBER_SELF_UPLOAD);
        severalParents = memberWith(StationPermission.MEMBER_READ);
        stationMemberRepo.grantPermission(severalParents, idOf(StationPermission.MEMBER_NOTES));
        stationMemberRepo.grantPermission(severalParents, idOf(StationPermission.MEMBER_EDIT));
        exportGroup = memberGroupRepo.create(station.id(), "Export").id();
        memberGroupRepo.addGroupPermission(exportGroup, idOf(StationPermission.MEMBER_EXPORT));
        stationMemberRepo.setUserTypePermissions(
                station.id(), StationUserType.TEAM, List.of(idOf(StationPermission.MEMBER_EDIT)));

        runMigration();
        runMigration();
    }

    @AfterAll
    static void cleanupStation() {
        stationRepo.delete(station.id());
    }

    /** A permission above reading members, held on its own, keeps reading member documents. */
    @Test
    void anAncestorOfReadingKeepsReadingDocuments() {
        assertEquals(
                Set.of(StationPermission.MEMBER_NOTES, StationPermission.DOCUMENT_READ_MEMBER),
                names(stationMemberRepo.findPermissions(notesTaker)));
    }

    /** Managing members keeps managing member documents, which brings reading them along. */
    @Test
    void managingMembersKeepsManagingDocuments() {
        assertEquals(
                Set.of(StationPermission.MEMBER_MANAGER, StationPermission.DOCUMENT_EDIT_MEMBER),
                names(stationMemberRepo.findPermissions(memberManager)));
    }

    /** A member permission that never reached member documents is given nothing. */
    @Test
    void anUnrelatedPermissionGetsNothing() {
        assertEquals(
                Set.of(StationPermission.MEMBER_SELF_UPLOAD), names(stationMemberRepo.findPermissions(selfUploader)));
    }

    /** Several member permissions reaching the same document permission give it once, and each one. */
    @Test
    void severalParentsGiveEachDocumentPermissionOnce() {
        assertEquals(
                Set.of(
                        StationPermission.MEMBER_READ,
                        StationPermission.MEMBER_NOTES,
                        StationPermission.MEMBER_EDIT,
                        StationPermission.DOCUMENT_READ_MEMBER,
                        StationPermission.DOCUMENT_EDIT_MEMBER),
                names(stationMemberRepo.findPermissions(severalParents)));
        assertEquals(5, stationMemberRepo.findPermissions(severalParents).size());
    }

    /** A group that exports members reads member documents as before. */
    @Test
    void aGroupKeepsReadingDocuments() {
        assertEquals(
                Set.of(StationPermission.MEMBER_EXPORT, StationPermission.DOCUMENT_READ_MEMBER),
                names(memberGroupRepo.findGroupPermissions(exportGroup)));
    }

    /** A member type that edits members keeps managing member documents. */
    @Test
    void aMemberTypeKeepsManagingDocuments() {
        assertEquals(
                Set.of(StationPermission.MEMBER_EDIT, StationPermission.DOCUMENT_EDIT_MEMBER),
                names(stationMemberRepo.findUserTypePermissions(station.id(), StationUserType.TEAM)));
    }

    private static int memberWith(StationPermission permission) {
        int memberId = stationMemberRepo
                .createWithoutAccount(station.id(), permission.name())
                .id();
        stationMemberRepo.grantPermission(memberId, idOf(permission));
        return memberId;
    }

    private static int idOf(StationPermission permission) {
        return stationMemberRepo.findPermissionByName(permission).orElseThrow().id();
    }

    private static Set<StationPermission> names(List<Permission> permissions) {
        return permissions.stream().map(Permission::permission).collect(Collectors.toSet());
    }

    private static void runMigration() throws IOException, SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(patch().replace("ember_schema", schemaName));
        }
    }

    /** The grant alone, without what the same release patch does before and after it. */
    private static String patch() throws IOException {
        try (var in = Objects.requireNonNull(
                MemberDocumentPermissionMigrationTest.class.getClassLoader().getResourceAsStream(PATCH))) {
            var patch = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return patch.substring(patch.indexOf(FROM), patch.indexOf(UNTIL));
        }
    }
}
