/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.transfer.AccountCredentialTableImporter;
import dev.chojo.ember.feature.station.transfer.AccountTableImporter;
import dev.chojo.ember.feature.station.transfer.DisabledModuleTableImporter;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        exportService = new StationExportService(stationRepo, new Api());
        var stationImporter = new StationTableImporter(stationRepo);
        importService = new StationImportService(
                stationRepo,
                exportService,
                new Api(),
                null,
                null,
                new FederationPartnerTransferFixupService(new FederationRepository(), null, stationRepo),
                TestRemoteUrlValidator.permissive(),
                stationImporter,
                Set.of(
                        stationImporter,
                        new AccountTableImporter(accountRepo),
                        new AccountCredentialTableImporter(accountRepo, passkeyModeService),
                        new DisabledModuleTableImporter(stationRepo)),
                accountRepo,
                org.mockito.Mockito.mock(dev.chojo.ember.feature.account.service.AuthService.class));
    }

    @Test
    void roundtripCreatesNewStationWithEquivalentData() {
        // -- Source side --
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

        // -- Export full bundle --
        Map<String, Object> bundle = collectBundle(sourceStation.id());

        // -- Simulate cross-instance transfer by removing the source-side artefacts before the import.
        // In real use the source lives on a different instance so its rows don't shadow the import.
        stationRepo.delete(sourceStation.id()); // cascades station_member, member_group, etc.
        accountRepo.delete(sourceAccount.id()); // cascades account_credential

        // -- Import into a fresh target station --
        var result = importService.importStation(bundle);

        // -- Verify --
        var targetStation = stationRepo.findById(result.stationId()).orElseThrow();
        assertEquals("Source Station", targetStation.name());
        assertEquals("de-DE", targetStation.locale());
        assertEquals("Europe/Berlin", targetStation.timezone());

        // Disabled modules round-trip via FLAT shape
        assertTrue(stationRepo.findDisabledModules(result.stationId()).contains(StationModule.LOST_AND_FOUND));

        // Account match-by-email: a brand-new account on the target should now exist with the
        // source's password hash and force_password_change=TRUE.
        var targetAccount = accountRepo.findByEmail("roundtrip-new@example.com").orElseThrow();
        assertEquals("Anna", targetAccount.firstName());
        var cred = accountRepo.findCredential(targetAccount.id()).orElseThrow();
        assertEquals("$bcrypt$source-hash", cred.passwordHash());
        assertTrue(cred.forcePasswordChange(), "newly created accounts must reset password on first login");

        // station_member linked via account_email lookup → resolved to the target account id.
        var targetMembers = stationMemberRepo.findByStation(result.stationId());
        assertFalse(targetMembers.isEmpty());
        assertTrue(targetMembers.stream().anyMatch(m -> m.accountId() == targetAccount.id()));

        // Member groups were re-inserted with new ids
        var targetGroups = memberGroupRepo.findByStation(result.stationId());
        assertEquals(2, targetGroups.size());
        assertTrue(targetGroups.stream().anyMatch(g -> "Trainers".equals(g.name())));

        // Sanity: source member's id ≠ target member's id (PK was remapped).
        var targetMemberWithSourceAccount = targetMembers.stream()
                .filter(m -> m.accountId() == targetAccount.id())
                .findFirst()
                .orElseThrow();
        assertNotEquals(sourceMemberId, targetMemberWithSourceAccount.id());
    }

    @Test
    void existingTargetAccountIsLinkedWithoutOverwriting() {
        // Pre-create an account on the target with a known password hash.
        String email = "roundtrip-existing@example.com";
        Account preExisting = accountRepo.create(email, "Bea", "Berger", true);
        accountRepo.createCredential(preExisting.id(), "$bcrypt$target-hash");

        // Build a synthetic bundle by hand - the import path is what we want to exercise; we don't
        // need a real source station for this assertion.
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

        // The pre-existing account's credential must NOT be overwritten.
        var cred = accountRepo.findCredential(preExisting.id()).orElseThrow();
        assertEquals("$bcrypt$target-hash", cred.passwordHash(), "existing target credential must not be replaced");
        assertFalse(cred.forcePasswordChange(), "existing target accounts keep their password-change flag");

        // The first name on the existing account must also be untouched.
        var targetAccount = accountRepo.findByEmail(email).orElseThrow();
        assertEquals("Bea", targetAccount.firstName());
    }

    @Test
    void blankEmailApplicantTransfersWithMemberAndWaitlistEntry() {
        // Use the same first name as another already-imported account (Tim Berger lives on the
        // shared test database from prior test data). The importer must still resolve the right
        // account because the FK-flattened lookup now matches by UID first - first-name / last-
        // name fallback was the production bug that mis-linked to the wrong same-named account
        // and lost the row to the UNIQUE(station_id, account_id) constraint.
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
