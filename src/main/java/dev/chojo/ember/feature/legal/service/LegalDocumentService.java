/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.util.HtmlSanitizer;
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
import java.util.regex.Pattern;

/**
 * Manages versioned legal documents (privacy policy, terms of service, consent text).
 * Each document type has a directory with locale subdirectories containing markdown files.
 * On initialization, detects changes by comparing content hashes against a version file,
 * archives previous versions, and generates diffs.
 *
 * <p>Directory structure:
 * <pre>
 * data/documents/privacy/
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
    private static final String DEFAULT_PLACEHOLDER_FILE = "data/documents/placeholders.json";
    private static final Pattern ORDER_PREFIX = Pattern.compile("^_?(\\d+)-");

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final BrowserStorageService browserStorage;
    private final PlaceholderService placeholders;

    public LegalDocumentService() {
        this(null);
    }

    /**
     * @param placeholderFile where the placeholder values are stored; falls back to
     *                        {@value #DEFAULT_PLACEHOLDER_FILE} when null or blank
     */
    public LegalDocumentService(String placeholderFile) {
        List<Extension> extensions =
                List.of(TablesExtension.create(), HeadingAnchorExtension.create(), AutolinkExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer =
                HtmlRenderer.builder().extensions(extensions).sanitizeUrls(true).build();
        this.browserStorage = new BrowserStorageService();
        this.placeholders = new PlaceholderService(Path.of(
                placeholderFile == null || placeholderFile.isBlank() ? DEFAULT_PLACEHOLDER_FILE : placeholderFile));
    }

    /**
     * Returns the service rendering the generated browser storage disclosure.
     *
     * @return the browser storage service backing generated sections
     */
    public BrowserStorageService browserStorage() {
        return browserStorage;
    }

    /**
     * Returns the service resolving the placeholders used across the documents.
     *
     * @return the placeholder service backing substitution
     */
    public PlaceholderService placeholders() {
        return placeholders;
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
     * Ensures every locale of a document directory carries the generated browser storage section.
     * Existing installations gain the section behind their hand-written ones; where it is already
     * present, its position and its enabled state are left untouched.
     *
     * @param baseDir the base directory containing locale subdirectories with markdown files
     */
    public void ensureGeneratedSection(Path baseDir) {
        if (!Files.isDirectory(baseDir)) return;
        try (DirectoryStream<Path> locales = Files.newDirectoryStream(baseDir, Files::isDirectory)) {
            for (Path localeDir : locales) {
                if (localeDir.getFileName().toString().equals("history")) continue;
                ensureGeneratedSectionInLocale(localeDir);
            }
        } catch (IOException e) {
            log.error("Failed to ensure generated section in {}", baseDir, e);
        }
    }

    private void ensureGeneratedSectionInLocale(Path localeDir) {
        int highestPrefix = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (BrowserStorageService.isGeneratedSection(name)) return;
                var matcher = ORDER_PREFIX.matcher(name);
                if (matcher.find()) {
                    highestPrefix = Math.max(highestPrefix, Integer.parseInt(matcher.group(1)));
                }
            }
        } catch (IOException e) {
            log.error("Failed to inspect legal section files in {}", localeDir, e);
            return;
        }

        Path file =
                localeDir.resolve(String.format("%02d-%s.md", highestPrefix + 1, BrowserStorageService.SECTION_NAME));
        try {
            Files.writeString(file, "", StandardCharsets.UTF_8);
            log.info("Added generated browser storage section: {}", file);
        } catch (IOException e) {
            log.error("Failed to create generated section {}", file, e);
        }
    }

    /**
     * Retrieves and renders a legal document for the given locale, falling back to the default locale if unavailable.
     *
     * @param baseDir the base directory containing locale subdirectories with markdown files
     * @param locale  the desired locale (e.g. "de", "en")
     * @return the rendered document with HTML, raw markdown, and version hash
     */
    public RenderedDocument getDocument(Path baseDir, String locale) {
        return getDocument(baseDir, locale, typeSlug(baseDir));
    }

    /**
     * Retrieves and renders a legal document.
     *
     * <p>A legal page must never come back blank: if the directory holds nothing — because it was
     * pointed somewhere else, emptied by hand, or never laid down — the bundled template for the
     * type takes over. What is served is then what Ember ships rather than nothing at all.
     *
     * @param baseDir  the base directory containing the markdown files
     * @param locale   the desired locale (e.g. "de", "en")
     * @param typeSlug the document type the bundled fallback is taken from
     * @return the rendered document with HTML, raw markdown, and version hash
     */
    public RenderedDocument getDocument(Path baseDir, String locale, String typeSlug) {
        String markdown = readMarkdownDirectory(baseDir, locale);
        if (markdown.isEmpty()) {
            markdown = readMarkdownDirectoryFlat(baseDir);
        }
        if (markdown.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            // Fall back to default locale
            markdown = readMarkdownDirectory(baseDir, DEFAULT_LOCALE);
        }
        if (markdown.isEmpty()) {
            markdown = readBundled(typeSlug, locale);
            if (markdown.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
                markdown = readBundled(typeSlug, DEFAULT_LOCALE);
            }
            if (!markdown.isEmpty()) {
                log.warn(
                        "No legal document in {} for locale {} — serving the bundled {} template instead",
                        baseDir,
                        locale,
                        typeSlug);
            }
        }
        String html = renderMarkdown(markdown);
        String version = hash(markdown);
        return new RenderedDocument(html, markdown, version);
    }

    /**
     * Assembles the bundled document of a type the same way a directory of sections is assembled,
     * so the generated sections carry their generated content here too.
     */
    private String readBundled(String typeSlug, String locale) {
        if (typeSlug == null) return "";
        var sb = new StringBuilder();
        for (var section : DataInitializer.bundledDocument(typeSlug, locale)) {
            String content = BrowserStorageService.isGeneratedSection(section.displayName())
                    ? browserStorage.toMarkdown(locale)
                    : placeholders.apply(section.content());
            if (content.isBlank()) continue;
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(content);
        }
        return sb.toString();
    }

    /**
     * The document type a directory stands for, taken from its name. Configuration may move the
     * directory, but not rename what it holds.
     */
    private static String typeSlug(Path baseDir) {
        Path name = baseDir.getFileName();
        return name == null ? null : name.toString();
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
        String html = renderer.render(document);
        return HtmlSanitizer.sanitize(html, HtmlSanitizer.Policy.STRICT);
    }

    /**
     * Reads markdown files from a locale subdirectory: baseDir/locale/*.md
     */
    private String readMarkdownDirectory(Path baseDir, String locale) {
        Path localeDir = baseDir.resolve(locale);
        if (!Files.isDirectory(localeDir)) {
            return "";
        }
        return readMarkdownFiles(localeDir, locale);
    }

    /**
     * Reads markdown files directly from baseDir/*.md (flat layout, backwards compatible).
     */
    private String readMarkdownDirectoryFlat(Path baseDir) {
        if (!Files.isDirectory(baseDir)) {
            return "";
        }
        return readMarkdownFiles(baseDir, DEFAULT_LOCALE);
    }

    private String readMarkdownFiles(Path dir, String locale) {
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
                String name = file.getFileName().toString();
                if (BrowserStorageService.isGeneratedSection(name)) {
                    sb.append(browserStorage.toMarkdown(locale));
                } else {
                    sb.append(placeholders.apply(Files.readString(file, StandardCharsets.UTF_8)));
                }
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
