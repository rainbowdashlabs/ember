/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GdprDeletionServiceTest extends RepositoryTestBase {
    private static GdprDeletionService service;
    private static Station station;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var avatars = new AvatarService(new ImageVariants(storage));
        service = new GdprDeletionService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                avatars,
                newDocumentService(storage),
                new SignatureImageService(new AccountSignatureRepository(), accountRepo, storage));
        station = stationRepo.create("GdprStation");
        Account account = accountRepo.create("gdpr-del@test.com", "Delete", "Me");
        accountRepo.createCredential(account.id(), "hash");
        member = stationMemberRepo.create(station.id(), account.id());

        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));

        var field = profileFieldRepo.create(
                station.id(), "Phone", FieldType.TEXT, ProfileFieldConfig.parse("{}"), false, false, null);
        profileFieldRepo.setValue(member.id(), field.id(), StringNode.valueOf("0123456789"));

        var group = memberGroupRepo.create(station.id(), "TestGroup");
        memberGroupRepo.addMember(group.id(), member.id());
    }

    @Test
    @Order(1)
    void memberExistsBeforeDeletion() {
        assertTrue(stationMemberRepo.findById(member.id()).isPresent());
        assertFalse(stationMemberRepo.findPermissions(member.id()).isEmpty());
    }

    /**
     * The membership row is declared for explicit deletion in the data tracking, so anonymising
     * removes it, and the cascading foreign keys take its dependent rows with it.
     */
    @Test
    @Order(10)
    void anonymizeMemberRemovesPersonalData() {
        service.anonymizeMember(member.id());

        assertTrue(stationMemberRepo.findById(member.id()).isEmpty());
    }

    @Test
    @Order(11)
    void anonymizedMemberHasNoRolesOrGroups() {
        var values = profileFieldRepo.findValues(member.id());
        assertTrue(values.isEmpty());

        var groups = memberGroupRepo.findGroupsForMember(member.id());
        assertTrue(groups.isEmpty());
    }

    @Test
    @Order(20)
    void deleteAccountRemovesAccountData() {
        var acc2 = accountRepo.create("gdpr-del2@test.com", "Also", "Delete");
        accountRepo.createCredential(acc2.id(), "hash2");
        var member2 = stationMemberRepo.create(station.id(), acc2.id());

        service.deleteAccount(acc2.id());

        assertTrue(accountRepo.findById(acc2.id()).isEmpty());
        assertTrue(stationMemberRepo.findById(member2.id()).isEmpty());
    }

    @Test
    @Order(30)
    void aStationAdministratorCannotDeleteTheirOwnAccount() {
        var admin = accountRepo.create("gdpr-own-admin@test.com", "Still", "Admin");
        var adminMember = stationMemberRepo.create(station.id(), admin.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .ifPresent(role -> stationMemberRepo.grantPermission(adminMember.id(), role.id()));

        var refused = assertThrows(RefusalResponse.class, () -> service.deleteOwnAccount(admin.id()));

        assertEquals(MemberRefusal.ACCOUNT_STILL_ADMINISTERS_STATION, refused.refusal());
        assertTrue(accountRepo.findById(admin.id()).isPresent());
    }

    @Test
    @Order(31)
    void aPlainMemberDeletesTheirOwnAccount() {
        var own = accountRepo.create("gdpr-own@test.com", "Gone", "Soon");
        stationMemberRepo.create(station.id(), own.id());

        service.deleteOwnAccount(own.id());

        assertTrue(accountRepo.findById(own.id()).isEmpty());
    }

    private static StationMember memberWithDocuments(String address, boolean keptForTheRecord) {
        var account = accountRepo.create(address, "Akte", "Mitglied");
        var filed = stationMemberRepo.create(station.id(), account.id());
        memberDocumentRepo.create(
                station.id(),
                "Attest",
                "attest.pdf",
                "application/pdf",
                1,
                false,
                keptForTheRecord,
                Uploader.nobody(),
                List.of(filed.id()));
        return filed;
    }

    /** A document nobody is named on is the station's paperwork, so the member's own go with them. */
    @Test
    @Order(40)
    void aDeletedMembersDocumentsGoWithThem() {
        var filed = memberWithDocuments("gdpr-docs@test.com", false);
        int document = memberDocumentRepo
                .findByMember(station.id(), filed.id(), true)
                .getFirst()
                .id();

        service.anonymizeMember(filed.id());

        assertTrue(memberDocumentRepo.findById(document).isEmpty());
    }

    /**
     * What is kept for the record outlasts the membership and the account. The erasure goes ahead, and
     * the document keeps the name of the person it is about instead of the link to them, so it neither
     * goes nor becomes the station's own paperwork.
     */
    @Test
    @Order(41)
    void anErasedAccountLeavesItsNameOnWhatIsKeptForTheRecord() {
        var filed = memberWithDocuments("gdpr-kept@test.com", true);
        int document = memberDocumentRepo
                .findByMember(station.id(), filed.id(), true)
                .getFirst()
                .id();

        service.deleteAccount(Objects.requireNonNull(filed.accountId()));

        assertTrue(stationMemberRepo.findById(filed.id()).isEmpty(), "the member is gone");
        assertTrue(memberDocumentRepo.findById(document).isPresent(), "the document is kept");
        assertTrue(memberDocumentRepo.membersOf(document).isEmpty());
        assertEquals(List.of("Akte Mitglied"), memberDocumentRepo.departedOf(document));
        assertFalse(memberDocumentRepo.hasNoMembers(document), "and it is still somebody's paperwork");
    }
}
