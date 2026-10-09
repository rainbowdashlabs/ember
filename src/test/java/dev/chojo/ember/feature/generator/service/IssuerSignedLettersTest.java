/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.signing.entity.PadesLevel;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.signing.service.IssuedLetterSigner;
import dev.chojo.ember.feature.signing.service.PdfSealer;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.SigningSamples;
import dev.chojo.ember.feature.signing.service.StationSigningKeys;
import dev.chojo.ember.feature.signing.service.TestSealing;
import dev.chojo.ember.feature.signing.service.TestSignatures;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.util.PdfText;
import dev.chojo.ember.util.Sha256;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A letter whose template names its issuer is signed for them as it is generated, where they agreed to it
 * and keep a signature picture: the picture and their name are drawn into the issuer field, the letter is
 * sealed with the station's key, and the signed letter is what is filed and logged; with a timestamp service
 * on loopback it validates at the verifier as a long-term seal. A letter is filed as it
 * was drawn, unsealed and with the field empty, without the consent, without a picture, for an issuer a
 * manager picked for the occasion, and when signing fails.
 */
class IssuerSignedLettersTest extends GeneratorTestBase {
    private static final IssuerSignatureRepository records = new IssuerSignatureRepository();

    private static SignatureImageService images;
    private static Wiring wiring;
    private static StationMember manager;

