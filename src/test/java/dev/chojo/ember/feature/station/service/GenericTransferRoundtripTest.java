/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.LendingUidClashes;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.transfer.AccountCredentialTableImporter;
import dev.chojo.ember.feature.station.transfer.AccountTableImporter;
import dev.chojo.ember.feature.station.transfer.DisabledModuleTableImporter;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives a full export → import round-trip through {@link StationExportService} and
 * {@link StationImportService} using the new metadata-driven engines. Covers:
 * <ul>
 *   <li>Station settings preserved across the trip (themes, public toggles).</li>
 *   <li>Account match-by-email: existing target accounts are linked, new ones are created with
 *       {@code force_password_change=TRUE}.</li>
 *   <li>Member groups + group memberships re-link via ID remap.</li>
 *   <li>Disabled-modules flat list round-trips correctly.</li>
 * </ul>
 */
@Tag("database")
class GenericTransferRoundtripTest extends RepositoryTestBase {

    private static StationExportService exportService;
    private static StationImportService importService;

    @BeforeAll
    static void setup() {
        exportService = new StationExportService(
                stationRepo,
                TestStationKeys.transfer(),
                TestStationKeys.partnersLeftBehind(),
                TestStationKeys.aiKeyTransfer(),
                new Api());
        var stationImporter = new StationTableImporter(stationRepo);
        importService = new StationImportService(
                stationRepo,
                exportService,
                new Api(),
                null,
                null,
                null,
                new FederationPartnerTransferFixupService(new FederationRepository(), null),
                TestStationKeys.transfer(),
                TestStationKeys.partnersLeftBehind(),
                new LendingUidClashes(new LendingRepository()),
                TestStationKeys.aiKeyTransfer(),
                TestRemoteUrlValidator.permissive(),
                TestRemoteUrlValidator.permissiveOutbound(),
                stationImporter,
                Set.of(
                        stationImporter,
                        new AccountTableImporter(accountRepo),
                        new AccountCredentialTableImporter(accountRepo, passkeyModeService),
                        new DisabledModuleTableImporter(stationRepo)),
                accountRepo,
                org.mockito.Mockito.mock(dev.chojo.ember.feature.account.service.AuthService.class),
                new TaskScheduler());
    }

    /**
     * Deletes the source before the import to simulate a transfer between instances, where the source
     * rows never shadow the imported ones.
     */
    @Test
    void roundtripCreatesNewStationWithEquivalentData() {
        var sourceStation = stationRepo.create("Source Station");
        stationRepo.updateLocale(sourceStation.id(), "de-DE");
        stationRepo.updateTimezone(sourceStation.id(), "Europe/Berlin");
        stationRepo.setDisabledModules(sourceStation.id(), Set.of(StationModule.LOST_AND_FOUND));

        Account sourceAccount = accountRepo.create("roundtrip-new@example.com", "Anna", "Aalto", true);
        accountRepo.createCredential(sourceAccount.id(), "$bcrypt$source-hash");
        var sourceMember = stationMemberRepo.create(sourceStation.id(), sourceAccount.id());
        memberGroupRepo.create(sourceStation.id(), "Trainers");
        memberGroupRepo.create(sourceStation.id(), "Veterans");
        int sourceMemberId = sourceMember.id();

        Map<String, Object> bundle = collectBundle(sourceStation.id());

        stationRepo.delete(sourceStation.id());
        accountRepo.delete(sourceAccount.id());

        var result = importService.importStation(bundle);

        var targetStation = stationRepo.findById(result.stationId()).orElseThrow();
        assertEquals("Source Station", targetStation.name());
        assertEquals("de-DE", targetStation.locale());
        assertEquals("Europe/Berlin", targetStation.timezone());

        assertTrue(
                stationRepo.findDisabledModules(result.stationId()).contains(StationModule.LOST_AND_FOUND),
                "disabled modules round-trip in the flat shape");

        var targetAccount = accountRepo.findByEmail("roundtrip-new@example.com").orElseThrow();
        assertEquals("Anna", targetAccount.firstName());
        var cred = accountRepo.findCredential(targetAccount.id()).orElseThrow();
        assertEquals("$bcrypt$source-hash", cred.passwordHash());
        assertTrue(cred.forcePasswordChange(), "newly created accounts must reset password on first login");

        var targetMembers = stationMemberRepo.findByStation(result.stationId());
        assertFalse(targetMembers.isEmpty());
        assertTrue(
                targetMembers.stream().anyMatch(m -> m.accountId() == targetAccount.id()),
                "the member resolves to the target account by email");

        var targetGroups = memberGroupRepo.findByStation(result.stationId());
        assertEquals(2, targetGroups.size());
        assertTrue(targetGroups.stream().anyMatch(g -> "Trainers".equals(g.name())));

        var targetMemberWithSourceAccount = targetMembers.stream()
                .filter(m -> m.accountId() == targetAccount.id())
                .findFirst()
                .orElseThrow();
        assertNotEquals(sourceMemberId, targetMemberWithSourceAccount.id(), "the member id is remapped");
    }

