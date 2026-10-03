/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The certificate every demo station gets: one per station however often the demo is seeded, saved through
 * the checks a manager's template passes, and printed for a seeded member with invented data only.
 */
class DemoDocumentTemplateSeederTest extends RepositoryTestBase {
    private static DemoRunContext run;
    private static GeneratorTestBase.Wiring wiring;
    private static DemoDocumentTemplateSeeder seeder;

    @BeforeAll
    static void seedTheDemoStations() {
        run = new DemoRunContext(new PasswordHasher().hash(DemoSeeder.PASSWORD));
        new DemoStationSeeder(accountRepo, stationRepo).seed(run);
        new DemoMemberSeeder(
                        accountRepo,
                        stationMemberRepo,
                        memberLookupService,
                        memberGroupRepo,
                        new MemberGroupSetRepository(),
                        profileFieldRepo,
                        profileFieldChangeRepo,
                        userTagRepo,
                        stationRepo)
                .seed(run);
        wiring = GeneratorTestBase.wire(run.primaryStation().station());
        seeder = new DemoDocumentTemplateSeeder(wiring.templates());
        seeder.seed(run);
    }

    @Test
    void everyDemoStationHasOneCertificate() {
        assertEquals(DemoStations.ALL.size(), run.stations().size());
        for (var station : run.stations()) {
            assertEquals(1, certificates(station).size(), station.profile().name());
        }
    }

    @Test
    void seedingAgainLeavesOne() {
        seeder.seed(run);

        for (var station : run.stations()) {
            assertEquals(1, certificates(station).size(), station.profile().name());
        }
    }

    @Test
    void theCertificateSavesThroughTheChecksOfAManagersTemplate() {
        var station = run.primaryStation();
        var owner = new Owner.Station(station.stationId());
        int id = certificates(station).getFirst().id();
        int author = Objects.requireNonNull(station.adminMember().accountId());

        var saved = wiring.templates().update(owner, id, DemoDocumentTemplateSeeder.certificate(), author);

        assertTrue(saved.legal(), "a certificate handed to others is legal");
        assertFalse(saved.selfService(), "the youth warden issues and signs it");
        assertEquals(DocumentLanguage.DE, saved.language());
        assertTrue(
                LetterContent.blocks(saved.header())
                        .anyMatch(cell -> cell.contentType() == CellContentType.IMAGE
                                && ContentCell.STATION_LOGO.equals(cell.content())),
                "the station logo heads it");
        assertEquals(3, saved.footer().getFirst().cells().size(), "the contacts stand in three columns");
        assertTrue(
                LetterContent.blocks(saved.body())
                        .anyMatch(cell -> cell.config() instanceof CellConfig.SignatureConfig(SignatureRole signer)
                                && signer == SignatureRole.ISSUER),
                "the issuer signs in the body");
    }

    @Test
    void itPrintsForASeededMemberWithInventedDataOnly() {
        var station = run.primaryStation();
        var member = station.members().fortgeschritten().getFirst();
        var account =
                accountRepo.findById(Objects.requireNonNull(member.accountId())).orElseThrow();
        var session = stationSession(station.adminMember(), StationPermission.DOCUMENT_EDIT_MEMBER);

        var generated = wiring.generation()
                .generate(session, certificates(station).getFirst().id(), member.id());
        String text = wiring.textOf(generated.documentId());

        assertTrue(text.contains(account.firstName() + " " + account.lastName()), text);
        assertTrue(text.contains("Jugendfeuerwehr Musterstadt"), text);
        assertTrue(text.contains("Erika Musterfrau"), text);
        assertTrue(text.contains("jugendwart@example.org"), text);
        assertTrue(text.contains("er sich") || text.contains("sie sich"), "the pronoun follows the gender: " + text);
        assertFalse(text.contains("Berlin"), "no real city and no real brigade, Berliner included: " + text);
    }

    private static List<DocumentTemplateSummary> certificates(DemoStationContext station) {
        return wiring.templates().list(new Owner.Station(station.stationId()), false).stream()
                .filter(template -> DemoDocumentTemplateSeeder.NAME.equals(template.name()))
                .toList();
    }
}
