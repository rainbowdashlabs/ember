/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.LeavingMemberGear;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.service.MessageErasure;
import dev.chojo.ember.feature.messages.service.MessageFixtures;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.TestSignatures;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
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
    private static StorageService storage;
    private static SignatureImageService signatureImages;
    private static Station station;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        var avatars = new AvatarService(new ImageVariants(storage));
        signatureImages = new SignatureImageService(new AccountSignatureRepository(), accountRepo, storage);
        service = new GdprDeletionService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                avatars,
                newDocumentService(storage),
                signatureImages,
                new AccountReach(accountRepo),
                new MessageErasure(new ConversationRepository()),
                new LeavingMemberGear(itemMovementService, itemCustodyService, inventoryRepo));
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
    @Order(21)
    void deletingTheLastMembershipTakesTheAccountAlong() {
        var alone = accountRepo.create("gdpr-alone@test.com", "Only", "Here");
        var aloneMember = stationMemberRepo.create(station.id(), alone.id());

        service.anonymizeMember(aloneMember.id());

        assertTrue(accountRepo.findById(alone.id()).isEmpty());
    }

    /**
     * A deleted member's gear comes off their record before they are erased: their open movement closes,
     * the station's piece goes back on the shelf, the association's piece back into the station's hold,
     * and a missing one stays missing on nobody's record.
     */
    @Test
    @Order(23)
    void deletingAMemberTakesTheirGearBackFirst() {
        var leaving = accountRepo.create("gdpr-gear@test.com", "Gear", "Holder");
        var leavingMember = stationMemberRepo.create(station.id(), leaving.id());
        int inventoryId = inventoryRepo
                .create(station.id(), "GdprGear " + System.nanoTime(), InventoryType.MIXED, false)
                .id();
        int own = held(inventoryId, "GG-1", ItemOwner.STATION, leavingMember.id());
        int theirs = held(inventoryId, "GG-2", ItemOwner.CLUSTER, leavingMember.id());
        int missing = held(inventoryId, "GG-3", ItemOwner.STATION, leavingMember.id());
        itemCustodyService.markLost(missing, null, null);
        int swapped = held(inventoryId, "GG-4", ItemOwner.STATION, leavingMember.id());
        var swap = itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                leavingMember.id(),
                "Gear Holder",
                swapped,
                inventoryId,
                null,
                null,
                "Kaputt",
                new ItemMovementService.Actor(leavingMember.id(), true),
                null);

        service.anonymizeMember(leavingMember.id());

        assertEquals(
                ItemCustody.WITH_OWNER,
                inventoryRepo.findItemById(own).orElseThrow().custody());
        assertEquals(
                ItemCustody.AT_STATION,
                inventoryRepo.findItemById(theirs).orElseThrow().custody());
        var lost = inventoryRepo.findItemById(missing).orElseThrow();
        assertEquals(ItemCustody.LOST, lost.custody(), "a missing piece stays missing");
        assertNull(lost.assignedTo());
        assertFalse(itemMovementService.isOnAMovement(swapped), "the open swap closed");
        assertEquals(
                ItemCustody.WITH_OWNER,
                inventoryRepo.findItemById(swapped).orElseThrow().custody());
        assertTrue(itemMovementRepo
                .findById(swap.id())
                .map(m -> m.state() != MovementState.OPEN)
                .orElse(true));
    }

    /**
     * Erasing a member never fails over their gear: a piece another member's planned hand-out has
     * promised still comes off the record, rests on the shelf, and stays promised.
     */
    @Test
    @Order(24)
    void deletingAMemberSucceedsWhenAnotherMovementNamesTheirPiece() {
        var leaving = accountRepo.create("gdpr-promised@test.com", "Promised", "Piece");
        var leavingMember = stationMemberRepo.create(station.id(), leaving.id());
        var other = accountRepo.create("gdpr-receiver@test.com", "Gets", "It");
        var otherMember = stationMemberRepo.create(station.id(), other.id());
        int inventoryId = inventoryRepo
                .create(station.id(), "GdprPromised " + System.nanoTime(), InventoryType.INTERNAL, false)
                .id();
        int piece = held(inventoryId, "GP-1", ItemOwner.STATION, leavingMember.id());
        var handOut = itemMovementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                otherMember.id(),
                "Gets It",
                null,
                inventoryId,
                null,
                null,
                "Weitergabe",
                new ItemMovementService.Actor(otherMember.id(), true),
                piece);

        assertDoesNotThrow(() -> service.anonymizeMember(leavingMember.id()));

        var item = inventoryRepo.findItemById(piece).orElseThrow();
        assertEquals(ItemCustody.WITH_OWNER, item.custody(), "it rests on the shelf");
        assertNull(item.assignedTo());
        assertEquals(
                handOut.id(),
                itemMovementRepo.findOpenByIncomingItem(piece).orElseThrow().id(),
                "and is still promised");
    }

    private int held(int inventoryId, String code, ItemOwner owner, int memberId) {
        int id = inventoryRepo
                .createItem(inventoryId, code, "Jacke", null, null, owner, null)
                .id();
        itemCustodyService.assignToMember(id, memberId, "Gear Holder");
        return id;
    }

    /** An association still needs the account of somebody who holds a role in it, so only the member goes. */
    @Test
    @Order(22)
    void deletingTheLastMembershipKeepsAnAccountWithAnAssociationRole() {
        var held = accountRepo.create("gdpr-association@test.com", "Still", "Needed");
        var heldMember = stationMemberRepo.create(station.id(), held.id());
        var association = clusterRepo.create("GdprAssociation " + System.nanoTime(), null, station.id());
        clusterRepo.addMember(association.id(), held.id(), ClusterUserType.CLUSTER_USER);

        service.anonymizeMember(heldMember.id());

        assertTrue(stationMemberRepo.findById(heldMember.id()).isEmpty(), "the member is gone");
        assertTrue(accountRepo.findById(held.id()).isPresent(), "the account stays");
        assertTrue(clusterRepo.findMember(association.id(), held.id()).isPresent(), "and so does its role");
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

    /** The signature picture lives in the account's file store, outside the rows the deletion removes. */
    @Test
    @Order(32)
    void aDeletedAccountTakesItsSignaturePictureAlong() {
        var signer = accountRepo.create("gdpr-signature@test.com", "Unter", "Schrift");
        stationMemberRepo.create(station.id(), signer.id());
        signatureImages.save(signer.id(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        var scope = new StorageScope.Account(signer.uid());
        assertTrue(storage.readAllBytes(scope, StorageCategory.IMAGE_SIGNATURE, "signature.png")
                .isPresent());

        service.deleteAccount(signer.id());

        assertTrue(storage.readAllBytes(scope, StorageCategory.IMAGE_SIGNATURE, "signature.png")
                .isEmpty());
        assertTrue(new AccountSignatureRepository().find(signer.id()).isEmpty());
    }

    /**
     * A deleted account's messages stay for the others with nobody named as their author, and a
     * conversation nobody is left in goes with them.
     */
    @Test
    @Order(33)
    void aDeletedAccountsMessagesStayUntilNobodyIsLeft() {
        var writer = accountRepo.create("gdpr-writer@test.com", "Schreib", "Er");
        var reader = accountRepo.create("gdpr-reader@test.com", "Les", "Er");
        stationMemberRepo.create(station.id(), writer.id());
        stationMemberRepo.create(station.id(), reader.id());
        var messages = MessageFixtures.build();
        var direct = messages.conversations().openDirect(writer.id(), reader.uid());
        int written = messages.messages()
                .send(writer.id(), direct.uid(), "Hallo", null)
                .message()
                .id();

        service.deleteAccount(writer.id());

        var kept = new MessageRepository().find(written).orElseThrow();
        assertNull(kept.author(), "the message stays without its author");
        assertEquals(1, messages.conversations().list(reader.id()).size(), "the reader keeps the conversation");

        service.deleteAccount(reader.id());

        assertTrue(new ConversationRepository().findByUid(direct.uid()).isEmpty(), "nobody is left, so it goes");
        assertTrue(new MessageRepository().find(written).isEmpty());
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
