/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.service.pdf.FillInFields;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.AssembledDocument;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.GuardianLink;
import dev.chojo.ember.feature.signing.entity.RecordTimeBasis;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TypstCompiler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SigningStateAssemblerTest extends RepositoryTestBase {
    private static final Instant ASSEMBLED_AT = Instant.parse("2026-10-08T13:02:11Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-10-08T12:30:00Z");
    private static final String CONTENT_TEXT = "Lena darf am Zeltlager teilnehmen";
    private static final String GUARDIAN_STATEMENT = "Ich bin erziehungsberechtigt für Lena Beispiel und stimme zu.";
    private static final String PARTICIPANT_STATEMENT = "Ich nehme am Zeltlager teil.";
    private static final List<String> FIELDS = List.of("guardian1", "guardian2", "participant", "issuer");
    private static final UUID REQUEST = UUID.fromString("7f1c2a8e-0000-4000-8000-000000000001");
    private static final byte[] KEY_STAMP_TOKEN = {1, 2, 3, 4};

    private static KeyPair keys;
    private static X509Certificate authority;
    private static byte[] content;
    private static byte[] contentSha256;

    @BeforeAll
    static void createKeyAndContent() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        authority = PdfSealerTest.selfSigned(keys);
        content = contentWithSignatureFields();
        contentSha256 = HexFormat.of().parseHex(Sha256.hex(content));
    }

    @Test
    void aStateIsTheContentFollowedByItsRecord() throws IOException {
        var station = station("de-DE", "Europe/Berlin");

        var assembled = assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMP_SERVICE);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            assertEquals(2, assembled.recordPage());
            assertTrue(document.getNumberOfPages() >= 2);
            assertTrue(text(document, 1, 1).contains(CONTENT_TEXT));
            String record = text(document, 2, document.getNumberOfPages());
            assertTrue(record.contains("Signaturnachweis"));
            assertTrue(record.contains("Seite 2"), "page numbers continue the document's");
            assertTrue(record.contains(station.name()));
            assertTrue(record.contains("Lena Beispiel"));
            assertTrue(record.contains("Anna Beispiel"));
            assertTrue(record.contains("als erziehungsberechtigte Person für Lena Beispiel"));
            assertTrue(record.contains("Platz 1 in der Reihenfolge des Mitglieds"));
            assertTrue(record.contains("Max Wart"));
            assertTrue(record.contains("Passkey"));
            assertTrue(record.contains("Ja: die Bestätigung signiert den Inhalt"));
            assertTrue(record.contains("bei der Registrierung des Schlüssels"));
            assertTrue(record.contains("Code aus einer Authenticator-App"));
            assertTrue(record.contains("über das Konto von Anna Beispiel"));
            assertTrue(record.contains("Nein: die Bestätigung ist nur neben dem Dokument aufgezeichnet"));
            assertTrue(record.contains("Telefon: 0123 456"));
            assertTrue(record.contains("192.168.1.0"));
            assertTrue(record.contains("Noch offen"));
            assertTrue(record.contains("Bernd Beispiel"));
            assertTrue(record.contains("Auf Papier unterschrieben, bestätigt von Max Wart"));
            assertTrue(record.contains("8. Oktober 2026, 14:30:00 MESZ"), "times in the station's zone");
            assertTrue(record.contains("2 von 4"));
            assertTrue(record.contains(Sha256.hex(content).substring(0, 4)));
            assertTrue(record.contains(fingerprint().substring(0, 23)), "first eight pairs of the fingerprint");
            assertTrue(record.contains("https://ember.example.org/verify"));
            assertTrue(record.contains("unabhängigen Zeitstempeldienstes"));
            assertFalse(record.contains("qualifiziert"));
            assertFalse(record.contains("fortgeschritten"));
        }
    }

    @Test
    void theEvidenceIsAttachedAndLetsAPasskeyAnswerBeCheckedAgain() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        var view = view(station);

        var assembled = assembler().assemble(view, content, authority, RecordTimeBasis.TIMESTAMP_SERVICE);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            var spec = attachment(document);
            assertEquals("Data", spec.getCOSObject().getNameAsString(COSName.AF_RELATIONSHIP));
            assertEquals("application/json", spec.getEmbeddedFile().getSubtype());
            assertNotNull(spec.getEmbeddedFile().getModDate());
            COSArray associated = document.getDocumentCatalog().getCOSObject().getCOSArray(COSName.AF);
            assertNotNull(associated);
            assertEquals(spec.getCOSObject(), associated.getObject(0));

            byte[] json = spec.getEmbeddedFile().toByteArray();
            var file = SigningEvidenceFiles.read(json);
            assertEquals(SigningEvidenceFile.FORMAT, file.format());
            assertEquals(SigningChallenge.LABEL, file.challengeLayout());
            assertEquals(REQUEST, file.requestUid());
            assertEquals(Sha256.hex(content), file.contentSha256());
            assertEquals(ASSEMBLED_AT, file.assembledAt());
            assertEquals(
                    FIELDS,
                    file.fields().stream()
                            .map(SigningEvidenceFile.Field::fieldName)
                            .toList());
            assertNull(file.fields().get(1).act());
            assertEquals(FieldState.PAPER_CONFIRMED, file.fields().get(3).state());

            var guardian = file.fields().getFirst().act();
            assertNotNull(guardian);
            var webAuthn = guardian.webAuthn();
            assertNotNull(webAuthn);
            var stored =
                    (SigningEvidence.WebAuthnBound) view.evidence().getFirst().evidence();
            assertArrayEquals(stored.clientDataJson(), webAuthn.clientDataJson());
            assertArrayEquals(stored.authenticatorData(), webAuthn.authenticatorData());
            assertArrayEquals(stored.signature(), webAuthn.signature());
            assertArrayEquals(stored.credentialPublicKeyCose(), webAuthn.credentialPublicKeyCose());
            var keyStamp = webAuthn.credentialKeyStamp();
            assertNotNull(keyStamp);
            assertArrayEquals(KEY_STAMP_TOKEN, keyStamp.token());
            assertEquals(
                    new GuardianLink(0, Instant.parse("2025-03-01T09:00:00Z"), "Max Wart"), guardian.guardianLink());

            var recomputed = SigningChallenge.of(new SigningAct(
                    file.requestUid(),
                    new Signer(guardian.capacity(), guardian.accountId(), guardian.memberId()),
                    guardian.accountHolderName(),
                    guardian.memberName(),
                    guardian.fieldName(),
                    guardian.statement(),
                    HexFormat.of().parseHex(guardian.contentSha256()),
                    guardian.entries().stream()
                            .map(SigningEvidenceFile.Entry::asSigned)
                            .toList(),
                    guardian.nonce(),
                    guardian.signedAt(),
                    guardian.truncatedIp(),
                    guardian.userAgent()));
            assertArrayEquals(webAuthn.challenge(), recomputed);

            var participant = file.fields().get(2).act();
            assertNotNull(participant);
            assertEquals(StepUpProof.TOTP, participant.proof());
            assertFalse(participant.boundToDocument());
            assertNull(participant.webAuthn());

            String record = text(document, assembled.recordPage(), document.getNumberOfPages());
            assertTrue(record.contains(SigningEvidenceFile.FILE_NAME));
            assertTrue(record.contains(Sha256.hex(json).substring(0, 4)), "the record names the attachment's hash");
        }
    }

    @Test
    void eachSignedFieldShowsItsPictureNameDayAndRecordPage() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        var pictures = Map.of(
                11, new ActPicture(SignatureImages.clean(TestSignatures.drawn()).png(), ActPictureSource.SAVED));

        var assembled =
                assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMPS_OFF, pictures);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            String page = text(document, 1, 1);
            assertTrue(page.contains("Anna Beispiel"));
            assertTrue(page.contains("Lena Beispiel"));
            assertTrue(page.contains("Elektronisch signiert am 08.10.2026, Nachweis Seite 2"));
            assertEquals(1, imagesOn(document.getPage(0)), "the guardian's picture; the child left none");
        }
    }

    @Test
    void theEvidenceAndTheRecordNameEachPictureByItsHashAndHowItWasMade() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        var saved = new ActPicture(SignatureImages.clean(TestSignatures.drawn()).png(), ActPictureSource.SAVED);
        var uploaded = new ActPicture(
                SignatureImages.clean(TestSignatures.photographed()).png(), ActPictureSource.UPLOADED);

        var assembled = assembler()
                .assemble(
                        view(station),
                        content,
                        authority,
                        RecordTimeBasis.TIMESTAMPS_OFF,
                        Map.of(11, saved, 13, uploaded));

        try (var document = Loader.loadPDF(assembled.pdf())) {
            var file = SigningEvidenceFiles.read(
                    attachment(document).getEmbeddedFile().toByteArray());
            var guardian = file.fields().getFirst().act();
            var participant = file.fields().get(2).act();
            assertNotNull(guardian);
            assertNotNull(participant);
            assertEquals(new SigningEvidenceFile.Picture(saved.sha256(), ActPictureSource.SAVED), guardian.picture());
            assertEquals(
                    new SigningEvidenceFile.Picture(uploaded.sha256(), ActPictureSource.UPLOADED),
                    participant.picture());
            assertEquals(SigningChallenge.LABEL, file.challengeLayout(), "the challenge layout stays as it was");

            String record = text(document, assembled.recordPage(), document.getNumberOfPages());
            assertTrue(record.contains("Vorher im Konto gespeichert"));
            assertTrue(record.contains("Beim Unterschreiben als Foto oder Scan hochgeladen"));
            assertTrue(record.contains(grouped(saved.sha256())), record);
            assertTrue(record.contains(grouped(uploaded.sha256())), record);
        }
    }

    @Test
    void anActWithoutAPictureIsRecordedWithoutOne() throws IOException {
        var station = station("en-GB", "UTC");

        var assembled = assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMPS_OFF);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            var file = SigningEvidenceFiles.read(
                    attachment(document).getEmbeddedFile().toByteArray());
            assertNull(Objects.requireNonNull(file.fields().getFirst().act()).picture());
            assertFalse(text(document, assembled.recordPage(), document.getNumberOfPages())
                    .contains("Signature picture"));
        }
    }

    /** The first line of a hash as the record prints it: groups of four, eight to a line. */
    private static String grouped(String sha256) {
        var groups = new ArrayList<String>();
        for (int i = 0; i < 32; i += 4) {
            groups.add(sha256.substring(i, i + 4));
        }
        return String.join(" ", groups);
    }

    @Test
    void anEnglishStationCaptionsItsMarksInEnglish() throws IOException {
        var station = station("en-GB", "UTC");

        var assembled = assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMPS_OFF);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            assertTrue(text(document, 1, 1).contains("Signed electronically on 8 October 2026, record on page 2"));
        }
    }

    @Test
    void aSealTheContentCarriesIsTakenOutBeforeTheStateIsSealedAgain() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        byte[] sealedContent = SealedPdfs.sealer(SealedPdfs.noTimestamps())
                .seal(content, keys.getPrivate(), List.of(authority))
                .pdf();

        var assembled = assembler()
                .assemble(view(station, sealedContent), sealedContent, authority, RecordTimeBasis.TIMESTAMPS_OFF);
        var resealed = SealedPdfs.sealer(SealedPdfs.noTimestamps())
                .seal(assembled.pdf(), keys.getPrivate(), List.of(authority))
                .pdf();

        var signatures =
                SealedPdfs.validate(resealed, authority).getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size(), "only the new seal, no broken one from before");
        assertTrue(signatures.getFirst().isSignatureValid());
        assertTrue(SealedPdfs.referencedDataIntact(signatures.getFirst()));
    }

    /**
     * What a signer typed is drawn into its field on the content page before sealing, and the field is
     * taken out; a field the signer of a signed field left empty goes too, while the field of a signer still
     * to come stays, read only. The evidence and the record name each value with the field's label, the
     * challenge is still recomputable from the evidence, and the sealed state validates.
     */
    @Test
    void typedValuesAreDrawnBeforeSealingAndListedWithTheirLabels() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        byte[] frozen = contentWithFillIns();
        var entries = List.of(new SignerEntry("fill-guardian1-0", "0171 9876543"));
        var view = new SignatureRequestView(
                view(station, frozen).request(),
                view(station, frozen).fields(),
                List.of(guardianEvidence(entries), participantEvidence()));

        var assembled = assembler().assemble(view, frozen, authority, RecordTimeBasis.TIMESTAMPS_OFF);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            assertTrue(text(document, 1, 1).contains("0171 9876543"), "drawn on the content page");
            var form = document.getDocumentCatalog().getAcroForm(null);
            assertNull(form.getField("fill-guardian1-0"), "the filled field is taken out");
            assertNull(form.getField("fill-participant-1"), "a signed signer's empty field goes too");
            var open = form.getField("fill-guardian2-0");
            assertNotNull(open, "the field of a signer still to come stays");
            assertTrue(open.isReadOnly());
            var file = SigningEvidenceFiles.read(
                    attachment(document).getEmbeddedFile().toByteArray());
            var guardian = Objects.requireNonNull(file.fields().getFirst().act());
            assertEquals(
                    List.of(new SigningEvidenceFile.Entry("fill-guardian1-0", "0171 9876543", "Telefon im Notfall")),
                    guardian.entries());
            var webAuthn = Objects.requireNonNull(guardian.webAuthn());
            assertArrayEquals(
                    webAuthn.challenge(),
                    SigningChallenge.of(new SigningAct(
                            file.requestUid(),
                            new Signer(guardian.capacity(), guardian.accountId(), guardian.memberId()),
                            guardian.accountHolderName(),
                            guardian.memberName(),
                            guardian.fieldName(),
                            guardian.statement(),
                            HexFormat.of().parseHex(guardian.contentSha256()),
                            guardian.entries().stream()
                                    .map(SigningEvidenceFile.Entry::asSigned)
                                    .toList(),
                            guardian.nonce(),
                            guardian.signedAt(),
                            guardian.truncatedIp(),
                            guardian.userAgent())),
                    "the typed value is bound by the challenge");
        }
        assertTrue(recordOf(assembled).contains("Telefon im Notfall: 0171 9876543"));
        var sealed = SealedPdfs.sealer(SealedPdfs.noTimestamps())
                .seal(assembled.pdf(), keys.getPrivate(), List.of(authority))
                .pdf();
        var signature = SealedPdfs.validate(sealed, authority)
                .getDiagnosticData()
                .getSignatures()
                .getFirst();
        assertTrue(signature.isSignatureValid());
        SigningSamples.write("fields-to-fill-in-content.pdf", frozen);
        SigningSamples.write("fields-to-fill-in-signed.pdf", sealed);
    }

    @Test
    void aPdfA3bContentStaysPdfA3bAndKeepsItsOpenSignatureFields() throws IOException {
        var station = station("de-DE", "Europe/Berlin");

        var assembled = assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMP_SERVICE);

        try (var document = Loader.loadPDF(assembled.pdf())) {
            var catalog = document.getDocumentCatalog();
            assertEquals(1, catalog.getOutputIntents().size());
            var metadata = catalog.getMetadata();
            assertNotNull(metadata);
            String xmp = new String(metadata.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(Pattern.compile("pdfaid:part(>|=\")3").matcher(xmp).find(), xmp);
            assertTrue(
                    Pattern.compile("pdfaid:conformance(>|=\")B").matcher(xmp).find(), xmp);
        }
        assertEquals(List.of("guardian2", "issuer"), SignatureFields.unsigned(assembled.pdf()));
    }

    @Test
    void theRecordSaysWhereItsTimesComeFrom() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        var view = view(station);

        assertTrue(recordOf(assembler().assemble(view, content, authority, RecordTimeBasis.NO_SERVICE_ANSWERED))
                .contains("Beim Versiegeln hat kein Zeitstempeldienst geantwortet"));
        assertTrue(recordOf(assembler().assemble(view, content, authority, RecordTimeBasis.TIMESTAMPS_OFF))
                .contains("Beim Versiegeln hat diese Installation keine Zeitstempel eingeholt"));
    }

    @Test
    void anEnglishStationGetsItsRecordInEnglishAndInItsZone() throws IOException {
        var station = station("en-GB", "UTC");

        var record = recordOf(assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMPS_OFF));

        assertTrue(record.contains("Signature record"));
        assertTrue(record.contains("as guardian of Lena Beispiel"));
        assertTrue(record.contains("8 October 2026, 12:30:00 UTC"));
        assertTrue(record.contains("When the document was sealed, this installation asked no timestamp service"));
        assertTrue(record.contains("A timestamp can be added later"));
    }

    @Test
    void theAssembledStateIsSealedAsAWholeAndKeepsItsAttachment() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        var assembled = assembler().assemble(view(station), content, authority, RecordTimeBasis.TIMESTAMPS_OFF);

        var sealed = SealedPdfs.sealer(SealedPdfs.noTimestamps())
                .seal(assembled.pdf(), keys.getPrivate(), List.of(authority))
                .pdf();

        var signatures =
                SealedPdfs.validate(sealed, authority).getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        assertTrue(signatures.getFirst().isSignatureValid());
        assertTrue(SealedPdfs.referencedDataIntact(signatures.getFirst()));
        try (var document = Loader.loadPDF(sealed)) {
            assertNotNull(attachment(document));
        }
    }

    @Test
    void contentOtherThanTheFrozenOneIsRefused() throws IOException {
        var station = station("de-DE", "Europe/Berlin");
        byte[] other = TestSealing.onePagePdf();

        assertThrows(IllegalArgumentException.class, () -> assembler()
                .assemble(view(station), other, authority, RecordTimeBasis.TIMESTAMPS_OFF));
    }

    @Test
    void evidenceForAFieldTheRequestDoesNotHaveIsRefused() {
        var station = station("de-DE", "Europe/Berlin");
        var view = view(station);
        var stray = new StoredEvidence(
                99,
                999,
                SignatureLevel.SIMPLE,
                view.evidence().getLast().evidence(),
                null,
                null,
                null,
                null,
                SIGNED_AT);
        var broken = new SignatureRequestView(view.request(), view.fields(), List.of(stray));

        assertThrows(IllegalArgumentException.class, () -> assembler()
                .assemble(broken, content, authority, RecordTimeBasis.TIMESTAMPS_OFF));
    }

    @Test
    void theExpectedTimeBasisFollowsWhetherTimestampsAreAsked() {
        var on = mock(TimestampServices.class);
        when(on.enabled()).thenReturn(true);
        var off = mock(TimestampServices.class);
        when(off.enabled()).thenReturn(false);

        assertEquals(RecordTimeBasis.TIMESTAMP_SERVICE, assembler(on).expectedTimeBasis());
        assertEquals(RecordTimeBasis.TIMESTAMPS_OFF, assembler(off).expectedTimeBasis());
    }

    private static SigningStateAssembler assembler() {
        return assembler(SealedPdfs.noTimestamps());
    }

    private static SigningStateAssembler assembler(TimestampServices timestamps) {
        return new SigningStateAssembler(
                stationRepo, timestamps, "https://ember.example.org/", Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
    }

    private static Station station(String locale, String timezone) {
        var station = stationRepo.create("Wache " + UUID.randomUUID());
        stationRepo.updateLocale(station.id(), locale);
        stationRepo.updateTimezone(station.id(), timezone);
        return station;
    }

    private static SignatureRequestView view(Station station) {
        return view(station, content);
    }

    private static SignatureRequestView view(Station station, byte[] frozen) {
        var request = new SignatureRequest(
                1,
                REQUEST,
                station.id(),
                null,
                null,
                null,
                "Lena Beispiel",
                Sha256.hex(frozen),
                RequestState.OPEN,
                null,
                48,
                null,
                SIGNED_AT.minusSeconds(3600),
                null,
                null,
                false);
        var fields = List.of(
                field(
                        11,
                        "guardian1",
                        FieldRole.GUARDIAN,
                        "Anna Beispiel",
                        SignerCapacity.GUARDIAN,
                        GUARDIAN_STATEMENT,
                        FieldState.SIGNED,
                        null),
                field(
                        12,
                        "guardian2",
                        FieldRole.GUARDIAN,
                        "Bernd Beispiel",
                        SignerCapacity.GUARDIAN,
                        GUARDIAN_STATEMENT,
                        FieldState.OPEN,
                        null),
                field(
                        13,
                        "participant",
                        FieldRole.PARTICIPANT,
                        "Lena Beispiel",
                        SignerCapacity.MEMBER_THROUGH_ACCOUNT,
                        PARTICIPANT_STATEMENT,
                        FieldState.SIGNED,
                        null),
                field(
                        14,
                        "issuer",
                        FieldRole.ISSUER,
                        null,
                        SignerCapacity.ACCOUNT_HOLDER,
                        "Ausgestellt.",
                        FieldState.PAPER_CONFIRMED,
                        "Max Wart"));
        return new SignatureRequestView(request, fields, List.of(guardianEvidence(), participantEvidence()));
    }

    private static RequestedSignature field(
            int id,
            String name,
            FieldRole role,
            String signerName,
            SignerCapacity capacity,
            String statement,
            FieldState state,
            String settledByName) {
        Instant settledAt = state == FieldState.OPEN ? null : SIGNED_AT;
        return new RequestedSignature(
                id,
                1,
                name,
                role,
                7,
                null,
                signerName,
                capacity,
                statement,
                state,
                settledAt,
                null,
                settledByName,
                null);
    }

    private static StoredEvidence guardianEvidence() {
        return guardianEvidence(List.of(new SignerEntry("Telefon", "0123 456")));
    }

    private static StoredEvidence guardianEvidence(List<SignerEntry> entries) {
        var act = new SigningAct(
                REQUEST,
                Signer.guardian(31, 7),
                "Anna Beispiel",
                "Lena Beispiel",
                "guardian1",
                GUARDIAN_STATEMENT,
                contentSha256,
                entries,
                new byte[32],
                SIGNED_AT,
                "192.168.1.0",
                "Mozilla/5.0 (X11; Linux x86_64)");
        var evidence = new SigningEvidence.WebAuthnBound(
                act,
                StepUpProof.PASSKEY,
                "ember.example.org",
                SigningChallenge.of(act),
                new byte[] {9, 9},
                new byte[] {5, 6, 7},
                "{\"type\":\"webauthn.get\"}".getBytes(StandardCharsets.UTF_8),
                new byte[37],
                new byte[] {8, 8, 8},
                true,
                3,
                new CredentialKeyStamp(
                        KEY_STAMP_TOKEN,
                        Instant.parse("2025-03-02T09:00:00Z"),
                        "http://timestamp.example",
                        KeyStampKind.AT_REGISTRATION));
        return new StoredEvidence(
                1,
                11,
                SignatureLevel.SIMPLE,
                evidence,
                3,
                7,
                new GuardianLink(0, Instant.parse("2025-03-01T09:00:00Z"), "Max Wart"),
                null,
                SIGNED_AT);
    }

    private static StoredEvidence participantEvidence() {
        var act = new SigningAct(
                REQUEST,
                Signer.memberThroughAccount(31, 7),
                "Anna Beispiel",
                "Lena Beispiel",
                "participant",
                PARTICIPANT_STATEMENT,
                contentSha256,
                List.of(),
                new byte[32],
                SIGNED_AT,
                null,
                null);
        return new StoredEvidence(
                2, 13, SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act), 3, 7, null, null, SIGNED_AT);
    }

    private static byte[] contentWithSignatureFields() throws Exception {
        byte[] letter = TypstCompiler.compile(
                "#set document(title: \"Einverständnis\")\n#set text(lang: \"de\")\n= Einverständnis\n" + CONTENT_TEXT
                        + ".\n",
                TypstCompiler.Output.PDF_A_3B);
        try (var document = Loader.loadPDF(letter);
                var out = new ByteArrayOutputStream()) {
            var page = document.getPage(0);
            float y = 100;
            for (String name : FIELDS) {
                SignatureFields.add(document, page, new PDRectangle(72, y, 200, 40), name);
                y += 60;
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * The content with its signature fields and three fields to fill in: one each for the first guardian,
     * who signs, the second, who has not signed yet, and the participant, who signs and leaves it empty.
     */
    private static byte[] contentWithFillIns() throws IOException {
        try (var document = Loader.loadPDF(content);
                var out = new ByteArrayOutputStream()) {
            var page = document.getPage(0);
            FillInFields.add(
                    document,
                    page,
                    new PDRectangle(300, 100, 200, 20),
                    new FillInField("fill-guardian1-0", "guardian1", "Telefon im Notfall", true, 40));
            FillInFields.add(
                    document,
                    page,
                    new PDRectangle(300, 160, 200, 20),
                    new FillInField("fill-guardian2-0", "guardian2", "Telefon im Notfall", true, 40));
            FillInFields.add(
                    document,
                    page,
                    new PDRectangle(300, 220, 200, 20),
                    new FillInField("fill-participant-1", "participant", "Allergien", false, 500));
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String fingerprint() {
        try {
            return PublishedCertificates.fingerprintOf(authority.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static PDComplexFileSpecification attachment(PDDocument document) throws IOException {
        var names = document.getDocumentCatalog().getNames();
        assertNotNull(names);
        var files = names.getEmbeddedFiles();
        assertNotNull(files);
        var spec = files.getNames().get(SigningEvidenceFile.FILE_NAME);
        assertNotNull(spec);
        return spec;
    }

    private static String recordOf(AssembledDocument assembled) throws IOException {
        try (var document = Loader.loadPDF(assembled.pdf())) {
            return text(document, assembled.recordPage(), document.getNumberOfPages());
        }
    }

    private static String text(PDDocument document, int from, int to) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(from);
        stripper.setEndPage(to);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }

    /** How many pictures a page draws. */
    static int imagesOn(PDPage page) throws IOException {
        int images = 0;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject) images++;
        }
        return images;
    }
}
