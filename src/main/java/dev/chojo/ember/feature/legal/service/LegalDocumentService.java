/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.util.TextDiff;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Manages versioned legal documents (privacy policy, terms of service, consent text).
 * Each document type has a directory with locale subdirectories containing markdown files.
 * On initialization, detects changes by comparing content hashes against a version file,
 * archives previous versions, and generates diffs.
 *
 * <p>Directory structure:
 * <pre>
 * data/privacy/
 *   de/
 *     01-general.md
 *     02-rights.md
 *   en/
 *     01-general.md
 *     02-rights.md
 *   version.txt         (current content hash)
 *   history/
 *     &lt;hash&gt;.md         (archived full markdown)
 *     &lt;old&gt;_to_&lt;new&gt;.diff (change summary)
 * </pre>
 */
public class LegalDocumentService {
    private static final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);
    private static final String DEFAULT_LOCALE = "de";

    private final Parser parser;
    private final HtmlRenderer renderer;

    public LegalDocumentService() {
        List<Extension> extensions =
                List.of(TablesExtension.create(), HeadingAnchorExtension.create(), AutolinkExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /**
     * Initializes a document directory: checks for version changes, archives old content, generates diff.
     *
     * @return true if the content changed since last startup
     */
    public boolean initialize(Path baseDir) {
        String currentMarkdown = readMarkdownDirectory(baseDir, DEFAULT_LOCALE);
        if (currentMarkdown.isEmpty()) {
            // Also try reading directly from base dir (flat layout without locale subdirs)
            currentMarkdown = readMarkdownDirectoryFlat(baseDir);
        }
        if (currentMarkdown.isEmpty()) {
            log.warn("No markdown content found in {}", baseDir);
            return false;
        }

        String currentHash = hash(currentMarkdown);
        Path versionFile = baseDir.resolve("version.txt");
        Path historyDir = baseDir.resolve("history");

        String previousHash = readVersionFile(versionFile);

        if (previousHash != null && previousHash.equals(currentHash)) {
            log.info("Legal document unchanged: {} (version {})", baseDir, currentHash);
            return false;
        }

        // Content changed or first time
        try {
            Files.createDirectories(historyDir);
        } catch (IOException e) {
            log.error("Failed to create history directory: {}", historyDir, e);
        }

        if (previousHash != null) {
            log.info("Legal document changed: {} ({} -> {})", baseDir, previousHash, currentHash);

            // Read the archived previous content for diff
            Path previousArchive = historyDir.resolve(previousHash + ".md");
            if (Files.exists(previousArchive)) {
                try {
                    String previousMarkdown = Files.readString(previousArchive, StandardCharsets.UTF_8);
                    String diff = generateDiff(previousMarkdown, currentMarkdown);

                    // Write diff file
                    Path diffFile = historyDir.resolve(previousHash + "_to_" + currentHash + ".diff");
                    Files.writeString(diffFile, diff, StandardCharsets.UTF_8);
                    log.info("Diff written to {}", diffFile);
                } catch (IOException e) {
                    log.error("Failed to generate diff", e);
                }
            }
        } else {
            log.info("Legal document initialized: {} (version {})", baseDir, currentHash);
        }

        // Archive current content
        try {
            Path archiveFile = historyDir.resolve(currentHash + ".md");
            Files.writeString(archiveFile, currentMarkdown, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to archive content", e);
        }

        // Write version file
        writeVersionFile(versionFile, currentHash);

        return previousHash != null; // Only report as "changed" if there was a previous version
    }

    /**
     * Retrieves and renders a legal document for the given locale, falling back to the default locale if unavailable.
     *
     * @param baseDir the base directory containing locale subdirectories with markdown files
     * @param locale  the desired locale (e.g. "de", "en")
     * @return the rendered document with HTML, raw markdown, and version hash
     */
    public RenderedDocument getDocument(Path baseDir, String locale) {
        String markdown = readMarkdownDirectory(baseDir, locale);
        if (markdown.isEmpty()) {
            markdown = readMarkdownDirectoryFlat(baseDir);
        }
        if (markdown.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            // Fall back to default locale
            markdown = readMarkdownDirectory(baseDir, DEFAULT_LOCALE);
        }
        String html = renderMarkdown(markdown);
        String version = hash(markdown);
        return new RenderedDocument(html, markdown, version);
    }

    /**
     * Retrieves and renders a legal document using the default locale.
     *
     * @param baseDir the base directory containing the markdown files
     * @return the rendered document with HTML, raw markdown, and version hash
     */
    public RenderedDocument getDocument(Path baseDir) {
        return getDocument(baseDir, DEFAULT_LOCALE);
    }

    /**
     * Gets the diff between two versions. First checks for a pre-computed diff file,
     * then falls back to generating the diff on-demand from archived markdown files.
     * This handles the case where multiple version changes occurred between user logins.
     */
    public String getDiff(Path baseDir, String fromVersion, String toVersion) {
        if (fromVersion == null || toVersion == null || fromVersion.equals(toVersion)) {
            return null;
        }

        Path historyDir = baseDir.resolve("history");

        // Try pre-computed diff first
        Path diffFile = historyDir.resolve(fromVersion + "_to_" + toVersion + ".diff");
        if (Files.exists(diffFile)) {
            try {
                return Files.readString(diffFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to read diff file", e);
            }
        }

        // Fall back to on-demand generation from archived markdown files
        Path fromArchive = historyDir.resolve(fromVersion + ".md");
        Path toArchive = historyDir.resolve(toVersion + ".md");

        if (!Files.exists(fromArchive) || !Files.exists(toArchive)) {
            log.debug(
                    "Cannot generate diff: archived version missing (from={} exists={}, to={} exists={})",
                    fromVersion,
                    Files.exists(fromArchive),
                    toVersion,
                    Files.exists(toArchive));
            return null;
        }

        try {
            String fromMarkdown = Files.readString(fromArchive, StandardCharsets.UTF_8);
            String toMarkdown = Files.readString(toArchive, StandardCharsets.UTF_8);
            String diff = generateDiff(fromMarkdown, toMarkdown);

            // Cache the generated diff for future requests
            try {
                Files.writeString(diffFile, diff, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to cache on-demand diff", e);
            }

            return diff;
        } catch (IOException e) {
            log.error("Failed to generate on-demand diff", e);
            return null;
        }
    }

    /**
     * Generates a human-readable diff between two markdown texts using java-diff-utils.
     */
    String generateDiff(String oldText, String newText) {
        return TextDiff.generateDiffSummary(oldText, newText);
    }

    /**
     * Computes a truncated SHA-256 hash (first 16 hex characters) of the given content.
     *
     * @param content the text to hash
     * @return a 16-character hex string identifying the content version
     */
    String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String renderMarkdown(String markdown) {
        if (markdown.isEmpty()) return "";
        var document = parser.parse(markdown);
        return renderer.render(document);
    }

    /**
     * Reads markdown files from a locale subdirectory: baseDir/locale/*.md
     */
    private String readMarkdownDirectory(Path baseDir, String locale) {
        Path localeDir = baseDir.resolve(locale);
        if (!Files.isDirectory(localeDir)) {
            return "";
        }
        return readMarkdownFiles(localeDir);
    }

    /**
     * Reads markdown files directly from baseDir/*.md (flat layout, backwards compatible).
     */
    private String readMarkdownDirectoryFlat(Path baseDir) {
        if (!Files.isDirectory(baseDir)) {
            return "";
        }
        return readMarkdownFiles(baseDir);
    }

    private String readMarkdownFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path entry : stream) {
                // Skip disabled files (prefixed with _)
                if (entry.getFileName().toString().startsWith("_")) continue;
                files.add(entry);
            }
        } catch (IOException e) {
            log.error("Failed to read markdown directory: {}", dir, e);
            return "";
        }

        Collections.sort(files);

        var sb = new StringBuilder();
        for (Path file : files) {
            try {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("Failed to read markdown file: {}", file, e);
            }
        }
        return sb.toString();
    }

    private String readVersionFile(Path versionFile) {
        if (!Files.exists(versionFile)) {
            return null;
        }
        try {
            String content =
                    Files.readString(versionFile, StandardCharsets.UTF_8).strip();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.error("Failed to read version file: {}", versionFile, e);
            return null;
        }
    }

    private void writeVersionFile(Path versionFile, String hash) {
        try {
            Files.writeString(versionFile, hash + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write version file: {}", versionFile, e);
        }
    }

    /**
     * A rendered legal document containing the HTML output, raw markdown source, and a version hash.
     *
     * @param html     the rendered HTML content
     * @param markdown the raw markdown source
     * @param version  the content hash identifying this version
     */
    public record RenderedDocument(String html, String markdown, String version) {}
}
