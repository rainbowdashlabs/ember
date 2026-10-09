/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.signing.entity.RecordTimeBasis;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.CertificateEncodingException;

/**
 * The readable record of a signed document's sealed version, built when somebody asks for it.
 *
 * <p>A sealed version carries the evidence of its signatures invisibly, as an attachment, and shows nothing
 * but the signature pictures on its pages. Its record is rendered from that attachment
 * ({@link SignatureRecordPage}), so it says exactly what the version holds, names the version by its number
 * and SHA-256, and is sealed by the station at the moment it is built. It comes on its own, to keep and print
 * beside the document, or joined to the document as one copy to hand out ({@link #withRecord}). Neither is a
 * version of the document: the document stays as it was filed, and each copy is built and sealed afresh.
 *
 * <p>Both are sealed like everything else the station seals ({@link PdfSealer}): with a timestamp where a
 * service gives one. The record's own seal vouches that the station built it from the version; the
 * version's seal and timestamp remain what proves the signatures.
 */
@Singleton
public class SignatureRecords {
    private static final String VERIFY_PATH = "/verify";

    private final DocumentService documents;
    private final StationRepository stations;
    private final StationSigningKeys keys;
    private final PdfSealer sealer;
    private final TimestampServices timestamps;
    private final String verifyAddress;

    @Inject
    public SignatureRecords(
            DocumentService documents,
            StationRepository stations,
            StationSigningKeys keys,
            PdfSealer sealer,
            TimestampServices timestamps,
            Api api) {
        this(documents, stations, keys, sealer, timestamps, api.baseUrl());
    }

    /**
     * @param baseUrl the installation's public base address, before the verification page's path
     */
    SignatureRecords(
            DocumentService documents,
            StationRepository stations,
            StationSigningKeys keys,
            PdfSealer sealer,
            TimestampServices timestamps,
            String baseUrl) {
        this.documents = documents;
        this.stations = stations;
        this.keys = keys;
        this.sealer = sealer;
        this.timestamps = timestamps;
        this.verifyAddress =
                (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + VERIFY_PATH;
    }

    /**
     * The record of one sealed version on its own, sealed now.
     *
     * @param document the sealed document, already checked to be one the reader may read
     * @param number   the version's number
     * @return the record as a sealed PDF
     */
    public RecordFile record(Document document, int number) {
        var version = versionOf(document, number);
        byte[] pdf = read(document, version);
        var key = keys.forStation(document.stationId());
        byte[] record = render(document, version, pdf, key, 1);
        return new RecordFile(fileName(document, number, "record"), seal(record, key));
    }

    /**
     * One copy of a sealed version with its record joined after the last page, sealed now as a whole. The
     * version's own seal is taken out of the copy, since a page added after it would count against it; the
     * evidence it carries stays attached.
     *
     * @param document the sealed document, already checked to be one the reader may read
     * @param number   the version's number
     * @return the copy as a sealed PDF
     */
    public RecordFile withRecord(Document document, int number) {
        var version = versionOf(document, number);
        byte[] pdf = read(document, version);
        var key = keys.forStation(document.stationId());
        try (PDDocument copy = Loader.loadPDF(pdf)) {
            byte[] record = render(document, version, pdf, key, copy.getNumberOfPages() + 1);
            SignatureMarks.withoutSeals(copy);
            try (PDDocument recordDocument = Loader.loadPDF(record)) {
                keepOneOutputIntent(copy, recordDocument);
                new PDFMergerUtility().appendDocument(copy, recordDocument);
                var out = new ByteArrayOutputStream();
                copy.save(out);
                return new RecordFile(fileName(document, number, "with-record"), seal(out.toByteArray(), key));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Version " + number + " of document " + document.id() + " could not be joined", e);
        }
    }

    private SealedVersion versionOf(Document document, int number) {
        documents.requireKept(document.stationId(), DocumentDoor.STATION);
        return documents.sealedVersions(document).stream()
                .filter(candidate -> candidate.version() == number)
                .findFirst()
                .orElseThrow(DocumentRefusal.SEALED_VERSION_NOT_FOUND::raise);
    }

    private byte[] read(Document document, SealedVersion version) {
        return documents.read(document, version).orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
    }

    private byte[] render(Document document, SealedVersion version, byte[] pdf, SealingKey key, int firstPage) {
        byte[] json = SigningStateAssembler.evidenceOf(pdf).orElseThrow(DocumentRefusal.SIGNATURE_RECORD_NONE::raise);
        Station station =
                stations.findById(document.stationId()).orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        var input = new SignatureRecordPage.Input(
                SigningEvidenceFiles.read(json),
                Sha256.hex(json),
                new SignatureRecordPage.RecordedVersion(
                        document.title(), version.version(), version.sha256(), version.sealedAt()),
                station.name(),
                StationFormat.languageOf(station),
                StationFormat.timezoneOf(station),
                fingerprintOf(key),
                timeBasisOf(version),
                verifyAddress,
                firstPage);
        try {
            return SignatureRecordPage.render(input);
        } catch (IOException e) {
            throw new UncheckedIOException("The record of document " + document.id() + " could not be rendered", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while rendering the record of document " + document.id(), e);
        }
    }

    private RecordTimeBasis timeBasisOf(SealedVersion version) {
        if (version.timestampedBy() != null) return RecordTimeBasis.TIMESTAMP_SERVICE;
        return timestamps.enabled() ? RecordTimeBasis.NO_SERVICE_ANSWERED : RecordTimeBasis.TIMESTAMPS_OFF;
    }

    private byte[] seal(byte[] pdf, SealingKey key) {
        return sealer.seal(pdf, key.privateKey(), key.chain()).pdf();
    }

    /**
     * Drops the record's output intent where the document has one. Both are the sRGB intent Typst writes,
     * and PDF/A allows several intents only when they share one profile object, which a copy would not.
     */
    private static void keepOneOutputIntent(PDDocument document, PDDocument record) {
        if (document.getDocumentCatalog().getOutputIntents().isEmpty()) return;
        record.getDocumentCatalog().getCOSObject().removeItem(COSName.OUTPUT_INTENTS);
    }

    private static String fingerprintOf(SealingKey key) {
        try {
            return PublishedCertificates.fingerprintOf(key.authority().getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("The authority certificate cannot be encoded", e);
        }
    }

    private static String fileName(Document document, int number, String suffix) {
        String versioned = DocumentService.versionFileName(document.fileName(), number);
        int dot = versioned.lastIndexOf('.');
        String stem = dot <= 0 ? versioned : versioned.substring(0, dot);
        return stem + "-" + suffix + ".pdf";
    }

    /**
     * A record or a copy with its record, as it is handed out.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param fileName the name to save it under
     * @param pdf      the sealed PDF
     */
    public record RecordFile(String fileName, byte[] pdf) {}
}
