/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.DocumentVersion;
import dev.chojo.ember.feature.legal.entity.LegalDocumentType;
import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.util.FilePaths;
import dev.chojo.ember.util.HtmlSanitizer;
import dev.chojo.ember.util.Markdown;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TextDiff;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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

    private final BrowserStorageService browserStorage;
    private final PlaceholderService placeholders;

    public LegalDocumentService() {
        this(null);
    }

    /**
     * @param placeholderFile where the placeholder values are stored; falls back to
     *                        {@value #DEFAULT_PLACEHOLDER_FILE} when null or blank
     */
    public LegalDocumentService(@Nullable String placeholderFile) {
        this(placeholderFile, new BrowserStorageService());
    }

    /**
     * @param placeholderFile where the placeholder values are stored; falls back to
     *                        {@value #DEFAULT_PLACEHOLDER_FILE} when null or blank
     * @param browserStorage  the service rendering the generated browser storage section
     */
    LegalDocumentService(@Nullable String placeholderFile, BrowserStorageService browserStorage) {
        this.browserStorage = browserStorage;
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
     * Initializes a document directory: checks for version changes, archives the content under its
     * version, generates the diff from the previous version and pins the legacy hash
     * ({@link LegacyVersionPin}). A directory without locale subdirectories is read flat.
     *
     * <p>Versions are those of {@link #versionOf(Path)}, so a stored key added to a known category
     * is no change. A version file still holding the whole-document hash of the current text, as
     * written before versions left out the stored keys, is no change either.
     *
     * @return true if the version changed since last startup, false on the very first one
     */
    public boolean initialize(Path baseDir) {
        String currentMarkdown = readMarkdownDirectory(baseDir, DEFAULT_LOCALE, browserStorage::toMarkdown);
        if (currentMarkdown.isEmpty()) {
            currentMarkdown = readMarkdownDirectoryFlat(baseDir, browserStorage::toMarkdown);
        }
        if (currentMarkdown.isEmpty()) {
            log.warn("No markdown content found in {}", baseDir);
            return false;
        }

        String version = version(baseDir, typeSlug(baseDir));
        String wholeDocumentHash = hash(currentMarkdown);
        LegacyVersionPin.pinOnce(baseDir, wholeDocumentHash, version);

        Path versionFile = baseDir.resolve("version.txt");
        Path historyDir = baseDir.resolve("history");
        String previous = readVersionFile(versionFile);
        boolean changed = previous != null && !previous.equals(version) && !previous.equals(wholeDocumentHash);

        try {
            Files.createDirectories(historyDir);
        } catch (IOException e) {
            log.error("Failed to create history directory: {}", historyDir, e);
        }

        if (changed) {
            log.info("Legal document changed: {} ({} -> {})", baseDir, previous, version);
            writeDiffFrom(historyDir, previous, version, currentMarkdown);
        } else {
            log.info("Legal document unchanged: {} (version {})", baseDir, version);
        }

        try {
            Files.writeString(historyDir.resolve(version + ".md"), currentMarkdown, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to archive content", e);
        }

        writeVersionFile(versionFile, version);
        return changed;
    }

    private void writeDiffFrom(Path historyDir, String previous, String version, String currentMarkdown) {
        Path previousArchive = historyDir.resolve(previous + ".md");
        if (!Files.exists(previousArchive)) return;
        try {
            String previousMarkdown = Files.readString(previousArchive, StandardCharsets.UTF_8);
            Path diffFile = historyDir.resolve(previous + "_to_" + version + ".diff");
            Files.writeString(diffFile, generateDiff(previousMarkdown, currentMarkdown), StandardCharsets.UTF_8);
            log.info("Diff written to {}", diffFile);
        } catch (IOException e) {
            log.error("Failed to generate diff", e);
        }
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
                if (FilePaths.nameOf(localeDir).equals("history")) continue;
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
                String name = FilePaths.nameOf(entry);
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
     * <p>A legal page must never come back blank: if the directory holds nothing - because it was
     * pointed somewhere else, emptied by hand, or never laid down - the bundled template for the
     * type takes over. What is served is then what Ember ships rather than nothing at all.
     *
     * @param baseDir  the base directory containing the markdown files
     * @param locale   the desired locale (e.g. "de", "en")
     * @param typeSlug the document type the bundled fallback is taken from
     * @return the rendered document with HTML, raw markdown, and version hash
     */
    public RenderedDocument getDocument(Path baseDir, String locale, @Nullable String typeSlug) {
        String markdown = resolveMarkdown(baseDir, locale, typeSlug, browserStorage::toMarkdown);
        var numbered = LegalNumbering.apply(markdown, styleFor(typeSlug), paragraphSign(locale));
        String html = Markdown.toHtml(numbered.markdown(), HtmlSanitizer.Policy.STRICT);
        if (!numbered.unresolved().isEmpty()) {
            log.warn("Legal document {} refers to sections that do not exist: {}", baseDir, numbered.unresolved());
        }
        return new RenderedDocument(html, markdown, version(baseDir, typeSlug));
    }

    /**
     * The version a consent to a document is given for, and the legacy hash still taken as it.
     *
     * <p>The version is taken over the default locale with the generated browser storage section
     * counted by its categories alone instead of by every stored key: a key added to a category
     * already disclosed leaves it as it was, while a new category, a reworded one or a change to
     * the written text moves it. A document without that section is versioned by its text alone.
     *
     * <p>The legacy hash is the one {@link LegacyVersionPin} holds while the version is still the
     * one it was pinned to, otherwise the whole-document hash of the document as it reads now.
     *
     * @param baseDir the base directory containing the markdown files
     * @return the version and the legacy hash of the document
     */
    public DocumentVersion versionOf(Path baseDir) {
        String version = version(baseDir, typeSlug(baseDir));
        String legacy = LegacyVersionPin.pinnedFor(baseDir, version).orElseGet(() -> wholeDocumentHash(baseDir));
        return new DocumentVersion(version, legacy);
    }

    private String version(Path baseDir, @Nullable String typeSlug) {
        return hash(resolveMarkdown(baseDir, DEFAULT_LOCALE, typeSlug, browserStorage::categorySummary));
    }

    /**
     * The hash versions were taken from before they left out the stored keys: the whole document
     * in the default locale, every stored key included.
     */
    String wholeDocumentHash(Path baseDir) {
        return hash(resolveMarkdown(baseDir, DEFAULT_LOCALE, typeSlug(baseDir), browserStorage::toMarkdown));
    }

    /**
     * Assembles the markdown of a document: the locale asked for, the flat layout, the default
     * locale, and the bundled template of the type, in that order, whichever first holds anything.
     *
     * @param storageSection renders the generated browser storage section for a locale
     */
    private String resolveMarkdown(
            Path baseDir, String locale, @Nullable String typeSlug, Function<String, String> storageSection) {
        String markdown = readMarkdownDirectory(baseDir, locale, storageSection);
        if (markdown.isEmpty()) {
            markdown = readMarkdownDirectoryFlat(baseDir, storageSection);
        }
        if (markdown.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            markdown = readMarkdownDirectory(baseDir, DEFAULT_LOCALE, storageSection);
        }
        if (markdown.isEmpty()) {
            markdown = readBundled(typeSlug, locale, storageSection);
            if (markdown.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
                markdown = readBundled(typeSlug, DEFAULT_LOCALE, storageSection);
            }
            if (!markdown.isEmpty()) {
                log.warn(
                        "No legal document in {} for locale {} - serving the bundled {} template instead",
                        baseDir,
                        locale,
                        typeSlug);
            }
        }
        return markdown;
    }

    /**
     * How a document type counts its sections.
     *
     * <p>Only the terms of service number their sections, and they already do: taking the numbers
     * out of the headings and assigning them while rendering reproduces them exactly, as long as
     * the order has not changed. Every other document is left as it reads, and a reference into it
     * carries the section title instead of a number.
     */
    private static LegalNumbering.Style styleFor(@Nullable String typeSlug) {
        return LegalDocumentType.TOS.slug().equals(typeSlug)
                ? LegalNumbering.Style.PARAGRAPH
                : LegalNumbering.Style.NONE;
    }

    /**
     * What a paragraph is called in the given locale. German legal texts use the section sign,
     * English ones spell the word out.
     */
    private static String paragraphSign(String locale) {
        return "en".equalsIgnoreCase(locale) ? "Section" : "§";
    }

    /**
     * Assembles the bundled document of a type the same way a directory of sections is assembled,
     * so the generated sections carry their generated content here too.
     */
    private String readBundled(@Nullable String typeSlug, String locale, Function<String, String> storageSection) {
        if (typeSlug == null) return "";
        var sb = new StringBuilder();
        for (var section : DataInitializer.bundledDocument(typeSlug, locale)) {
            String content = BrowserStorageService.isGeneratedSection(section.displayName())
                    ? storageSection.apply(locale)
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
    private static @Nullable String typeSlug(Path baseDir) {
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
     *
     * <p>A legacy hash whose content was never archived is read as the version it was pinned to
     * ({@link LegacyVersionPin}). Where neither is known, there is no diff and the reader is shown
     * the current text alone.
     */
    public @Nullable String getDiff(Path baseDir, @Nullable String fromVersion, @Nullable String toVersion) {
        String diff = archivedDiff(baseDir, fromVersion, toVersion);
        if (diff != null || fromVersion == null) return diff;
        return LegacyVersionPin.versionPinnedTo(baseDir, fromVersion)
                .map(pinned -> archivedDiff(baseDir, pinned, toVersion))
                .orElse(null);
    }

    private @Nullable String archivedDiff(Path baseDir, @Nullable String fromVersion, @Nullable String toVersion) {
        if (fromVersion == null || toVersion == null || fromVersion.equals(toVersion)) {
            return null;
        }

        Path historyDir = baseDir.resolve("history");

        Path diffFile = historyDir.resolve(fromVersion + "_to_" + toVersion + ".diff");
        if (Files.exists(diffFile)) {
            try {
                return Files.readString(diffFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to read diff file", e);
            }
        }

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
        return Sha256.hexPrefix(content, 16);
    }

    /**
     * Reads markdown files from a locale subdirectory: baseDir/locale/*.md
     */
    private String readMarkdownDirectory(Path baseDir, String locale, Function<String, String> storageSection) {
        Path localeDir = baseDir.resolve(locale);
        if (!Files.isDirectory(localeDir)) {
            return "";
        }
        return readMarkdownFiles(localeDir, locale, storageSection);
    }

    /**
     * Reads markdown files directly from baseDir/*.md (flat layout, backwards compatible).
     */
    private String readMarkdownDirectoryFlat(Path baseDir, Function<String, String> storageSection) {
        if (!Files.isDirectory(baseDir)) {
            return "";
        }
        return readMarkdownFiles(baseDir, DEFAULT_LOCALE, storageSection);
    }

    private String readMarkdownFiles(Path dir, String locale, Function<String, String> storageSection) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path entry : stream) {
                if (isSwitchedOff(entry)) continue;
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
                String name = FilePaths.nameOf(file);
                if (BrowserStorageService.isGeneratedSection(name)) {
                    sb.append(storageSection.apply(locale));
                } else {
                    sb.append(placeholders.apply(Files.readString(file, StandardCharsets.UTF_8)));
                }
            } catch (IOException e) {
                log.error("Failed to read markdown file: {}", file, e);
            }
        }
        return sb.toString();
    }

    /** Whether a markdown file is switched off, which an underscore at the start of its name says. */
    private static boolean isSwitchedOff(Path markdownFile) {
        return FilePaths.nameOf(markdownFile).startsWith("_");
    }

    private @Nullable String readVersionFile(Path versionFile) {
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