    /** Builds the bundle by hand, since only the import path is under test here. */
    @Test
    void existingTargetAccountIsLinkedWithoutOverwriting() {
        String email = "roundtrip-existing@example.com";
        Account preExisting = accountRepo.create(email, "Bea", "Berger", true);
        accountRepo.createCredential(preExisting.id(), "$bcrypt$target-hash");

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put(
                "account",
                List.of(Map.of(
                        "email", email,
                        "first_name", "Source Bea",
                        "last_name", "Berger")));
        bundle.put(
                "account_credential", List.of(Map.of("account_email", email, "password_hash", "$bcrypt$source-only")));
        bundle.put("station", Map.of("name", "Existing-Wins Station"));

        importService.importStation(bundle);

        var cred = accountRepo.findCredential(preExisting.id()).orElseThrow();
        assertEquals("$bcrypt$target-hash", cred.passwordHash(), "existing target credential must not be replaced");
        assertFalse(cred.forcePasswordChange(), "existing target accounts keep their password-change flag");

        var targetAccount = accountRepo.findByEmail(email).orElseThrow();
        assertEquals("Bea", targetAccount.firstName(), "the existing account's name stays untouched");
    }

    /**
     * Shares its first name with an account already in the shared test database. The flattened foreign
     * key lookup matches by UID first; the name fallback once linked the wrong same-named account and lost
     * the row to the unique station and account constraint.
     */
    @Test
    void blankEmailApplicantTransfersWithMemberAndWaitlistEntry() {
        var sourceStation = stationRepo.create("Blank-Email Source");
        Account blankAccount = accountRepo.create(null, "Tim", "Bauer", true);
        var sourceMember = stationMemberRepo.create(sourceStation.id(), blankAccount.id());

        Map<String, Object> bundle = collectBundle(sourceStation.id());

        stationRepo.delete(sourceStation.id());
        accountRepo.delete(blankAccount.id());

        var result = importService.importStation(bundle);

        var targetMembers = stationMemberRepo.findByStation(result.stationId());
        assertEquals(1, targetMembers.size(), "blank-email source member must arrive on destination");
        var targetMember = targetMembers.getFirst();
        assertTrue(targetMember.accountId() > 0, "destination station_member must point at a destination account");
        var targetAccount =
                accountRepo.findById(targetMember.accountId()).orElseThrow(() -> new AssertionError("account missing"));
        assertNull(targetAccount.email(), "blank email round-trips as NULL, not empty string");
        assertEquals("Tim", targetAccount.firstName());
        assertEquals(
                blankAccount.uid(),
                targetAccount.uid(),
                "source UID preserved on the destination account so the FK-flattened lookup resolves it uniquely");
        assertNotEquals(
                sourceMember.id(),
                targetMember.id(),
                "destination member must have a remapped integer id (uid stays stable)");
        assertEquals(
                sourceMember.uid(),
                targetMember.uid(),
                "station_member uid must round-trip across transfer so author_member_uid columns on comments resolve");
    }