    private StationMember warden;
    private StationMember lena;
    private int templateId;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        images = new SignatureImageService(
                new AccountSignatureRepository(),
                accountRepo,
                new StorageService(new StorageBackendResolver(backend), backend));
        var station = stationRepo.create("Ausstellende Wache");
        wiring = wire(station, TestSealing.issuedLetterSigner(stationRepo, stationMemberRepo, images));
        manager = wiring.member("issued-manager@test.com", "Mara", "Leitung");
    }

    @BeforeEach
    void issuerAndMember() {
        warden = wiring.member(UUID.randomUUID() + "@issued.test", "Erika", "Wehr");
        lena = wiring.member(UUID.randomUUID() + "@issued.test", "Lena", "Lernend");
        templateId = certificate(warden);
    }

    @Test
    void aConsentingIssuersPictureIsDrawnInAndTheLetterSealed() throws IOException {
        var saved = images.save(warden.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        var consent = images.consent(warden.accountId(), true);

        var generated = wiring.generation().generate(as(manager), templateId, lena.id(), null);

        byte[] file = fileOf(generated.documentId());
        assertEquals(List.of(), SignatureFields.unsigned(file), "the issuer field carries the signature now");
        String text = Objects.requireNonNull(PdfText.extract(file));
        assertEquals(
                1, text.split("Erika Wehr", -1).length - 1, "the name under the line once, the picture alone above it");
        assertFalse(text.contains("Elektronisch signiert"), "the picture alone, no caption printed beside it");
        assertEquals(1, picturesOnFirstPage(file));
        assertEquals(
                1, TestSealing.intactSealsOf(file, stationRepo, wiring.station().id()));
        var logged = wiring.log().findById(generated.generationId()).orElseThrow();
        assertEquals(Sha256.hex(file), logged.fileSha256());
        var record = records.findByGeneration(generated.generationId()).orElseThrow();
        assertEquals(warden.id(), record.issuerId());
        assertEquals(consent.autoSignConsentedAt(), record.consentedAt());
        assertEquals(saved.imageSha256(), record.imageSha256());
        assertEquals(SealLevel.BASELINE_B, record.sealLevel());
    }

    @Test
    void aLetterSignedForItsIssuerWithATimestampValidatesLongTerm() throws IOException {
        try (var sealing = TestSealing.withTimestamps()) {
            var stamped = wire(
                    stationRepo.create("Stempelnde Wache " + UUID.randomUUID()),
                    sealing.stampingLetterSigner(stationRepo, stationMemberRepo, images));
            var issuer = stamped.member(UUID.randomUUID() + "@issued.test", "Erika", "Stempel");
            var boss = stamped.member(UUID.randomUUID() + "@issued.test", "Bo", "Leitung");
            images.save(issuer.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
            images.consent(issuer.accountId(), true);

            var generated = stamped.generation()
                    .generate(
                            as(boss),
                            certificate(stamped, issuer, boss),
                            participant(stamped).id(),
                            null);

            byte[] file = fileOf(stamped, generated.documentId());
            assertEquals(List.of(), SignatureFields.unsigned(file));
            assertEquals(
                    SealLevel.BASELINE_LT,
                    records.findByGeneration(generated.generationId())
                            .orElseThrow()
                            .sealLevel());
            var verification = TestSealing.verifier().verify(file);
            assertEquals(1, verification.signatures().size());
            var seal = verification.signatures().getFirst();
            assertEquals(ValidationIndication.TOTAL_PASSED, seal.indication());
            assertEquals(PadesLevel.BASELINE_LT, seal.level());
            assertTrue(seal.issuedHere());
            assertTrue(seal.intact());
            assertFalse(seal.modifiedAfterSealing());
            SigningSamples.write("issued-letter-baseline-lt.pdf", file);
        }
    }

    @Test
    void withoutTheConsentTheLetterIsFiledUnsignedAndUnsealed() throws IOException {
        images.save(warden.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);

        assertFiledUnsigned(wiring.generation().generate(as(manager), templateId, lena.id(), null));
    }

    @Test
    void withoutAPictureTheLetterIsFiledUnsignedAndUnsealed() throws IOException {
        images.consent(warden.accountId(), true);

        assertFiledUnsigned(wiring.generation().generate(as(manager), templateId, lena.id(), null));
    }

    @Test
    void anIssuerPickedForTheOccasionIsNotSignedFor() throws IOException {
        images.save(warden.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        images.consent(warden.accountId(), true);
        var other = certificate(manager);

        assertFiledUnsigned(wiring.generation()
                .generate(as(manager), other, lena.id(), new DocumentIssuerService.IssuerChoice(warden.id(), null)));
    }

    @Test
    void aLetterThatCannotBeSignedIsFiledUnsigned() throws IOException {
        images.save(warden.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        images.consent(warden.accountId(), true);
        var brokenKeys = mock(StationSigningKeys.class);
        when(brokenKeys.forStation(anyInt())).thenThrow(new IllegalStateException("The key does not open"));
        var failing = new IssuedLetterSigner(stationMemberRepo, images, brokenKeys, mock(PdfSealer.class), records);
        var broken = wire(stationRepo.create("Kaputte Wache " + UUID.randomUUID()), failing);
        var issuer = broken.member(UUID.randomUUID() + "@issued.test", "Ida", "Kaputt");
        var boss = broken.member(UUID.randomUUID() + "@issued.test", "Bo", "Leitung");
        images.save(issuer.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        images.consent(issuer.accountId(), true);

        var generated = broken.generation()
                .generate(
                        as(boss),
                        certificate(broken, issuer, boss),
                        participant(broken).id(),
                        null);

        assertFiledUnsigned(broken, generated);
    }

    private void assertFiledUnsigned(DocumentGenerationService.GeneratedDocumentResponse generated) throws IOException {
        assertFiledUnsigned(wiring, generated);
    }

    private static void assertFiledUnsigned(Wiring at, DocumentGenerationService.GeneratedDocumentResponse generated)
            throws IOException {
        byte[] file = fileOf(at, generated.documentId());
        assertEquals(List.of("issuer"), SignatureFields.unsigned(file));
        assertEquals(0, picturesOnFirstPage(file));
        assertFalse(Objects.requireNonNull(PdfText.extract(file)).contains("Elektronisch signiert"));
        assertTrue(records.findByGeneration(generated.generationId()).isEmpty());
    }

    private static int certificate(StationMember issuer) {
        return certificate(wiring, issuer, manager);
    }

    private static int certificate(Wiring at, StationMember issuer, StationMember author) {
        var request = letter("Urkunde " + UUID.randomUUID())
                .body(List.of(
                        row(text("{{member.fullName}} hat die Jugendflamme bestanden.")),
                        row(signature(SignatureRole.ISSUER, "Jugendwartin"))))
                .issuer(issuer.id(), "Jugendwartin")
                .build();
        return at.templates().create(at.owner(), request, author.id()).id();
    }

    private static StationMember participant(Wiring at) {
        return at.member(UUID.randomUUID() + "@issued.test", "Lena", "Lernend");
    }

    private static byte[] fileOf(int documentId) {
        return fileOf(wiring, documentId);
    }

    private static byte[] fileOf(Wiring at, int documentId) {
        var document = memberDocumentRepo.findById(documentId).orElseThrow();
        return at.documents().read(document).orElseThrow();
    }

    private static int picturesOnFirstPage(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var resources = document.getPage(0).getResources();
            int pictures = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) pictures++;
            }
            return pictures;
        }
    }
}
