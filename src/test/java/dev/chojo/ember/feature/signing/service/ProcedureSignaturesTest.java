/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The documents steps of procedures ask to sign: generated for the member a procedure is about, filed with
 * them where they see it, asked for on behalf of whoever set the procedure up, and withdrawn again.
 */
class ProcedureSignaturesTest extends GeneratorTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static Wiring wiring;
    private static ProcedureSignatures signatures;
    private static StationMember manager;
    private static StationMember member;
    private static int loginPermission;

    @BeforeAll
    static void setup() {
        wiring = wire(stationRepo.create("Procedure Signing Station"));
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        manager = member("Maria", "Leitung");
        member = member("Alex", "Albers");
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, wiring.documents(), guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo);
        var requests = new SignatureRequestService(
                requestRepo,
                new SigningEvidenceRepository(),
                wiring.log(),
                memberDocumentRepo,
                wiring.documents(),
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                notices,
                new TemplateDocumentStatements(wiring.generator()));
        signatures =
                new ProcedureSignatures(wiring.generation(), requests, stationMemberRepo, memberPermissionResolver);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
    }

    /**
     * The document is generated about the member, filed with them and not hidden, and its signatures are
     * asked for in the name of whoever set the procedure up. Withdrawing it ends what it still waits for.
     */
    @Test
    void aStepsDocumentIsAskedForAndWithdrawn() {
        int consent = template(
                "Aufnahmeantrag",
                row(text("{{member.fullName}} tritt bei.")),
                row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));

        int generationId = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(generationId, manager.id());

        var generation = wiring.log().findById(generationId).orElseThrow();
        assertEquals(member.id(), generation.memberId());
        var document = memberDocumentRepo.findById(generation.documentId()).orElseThrow();
        assertFalse(document.hidden(), "the member sees what they are asked to sign");
        assertTrue(memberDocumentRepo.isBoundTo(document.id(), member.id()));
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        assertEquals(RequestState.OPEN, request.state());
        assertEquals(manager.id(), request.createdBy());
        assertFalse(requestRepo.fieldsOf(request.id()).isEmpty());

        signatures.withdraw(generationId, manager.id());

        assertFalse(requestRepo.findById(request.id()).orElseThrow().open(), "nothing is waited for any more");
        signatures.withdraw(generationId, manager.id());
    }

    /** A template nobody of the member's side signs is not a step's document. */
    @Test
    void aDocumentOnlyTheIssuerSignsIsRefused() {
        int issued = template("Bescheinigung", row(signature(SignatureRole.ISSUER, "Unterschrift")));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> signatures.generate(wiring.station().id(), issued, member.id(), manager.id()));

        assertEquals(DocumentRefusal.SIGNING_NO_FIELDS, refused.refusal());
    }

    /** A member who left is not asked about, and nothing is filed for them. */
    @Test
    void nothingIsGeneratedForSomebodyWhoLeft() {
        int consent = template("Abschied", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        var left = member("Lou", "Ludwig");
        stationMemberRepo.setFormer(left.id(), true);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> signatures.generate(wiring.station().id(), consent, left.id(), manager.id()));

        assertEquals(DocumentRefusal.SIGNING_MEMBER_GONE, refused.refusal());
        assertTrue(memberDocumentRepo
                .findByMember(wiring.station().id(), left.id(), true)
                .isEmpty());
    }

    /** Only a current member who generates documents for members asks in a procedure's name. */
    @Test
    void onlyThoseWhoGenerateDocumentsAsk() {
        assertFalse(signatures.mayAsk(wiring.station().id(), member.id()));
        assertFalse(signatures.mayAsk(wiring.station().id() + 1000, manager.id()));
    }

    private static StationMember member(String first, String last) {
        var account = accountRepo.create("procedure-signing-" + NAMES.incrementAndGet() + "@test.com", first, last);
        var created = stationMemberRepo.create(wiring.station().id(), account.id());
        stationMemberRepo.grantPermission(created.id(), loginPermission);
        return created;
    }

    private static int template(String name, BlockRowRequest... rows) {
        return wiring.templates()
                .create(wiring.owner(), letter(name).body(List.of(rows)).build(), manager.id())
                .id();
    }
}