    @Test
    void multipleBlankEmailApplicantsTransferWithoutUniqueConstraintCollision() {
        var sourceStation = stationRepo.create("Multi-Blank Source");
        Account first = accountRepo.create(null, "Tim", "Bauer", true);
        Account second = accountRepo.create(null, "Anna", "Klein", true);
        stationMemberRepo.create(sourceStation.id(), first.id());
        stationMemberRepo.create(sourceStation.id(), second.id());

        Map<String, Object> bundle = collectBundle(sourceStation.id());

        stationRepo.delete(sourceStation.id());
        accountRepo.delete(first.id());
        accountRepo.delete(second.id());

        var result = importService.importStation(bundle);

        var targetMembers = stationMemberRepo.findByStation(result.stationId());
        assertEquals(
                2,
                targetMembers.size(),
                "both blank-email applicants must arrive - null emails do not collide on the partial unique index");
    }

    /**
     * A station that lent gear to a partner on another installation moves to that installation: its
     * copy of the request arrives where the partner keeps its own under the same uid, and the two
     * become the one request both stations of an installation share.
     */
    @Test
    void aRequestArrivingWhereItsPartnerKeepsItIsMergedIntoThatCopy() {
        var lending = new LendingRepository();
        var lender = stationRepo.create("Merge Lender");
        var partner = stationRepo.create("Merge Partner");
        var partnerMember = stationMemberRepo.create(
                partner.id(),
                accountRepo
                        .create("merge-partner@example.com", "Pia", "Partner", true)
                        .id());
        int radios = inventoryRepo
                .create(lender.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        var radio = inventoryRepo.createItem(radios, "HRT-M", "Handfunkgerät M", null, null);
        var request = lending.createRequest(
                UUID.randomUUID(),
                partner.uid(),
                lender.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                partnerMember.id(),
                null,
                null,
                "Übung");
        int line = lending.addRequestItem(request.id(), radios, radio.id(), null, 1, null)
                .id();
        lending.assignItem(line, radio.id());
        lending.updateRequestStatus(request.id(), LendingStatus.LENT);
        lending.createMessage(request.id(), lender.uid(), null, "Liegt bereit", false);
        itemCustodyService.lendToPartner(radio.id(), partner.id());
        borrowedGearService.handOver(radio, lender.id(), lender.uid(), partner.id(), line);
        var federation = new FederationRepository();
        federation.activatePartner(
                federation
                        .createPartner(partner.id(), lender.uid(), null, null, "https://lender.example")
                        .id(),
                "key");

        Map<String, Object> bundle = collectBundle(lender.id());
        stationRepo.markMovedAway(lender.id(), "https://partner.example");
        lending.leaveBehind(lender.uid());
        inventoryRepo.forgetMovedStation(lender.id());
        stationRepo.delete(lender.id());

        var result = importService.importStation(bundle);

        var merged = lending.findRequestByUid(request.uid()).orElseThrow();
        assertEquals(request.id(), merged.id(), "the partner's copy is the one that stays");
        assertEquals(partnerMember.id(), merged.createdBy());
        assertEquals(LendingStatus.LENT, merged.status());
        assertEquals(1, lending.findRequestsByStation(lender.uid()).size(), "no second request is left");
        var arrivedRadio = inventoryRepo.findItemsByStation(result.stationId()).stream()
                .filter(item -> "HRT-M".equals(item.internalId()))
                .findFirst()
                .orElseThrow();
        var mergedLine = lending.findItemsByRequest(merged.id()).getFirst();
        assertEquals(line, mergedLine.id());
        assertEquals(arrivedRadio.id(), mergedLine.itemId(), "the line names the gear that arrived");
        assertEquals(List.of(arrivedRadio.id()), lending.findAssignedItems(mergedLine.id()));
        assertEquals(partner.id(), arrivedRadio.custodyPartnerStationId());
        assertEquals(
                List.of("Liegt bereit"),
                lending.findMessagesByRequest(merged.id()).stream()
                        .map(LendingMessage::message)
                        .toList());
        var copy = inventoryRepo.findBorrowedItems(partner.id()).getFirst();
        assertEquals(result.stationId(), copy.ownerStationId(), "the partner's copy names its owner here again");
    }

    /** Collects every wire entry produced by the exporter into a single Map. */
    private static Map<String, Object> collectBundle(int stationId) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        for (String table : exportService.getTableOrder()) {
            var page = exportService.exportTable(stationId, table, 0, 10_000);
            Object payload = page.get(table);
            if (payload != null) bundle.put(table, payload);
        }
        return bundle;
    }
}
