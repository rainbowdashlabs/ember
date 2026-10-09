/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a PDF is PDF/A-3b, beyond the identification it claims for itself.
 *
 * <p>Where veraPDF is on the {@code PATH}, it validates the file against the PDF/A-3b profile and must
 * find it compliant. The structural checks run in any case, so a run without veraPDF still catches what
 * breaks most easily when a document is changed after Typst wrote it: the XMP identification, the output
 * intent with its profile, a document that is not encrypted and carries its file identifier, fonts that
 * are all embedded, annotations that print and, except links, have an appearance, no form that asks the reader to draw
 * appearances, no JavaScript, and attachments that say how they relate to the document and are listed in
 * the catalog's associated files.
 */
final class PdfA3b {
    private static final Pattern PART = Pattern.compile("pdfaid:part(?:>|=\")3");
    private static final Pattern CONFORMANCE = Pattern.compile("pdfaid:conformance(?:>|=\")B");
    private static final COSName GTS_PDFA1 = COSName.getPDFName("GTS_PDFA1");
    private static final long VERAPDF_TIMEOUT_SECONDS = 120;

    private PdfA3b() {}

    /**
     * Fails the test unless the PDF is PDF/A-3b, as far as the checks go.
     *
     * @param pdf the document
     * @throws IOException when it cannot be read or veraPDF cannot be run
     */
    static void assertConforms(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            assertIdentification(document);
            assertOutputIntents(document);
            assertFalse(document.isEncrypted(), "PDF/A forbids encryption");
            assertNotNull(document.getDocument().getTrailer().getCOSArray(COSName.ID), "a file identifier");
            assertNoJavaScript(document);
            assertFormDrawsNothing(document);
            assertAnnotations(document);
            assertFontsEmbedded(document);
            assertAttachments(document);
        }
        var veraPdf = veraPdf();
        if (veraPdf.isPresent()) assertVeraPdfPasses(veraPdf.get(), pdf);
    }

    private static void assertIdentification(PDDocument document) throws IOException {
        var metadata = document.getDocumentCatalog().getMetadata();
        assertNotNull(metadata, "a PDF/A document carries its identification in XMP");
        String xmp = new String(metadata.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(PART.matcher(xmp).find(), xmp);
        assertTrue(CONFORMANCE.matcher(xmp).find(), xmp);
    }

    private static void assertOutputIntents(PDDocument document) {
        var intents = document.getDocumentCatalog().getOutputIntents();
        assertFalse(intents.isEmpty(), "an output intent");
        var profiles = new HashSet<COSBase>();
        for (var intent : intents) {
            var dictionary = intent.getCOSObject();
            assertEquals(GTS_PDFA1, dictionary.getCOSName(COSName.S), "a PDF/A output intent");
            var profile = dictionary.getItem(COSName.DEST_OUTPUT_PROFILE);
            assertNotNull(profile, "an output intent with its profile");
            profiles.add(profile instanceof COSObject indirect ? indirect.getObject() : profile);
        }
        assertEquals(1, profiles.size(), "several output intents share one profile");
    }

    private static void assertNoJavaScript(PDDocument document) {
        var names = document.getDocumentCatalog().getNames();
        assertTrue(names == null || names.getJavaScript() == null, "PDF/A forbids JavaScript");
    }

    private static void assertFormDrawsNothing(PDDocument document) {
        var form = document.getDocumentCatalog().getAcroForm();
        assertTrue(form == null || !form.getNeedAppearances(), "the reader is never asked to draw appearances");
    }

    private static void assertAnnotations(PDDocument document) throws IOException {
        for (var page : document.getPages()) {
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationPopup) continue;
                String what = annotation.getSubtype() + " " + annotation.getRectangle();
                assertTrue(annotation.getCOSObject().containsKey(COSName.F), "flags on " + what);
                assertTrue(annotation.isPrinted(), "printed: " + what);
                assertFalse(annotation.isHidden(), "not hidden: " + what);
                assertFalse(annotation.isInvisible(), "not invisible: " + what);
                assertFalse(annotation.isNoView(), "shown: " + what);
                if (needsAppearance(annotation)) {
                    assertNotNull(annotation.getNormalAppearanceStream(), "an appearance for " + what);
                }
            }
        }
    }

    /** Every annotation needs an appearance, except a link and one without an area. */
    private static boolean needsAppearance(PDAnnotation annotation) {
        if (annotation instanceof PDAnnotationLink) return false;
        var rectangle = annotation.getRectangle();
        return rectangle != null && rectangle.getWidth() > 0 && rectangle.getHeight() > 0;
    }

    private static void assertFontsEmbedded(PDDocument document) throws IOException {
        var seen = new HashSet<COSBase>();
        for (var page : document.getPages()) {
            assertFontsEmbedded(page.getResources(), seen);
            for (PDAnnotation annotation : page.getAnnotations()) {
                var appearance = annotation.getNormalAppearanceStream();
                if (appearance != null) assertFontsEmbedded(appearance.getResources(), seen);
            }
        }
    }

    private static void assertFontsEmbedded(PDResources resources, Set<COSBase> seen) throws IOException {
        if (resources == null || !seen.add(resources.getCOSObject())) return;
        for (var name : resources.getFontNames()) {
            var font = resources.getFont(name);
            if (font == null || font instanceof PDType3Font) continue;
            assertTrue(font.isEmbedded(), "embedded font " + font.getName());
        }
        for (var name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDFormXObject form)
                assertFontsEmbedded(form.getResources(), seen);
        }
    }

    private static void assertAttachments(PDDocument document) throws IOException {
        var catalog = document.getDocumentCatalog();
        var names = catalog.getNames();
        var tree = names == null ? null : names.getEmbeddedFiles();
        if (tree == null) return;
        var associated = associatedFiles(catalog.getCOSObject());
        for (var spec : attachments(tree)) {
            var dictionary = spec.getCOSObject();
            assertNotNull(dictionary.getCOSName(COSName.AF_RELATIONSHIP), "how " + spec.getFile() + " relates");
            assertNotNull(spec.getFileUnicode(), "a Unicode name for " + spec.getFile());
            var file = spec.getEmbeddedFile();
            assertNotNull(file, "the content of " + spec.getFile());
            assertNotNull(file.getSubtype(), "the media type of " + spec.getFile());
            assertTrue(associated.contains(dictionary), spec.getFile() + " is among the associated files");
        }
    }

    private static List<PDComplexFileSpecification> attachments(PDNameTreeNode<PDComplexFileSpecification> node)
            throws IOException {
        var found = new ArrayList<PDComplexFileSpecification>();
        var leaves = node.getNames();
        if (leaves != null) found.addAll(leaves.values());
        var kids = node.getKids();
        if (kids != null) for (var kid : kids) found.addAll(attachments(kid));
        return found;
    }

    private static Set<COSDictionary> associatedFiles(COSDictionary catalog) {
        var files = new HashSet<COSDictionary>();
        COSArray associated = catalog.getCOSArray(COSName.AF);
        if (associated == null) return files;
        for (int i = 0; i < associated.size(); i++) {
            if (associated.getObject(i) instanceof COSDictionary dictionary) files.add(dictionary);
        }
        return files;
    }

    private static Optional<Path> veraPdf() {
        String path = System.getenv("PATH");
        if (path == null) return Optional.empty();
        for (var directory : path.split(File.pathSeparator)) {
            if (directory.isBlank()) continue;
            var candidate = Path.of(directory, "verapdf");
            if (Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static void assertVeraPdfPasses(Path veraPdf, byte[] pdf) throws IOException {
        var file = Files.createTempFile("pdfa3b-", ".pdf");
        try {
            Files.write(file, pdf);
            var process = new ProcessBuilder(veraPdf.toString(), "--flavour", "3b", "--format", "text", file.toString())
                    .redirectErrorStream(true)
                    .start();
            String report = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(process.waitFor(VERAPDF_TIMEOUT_SECONDS, TimeUnit.SECONDS), "veraPDF finishes");
            assertTrue(report.startsWith("PASS"), report);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while veraPDF checked the document", e);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
