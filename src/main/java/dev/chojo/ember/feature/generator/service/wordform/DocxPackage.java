/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * The parts of a Word document, which is a zip of XML files, read with limits so a crafted archive
 * cannot fill the memory, and its XML parsed with no document type and no outside entity.
 */
final class DocxPackage {
    /** The part holding the body of the document. */
    static final String BODY = "word/document.xml";

    /** The part holding the styles of the document. */
    static final String STYLES = "word/styles.xml";

    /** The most bytes all parts may hold together once unpacked. */
    private static final long MAX_UNPACKED = 64L * 1024 * 1024;

    /** The most parts a document may have. */
    private static final int MAX_PARTS = 2_000;

    private final Map<String, byte[]> parts;

    private DocxPackage(Map<String, byte[]> parts) {
        this.parts = parts;
    }

    /**
     * @param data the bytes of a Word document
     * @return its parts
     * @throws IOException where it is no zip, holds no body, or unpacks to more than the limits allow
     */
    static DocxPackage read(byte[] data) throws IOException {
        var parts = new LinkedHashMap<String, byte[]>();
        long total = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (parts.size() >= MAX_PARTS) throw new IOException("Too many parts in the document");
                var out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_UNPACKED) throw new IOException("The document unpacks to too much");
                    out.write(buffer, 0, read);
                }
                parts.put(entry.getName(), out.toByteArray());
            }
        }
        if (!parts.containsKey(BODY)) throw new IOException("The document has no body");
        return new DocxPackage(parts);
    }

    /** @return the names of every part, in the order the archive holds them */
    List<String> names() {
        return List.copyOf(parts.keySet());
    }

    /**
     * @param name the name of a part
     * @return the part parsed, or empty where the document has no such part
     * @throws IOException where the part is no well-formed XML
     */
    Optional<Document> xml(String name) throws IOException {
        byte[] part = parts.get(name);
        if (part == null) return Optional.empty();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return Optional.of(factory.newDocumentBuilder().parse(new ByteArrayInputStream(part)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("The part " + name + " cannot be read", e);
        }
    }

    /**
     * @param name     the name of a part
     * @param document what the part holds from now on
     * @throws IOException where the XML cannot be written
     */
    void replace(String name, Document document) throws IOException {
        try {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            var out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            parts.put(name, out.toByteArray());
        } catch (TransformerException e) {
            throw new IOException("The part " + name + " cannot be written", e);
        }
    }

    /**
     * @return the document as a Word file again
     * @throws IOException where it cannot be packed
     */
    byte[] bytes() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
