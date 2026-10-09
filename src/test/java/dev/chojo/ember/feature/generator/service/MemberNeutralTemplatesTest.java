/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document partner stations sign for a shared appointment is about nobody in particular: it names the
 * appointment and the station, reads alike for everybody and asks only signers every member's document has.
 * That is checked when the documents of a shared appointment are set and when an appointment that asks for
 * documents is shared, and such a document is drawn once per date with the appointment's values.
 */
class MemberNeutralTemplatesTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2026-11-14T09:00:00Z");
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static final EventFederationRepository shares = new EventFederationRepository();

    private static Wiring wiring;
    private static MemberNeutralTemplates neutral;
    private static EventRequirementService requirements;
    private static StationMember manager;

    @BeforeAll
    static void setup() {
        wiring = wire(stationRepo.create("Neutral Templates Station"));
        neutral = new MemberNeutralTemplates(wiring.templates());
        requirements =
                new EventRequirementService(new EventRequirementRepository(), wiring.templates(), neutral, shares);
        manager = wiring.member("neutral-manager-" + System.nanoTime() + "@example.com", "Maria", "Leitung");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
    }

    @Test
    void anAgreementNamingTheAppointmentAndTheStationCanBeSignedByPartners() {
        var agreement = template(
                row(text("Ich nehme an {{event.name}} am {{event.start|long}} bei {{station.name}} teil.")),
                row(
                        signature(SignatureRole.PARTICIPANT, "Teilnehmende Person"),
                        signature(SignatureRole.ANY_GUARDIAN, "Erziehungsberechtigte Person")));

        assertTrue(neutral.asksMemberSideToSign(agreement));
        assertTrue(neutral.signableByPartners(agreement));
        assertDoesNotThrow(() -> neutral.requireSignableByPartners(agreement));
    }

    @Test
    void anAgreementNamingAPersonIsRefused() {
        var agreement = template(
                row(text("{{member.fullName}} darf teilnehmen.")),
                row(signature(SignatureRole.PARTICIPANT, "Teilnehmende Person")));

        assertFalse(neutral.signableByPartners(agreement));
        refused(DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON, () -> neutral.requireSignableByPartners(agreement));
    }

    @Test
    void anAgreementWhoseBlocksDependOnTheMemberIsRefused() {
        var agreement = template(
                row(text("Beide unterschreiben.", GuardianCondition.SECOND_GUARDIAN)),
                row(signature(SignatureRole.GUARDIAN_1, "Erziehungsberechtigte Person")));

        refused(
                DocumentRefusal.PARTNER_AGREEMENT_DEPENDS_ON_THE_MEMBER,
                () -> neutral.requireSignableByPartners(agreement));
    }

    @Test
    void aSignerWhoseFieldDependsOnTheMemberOrTheIssuerIsRefused() {
        var second = template(row(signature(SignatureRole.GUARDIAN_2, "Zweite erziehungsberechtigte Person")));
        var each = template(row(signature(SignatureRole.EACH_GUARDIAN, "Erziehungsberechtigte")));
        var issuedAndSigned = template(
                row(signature(SignatureRole.PARTICIPANT, "Teilnehmende Person")),
                row(signature(SignatureRole.ISSUER, "Leitung")));

        for (var agreement : List.of(second, each, issuedAndSigned)) {
            refused(
                    DocumentRefusal.PARTNER_AGREEMENT_SIGNER_NOT_SHARED,
                    () -> neutral.requireSignableByPartners(agreement));
        }
    }

    @Test
    void aDocumentNobodyOfTheMemberSideSignsNeverTravelsAndIsNotChecked() {
        var handout = template(row(text("Packliste für {{member.fullName}}")));

        assertFalse(neutral.asksMemberSideToSign(handout));
        assertFalse(neutral.signableByPartners(handout));
        assertDoesNotThrow(() -> neutral.requireSignableByPartners(handout));
    }

    @Test
    void aSharedAppointmentRefusesADocumentNamingAPersonAndSharingRefusesOneThatAsksForIt() {
        var naming = template(
                row(text("{{member.fullName}} darf teilnehmen.")),
                row(signature(SignatureRole.PARTICIPANT, "Teilnehmende Person")));
        var plain = appointment();
        requirements.setForEvent(wiring.owner(), plain.id(), List.of(naming.id()));

        refused(DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON, () -> requirements.requireShareable(plain.id()));

        var shared = appointment();
        shares.setShare(shared.id(), ShareScope.ALL_PARTNERS);
        refused(
                DocumentRefusal.PARTNER_AGREEMENT_NAMES_A_PERSON,
                () -> requirements.setForEvent(wiring.owner(), shared.id(), List.of(naming.id())));
        assertEquals(List.of(), requirements.forEvent(shared.id()), "nothing was set");
    }

    @Test
    void theAgreementIsDrawnWithTheAppointmentAndTheStationAndItsSignatureFields() {
        var agreement = template(
                row(text("Teilnahme an {{event.name}} bei {{station.name}}.")),
                row(
                        signature(SignatureRole.PARTICIPANT, "Teilnehmende Person"),
                        signature(SignatureRole.GUARDIAN_1, "Erziehungsberechtigte Person")));
        var event =
                new GenerationContext.EventFacts("Zeltlager am See", START, START.plus(Duration.ofHours(6)), "Am See");

        var drawn = wiring.generator()
                .drawForAppointment(agreement, wiring.station().id(), event);

        String text = Objects.requireNonNull(PdfText.extract(drawn.pdf()));
        assertTrue(text.contains("Zeltlager am See"), text);
        assertTrue(text.contains(wiring.station().name()), text);
        assertEquals(List.of("participant", "guardian1"), SignatureFields.unsigned(drawn.pdf()));
        assertEquals(
                0,
                wiring.generator().statementsForAppointment(agreement).byField().size(),
                "no line words its own statement");
    }

    private static DocumentTemplate template(BlockRowRequest... rows) {
        int id = wiring.templates()
                .create(
                        wiring.owner(),
                        letter("Vereinbarung " + NAMES.incrementAndGet())
                                .body(List.of(rows))
                                .forAppointments(true)
                                .build(),
                        manager.id())
                .id();
        return wiring.templates().find(id).orElseThrow();
    }

    private static StationEvent appointment() {
        return eventRepo.create(
                wiring.station().id(),
                "Ausflug " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(6)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }
}
