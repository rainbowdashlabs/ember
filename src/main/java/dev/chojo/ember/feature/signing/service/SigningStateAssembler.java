/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.service.pdf.FillInFields;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureMark;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Puts together the document for one signing state: the frozen content as every signer read it, with what
 * each signer typed into the fields they were asked to fill in drawn into those fields
 * ({@link FilledInValues}), the picture of each signed field drawn into that field ({@link SignatureMarks}),
 * and the {@link SigningEvidenceFile} attached. The result is not sealed yet; it is sealed once, as a whole,
 * and supersedes the document of the state before.
 *
 * <p>The document stays the document: nothing is printed beside the pictures, and no page is added. Who
 * signed, when and with what proof is in the attached evidence, which a reader turns into a readable record
 * on request ({@link SignatureRecords}). Where a template wants the signer's name under the line, it prints
 * the name itself, as the field's own text.
 *
 * <p>Every state is assembled afresh from the frozen content, never by adding to the document before it: a
 * change after a seal counts against that seal. Signers bind to the hash of the frozen content, so each
 * version carries all acts so far in its attachment, and the latest version is the whole story.
 *
 * <p>The attachment is a PDF/A-3 associated file of the document ({@code /AF} in the catalog, relationship
 * {@code Data}, type {@code application/json}), invisible on the page. A content document that is PDF/A-3b,
 * as the generator's letters are, stays PDF/A-3b: its metadata and output intent are kept. A content
 * document that is not PDF/A does not become one.
 */
@Singleton
public class SigningStateAssembler {
    private static final String EVIDENCE_TYPE = "application/json";
    private static final String EVIDENCE_RELATIONSHIP = "Data";
    private static final String EVIDENCE_DESCRIPTION = "Machine-readable evidence of the signatures on this document";

    private final Clock clock;

    @Inject
    public SigningStateAssembler() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock the clock that dates each state
     */
    SigningStateAssembler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Puts together the document of a request as it stands now, without any signature picture.
     *
     * @param view    the request, its fields and the evidence of every act on them
     * @param content the frozen content every signer read, whose SHA-256 the request holds
     * @return the document, not yet sealed
     * @throws IllegalArgumentException when the content is not the request's frozen content, or evidence
     *                                  names a field the request does not have
     * @throws UncheckedIOException     when the content cannot be read
     */
    public byte[] assemble(SignatureRequestView view, byte[] content) {
        return assemble(view, content, Map.of());
    }

    /**
     * Puts together the document of a request as it stands now, with the picture of every signed field
     * drawn into it.
     *
     * <p>Each field a signing act filled shows the signer's picture and nothing else ({@link SignatureMarks});
     * the field itself is taken out, so no reader offers to sign it again. A seal the content carries from
     * before, as a letter signed for its issuer when it was generated has, is taken out first, since the
     * document is sealed afresh as a whole.
     *
     * @param view     the request, its fields and the evidence of every act on them
     * @param content  the frozen content every signer read, whose SHA-256 the request holds
     * @param pictures the signature picture of each act, by the id of the field it filled; the evidence names
     *                 each one by its hash and how it came to the act
     * @return the document, not yet sealed
     * @throws IllegalArgumentException when the content is not the request's frozen content, or evidence
     *                                  names a field the request does not have
     * @throws UncheckedIOException     when the content cannot be read
     */
    public byte[] assemble(SignatureRequestView view, byte[] content, Map<Integer, ActPicture> pictures) {
        return assemble(view, content, pictures, null);
    }

    /**
     * Puts together the document of a request as it stands now, with the picture of every signed field
     * drawn into it and the issuer's signature it carried from its generation named in its evidence.
     *
     * @param view     the request, its fields and the evidence of every act on them
     * @param content  the frozen content every signer read, whose SHA-256 the request holds
     * @param pictures the signature picture of each act, by the id of the field it filled
     * @param issued   the issuer's signature the content carried when it was generated, or null
     * @return the document, not yet sealed
     * @throws IllegalArgumentException when the content is not the request's frozen content, or evidence
     *                                  names a field the request does not have
     * @throws UncheckedIOException     when the content cannot be read
     */
    public byte[] assemble(
            SignatureRequestView view,
            byte[] content,
            Map<Integer, ActPicture> pictures,
            SigningEvidenceFile.@Nullable Issued issued) {
        var request = view.request();
        if (!Sha256.hex(content).equals(request.contentSha256())) {
            throw new IllegalArgumentException("The content is not the frozen content of request " + request.uid());
        }
        Instant now = clock.instant();
        try (PDDocument document = Loader.loadPDF(content)) {
            SigningEvidenceFile evidence =
                    SigningEvidenceFiles.of(view, pictures, labelsOf(FillInFields.of(document)), now, issued);
            SignatureMarks.withoutSeals(document);
            FilledInValues.draw(document, typedValues(view), signedFields(view));
            SignatureMarks.draw(document, marks(view, pictures));
            attach(document, SigningEvidenceFiles.write(evidence), now);
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException("The document of request " + request.uid() + " could not be assembled", e);
        }
    }

    /**
     * The evidence a sealed version carries as its attachment.
     *
     * @param pdf the sealed version
     * @return the evidence file's bytes, or empty for a document that carries none
     * @throws UncheckedIOException when the document cannot be read
     */
    public static Optional<byte[]> evidenceOf(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var names = document.getDocumentCatalog().getNames();
            if (names == null) return Optional.empty();
            var files = new TreeMap<String, PDComplexFileSpecification>();
            collect(names.getEmbeddedFiles(), files);
            var spec = files.get(SigningEvidenceFile.FILE_NAME);
            PDEmbeddedFile file = spec == null ? null : spec.getEmbeddedFile();
            return file == null ? Optional.empty() : Optional.of(file.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("The document could not be read for its evidence", e);
        }
    }

    private static Map<String, String> labelsOf(List<FillInField> fields) {
        var labels = new HashMap<String, String>();
        fields.forEach(field -> labels.putIfAbsent(field.name(), field.label()));
        return labels;
    }

    /** What every act so far typed into the document's fields, by the name of the field. */
    private static Map<String, String> typedValues(SignatureRequestView view) {
        var values = new HashMap<String, String>();
        view.evidence()
                .forEach(stored ->
                        stored.evidence().act().entries().forEach(entry -> values.put(entry.field(), entry.value())));
        return values;
    }

    /** The names of the signature fields a signing act filled. */
    private static Set<String> signedFields(SignatureRequestView view) {
        return view.fields().stream()
                .filter(field -> field.state() == FieldState.SIGNED)
                .map(RequestedSignature::fieldName)
                .collect(Collectors.toSet());
    }

    /** The picture of every field a signing act filled, in the order the fields were asked for. */
    private static List<SignatureMark> marks(SignatureRequestView view, Map<Integer, ActPicture> pictures) {
        Set<Integer> acted =
                view.evidence().stream().map(StoredEvidence::fieldId).collect(Collectors.toSet());
        var marks = new ArrayList<SignatureMark>();
        for (var field : view.fields()) {
            if (field.state() != FieldState.SIGNED || !acted.contains(field.id())) continue;
            var picture = pictures.get(field.id());
            marks.add(new SignatureMark(field.fieldName(), picture == null ? null : picture.png()));
        }
        return marks;
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
}
