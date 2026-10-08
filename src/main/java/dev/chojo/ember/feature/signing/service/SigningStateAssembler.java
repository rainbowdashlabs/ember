/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.AssembledDocument;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RecordTimeBasis;
import dev.chojo.ember.feature.signing.entity.SignatureMark;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Puts together the document for one signing state: the frozen content as every signer read it, with the
 * mark of each signed field drawn into that field ({@link SignatureMarks}), the {@link SignatureRecordPage}
 * after its last page, and the {@link SigningEvidenceFile} attached. The result is not sealed yet; it is
 * sealed once, as a whole, and supersedes the document of the state before.
 *
 * <p>Every state is assembled afresh from the frozen content, never by adding to the document before it: a
 * page added after a seal counts as a change to the sealed document, and DSS's default policy fails the
 * earlier seal for it. Signers bind to the hash of the frozen content, so each version carries all acts so
 * far on its record page and in its attachment, and the latest version is the whole story.
 *
 * <p>The attachment is a PDF/A-3 associated file of the document ({@code /AF} in the catalog, relationship
 * {@code Data}, type {@code application/json}), since the record page shows what it holds. A content
 * document that is PDF/A-3b, as the generator's letters are, stays PDF/A-3b: its metadata and output intent
 * are kept, and the record page is PDF/A-3b itself. A content document that is not PDF/A does not become
 * one.
 *
 * <p>The record page says where the document's times come from, which is only known for certain once the
 * seal is made. {@link #expectedTimeBasis} gives what to assemble with before sealing; when the seal then
 * comes back without a timestamp, the state is assembled again with
 * {@link RecordTimeBasis#NO_SERVICE_ANSWERED} and that document is the one to seal.
 */
@Singleton
public class SigningStateAssembler {
    private static final String VERIFY_PATH = "/verify";
    private static final String EVIDENCE_TYPE = "application/json";
    private static final String EVIDENCE_RELATIONSHIP = "Data";
    private static final String EVIDENCE_DESCRIPTION = "Machine-readable evidence of the signatures on this document";

    private final StationRepository stations;
    private final TimestampServices timestamps;
    private final String verifyAddress;
    private final Clock clock;

    /** Takes the verification page's address from the installation's public base address. */
    @Inject
    public SigningStateAssembler(StationRepository stations, TimestampServices timestamps, Api api) {
        this(stations, timestamps, api.baseUrl(), Clock.systemUTC());
    }

    /**
     * @param stations   the stations, for name, language and zone
     * @param timestamps the timestamp services, to tell whether a seal is meant to carry a timestamp
     * @param baseUrl    the installation's public base address
     * @param clock      the clock that dates each state
     */
    SigningStateAssembler(StationRepository stations, TimestampServices timestamps, String baseUrl, Clock clock) {
        this.stations = stations;
        this.timestamps = timestamps;
        this.verifyAddress =
                (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + VERIFY_PATH;
        this.clock = clock;
    }

    /**
     * @return {@link RecordTimeBasis#TIMESTAMP_SERVICE} when a seal is meant to carry a timestamp,
     *     {@link RecordTimeBasis#TIMESTAMPS_OFF} when no service is asked
     */
    public RecordTimeBasis expectedTimeBasis() {
        return timestamps.enabled() ? RecordTimeBasis.TIMESTAMP_SERVICE : RecordTimeBasis.TIMESTAMPS_OFF;
    }

    /**
     * Puts together the document of a request as it stands now.
     *
     * @param view      the request, its fields and the evidence of every act on them
     * @param content   the frozen content every signer read, whose SHA-256 the request holds
     * @param authority the installation authority that issued the certificate the document will be sealed
     *                  with, whose fingerprint the record page prints
     * @param timeBasis where the document's times come from
     * @return the document, not yet sealed, with the page its record starts on
     * @throws IllegalArgumentException when the content is not the request's frozen content, the station is
     *                                  gone, or evidence names a field the request does not have
     * @throws UncheckedIOException     when the content cannot be read or the record page cannot be rendered
     */
    public AssembledDocument assemble(
            SignatureRequestView view, byte[] content, X509Certificate authority, RecordTimeBasis timeBasis) {
        return assemble(view, content, authority, timeBasis, Map.of());
    }

    /**
     * Puts together the document of a request as it stands now, with the mark of every signed field drawn
     * into it.
     *
     * <p>Each field a signing act filled shows the signer's picture, their official name and the day, and
     * the page their record starts on ({@link SignatureMarks}); an act without a picture shows the caption
     * alone. A seal the content carries from before, as a letter signed for its issuer when it was
     * generated has, is taken out first, since the document is sealed afresh as a whole.
     *
     * @param view      the request, its fields and the evidence of every act on them
     * @param content   the frozen content every signer read, whose SHA-256 the request holds
     * @param authority the installation authority that issued the certificate the document will be sealed
     *                  with, whose fingerprint the record page prints
     * @param timeBasis where the document's times come from
     * @param pictures  the signature picture of each act, by the id of the field it filled; the evidence and
     *                  the record name each one by its hash and how it came to the act
     * @return the document, not yet sealed, with the page its record starts on
     * @throws IllegalArgumentException when the content is not the request's frozen content, the station is
     *                                  gone, or evidence names a field the request does not have
     * @throws UncheckedIOException     when the content cannot be read or the record page cannot be rendered
     */
    public AssembledDocument assemble(
            SignatureRequestView view,
            byte[] content,
            X509Certificate authority,
            RecordTimeBasis timeBasis,
            Map<Integer, ActPicture> pictures) {
        var request = view.request();
        if (!Sha256.hex(content).equals(request.contentSha256())) {
            throw new IllegalArgumentException("The content is not the frozen content of request " + request.uid());
        }
        Station station = stations.findById(request.stationId())
                .orElseThrow(() -> new IllegalArgumentException("No station " + request.stationId()));
        Instant now = clock.instant();
        SigningEvidenceFile evidence = SigningEvidenceFiles.of(view, pictures, now);
        byte[] evidenceJson = SigningEvidenceFiles.write(evidence);
        String language = StationFormat.languageOf(station);
        var zone = StationFormat.timezoneOf(station);
        try (PDDocument document = Loader.loadPDF(content)) {
            int recordPage = document.getNumberOfPages() + 1;
            SignatureMarks.withoutSeals(document);
            SignatureMarks.draw(document, marks(view, pictures, MarkCaptions.of(language, zone), recordPage));
            byte[] record = SignatureRecordPage.render(new SignatureRecordPage.Input(
                    evidence,
                    Sha256.hex(evidenceJson),
                    station.name(),
                    language,
                    zone,
                    fingerprintOf(authority),
                    timeBasis,
                    verifyAddress,
                    recordPage));
            try (PDDocument recordDocument = Loader.loadPDF(record)) {
                keepOneOutputIntent(document, recordDocument);
                new PDFMergerUtility().appendDocument(document, recordDocument);
                attach(document, evidenceJson, now);
                return new AssembledDocument(save(document), recordPage);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The document of request " + request.uid() + " could not be assembled", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while rendering the record of request " + request.uid(), e);
        }
    }

    /** The mark of every field a signing act filled, in the order the fields were asked for. */
    private static List<SignatureMark> marks(
            SignatureRequestView view, Map<Integer, ActPicture> pictures, MarkCaptions captions, int recordPage) {
        Map<Integer, byte[]> pngs = pictures.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().png()));
        var marks = new ArrayList<SignatureMark>();
        for (var field : view.fields()) {
            if (field.state() != FieldState.SIGNED) continue;
            view.evidence().stream()
                    .filter(stored -> stored.fieldId() == field.id())
                    .findFirst()
                    .map(stored -> stored.evidence().act())
                    .ifPresent(act -> marks.add(new SignatureMark(
                            field.fieldName(),
                            pngs.get(field.id()),
                            captions.act(act.signerName(), act.signedAt(), recordPage))));
        }
        return marks;
    }

    /**
     * Drops the record's output intent where the content has one. Both are the sRGB intent Typst writes,
     * and PDF/A allows several intents only when they share one profile object, which a copy would not.
     */
    private static void keepOneOutputIntent(PDDocument content, PDDocument record) {
        if (content.getDocumentCatalog().getOutputIntents().isEmpty()) return;
        record.getDocumentCatalog().getCOSObject().removeItem(COSName.OUTPUT_INTENTS);
    }

    private static void attach(PDDocument document, byte[] json, Instant at) throws IOException {
        Calendar when = GregorianCalendar.from(at.atZone(ZoneOffset.UTC));
        var file = new PDEmbeddedFile(document, new ByteArrayInputStream(json));
        file.setSubtype(EVIDENCE_TYPE);
        file.setSize(json.length);
        file.setCreationDate(when);
        file.setModDate(when);

        var spec = new PDComplexFileSpecification();
        spec.setFile(SigningEvidenceFile.FILE_NAME);
        spec.setFileUnicode(SigningEvidenceFile.FILE_NAME);
        spec.setFileDescription(EVIDENCE_DESCRIPTION);
        spec.setEmbeddedFile(file);
        spec.setEmbeddedFileUnicode(file);
        spec.getCOSObject().setName(COSName.AF_RELATIONSHIP, EVIDENCE_RELATIONSHIP);

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDDocumentNameDictionary names = catalog.getNames();
        if (names == null) names = new PDDocumentNameDictionary(catalog);
        var files = new TreeMap<String, PDComplexFileSpecification>();
        collect(names.getEmbeddedFiles(), files);
        files.put(SigningEvidenceFile.FILE_NAME, spec);
        var tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(files);
        names.setEmbeddedFiles(tree);
        catalog.setNames(names);

        COSArray associated = catalog.getCOSObject().getCOSArray(COSName.AF);
        if (associated == null) {
            associated = new COSArray();
            catalog.getCOSObject().setItem(COSName.AF, associated);
        }
        associated.add(spec);
    }

    private static void collect(
            @Nullable PDNameTreeNode<PDComplexFileSpecification> node, Map<String, PDComplexFileSpecification> into)
            throws IOException {
        if (node == null) return;
        Map<String, PDComplexFileSpecification> leaves = node.getNames();
        if (leaves != null) into.putAll(leaves);
        var kids = node.getKids();
        if (kids == null) return;
        for (var kid : kids) {
            collect(kid, into);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        var out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private static String fingerprintOf(X509Certificate certificate) {
        try {
            return PublishedCertificates.fingerprintOf(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("The authority certificate cannot be encoded", e);
        }
    }
}
