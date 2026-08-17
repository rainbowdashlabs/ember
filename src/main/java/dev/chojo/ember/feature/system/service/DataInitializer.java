/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Api;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Initializes the data directory from bundled templates if files are not present.
 * Templates are stored as classpath resources under {@code /templates/data/}.
 */
@Singleton
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final Map<String, Path> documentDirs;

    /**
     * @param apiConfig where the legal documents are read from; the templates are laid down there
     *                  rather than under {@code data/}, so an instance that points its documents
     *                  somewhere else still starts with a complete set
     */
    @Inject
    public DataInitializer(Api apiConfig) {
        this.documentDirs = Map.of(
                "consent", Path.of(apiConfig.consentDir()),
                "imprint", Path.of(apiConfig.imprintDir()),
                "privacy", Path.of(apiConfig.privacyPolicyDir()),
                "tos", Path.of(apiConfig.tosDir()));
    }

    /**
     * Where the bundled templates sit relative to the working directory.
     */
    private static final Path TEMPLATE_ROOT = Path.of("templates", "data");

    private static final String[] TEMPLATE_FILES = {
        "documents/consent/de/01-consent.md",
        "documents/consent/de/02-browser-storage.md",
        "documents/consent/en/01-consent.md",
        "documents/consent/en/02-browser-storage.md",
        "documents/imprint/de/01-impressum.md",
        "documents/imprint/en/01-imprint.md",
        "documents/privacy/de/01-general.md",
        "documents/privacy/de/02-rights.md",
        "documents/privacy/de/03-browser-storage.md",
        "documents/privacy/de/04-sicherheit.md",
        "documents/privacy/de/_05-mailversand.md",
        "documents/privacy/de/_05-mailversand-brevo.md",
        "documents/privacy/de/_05-mailversand-sweego.md",
        "documents/privacy/de/_05-mailversand-sendgrid.md",
        "documents/privacy/de/_05-mailversand-rapidmail.md",
        "documents/privacy/en/01-general.md",
        "documents/privacy/en/02-rights.md",
        "documents/privacy/en/03-browser-storage.md",
        "documents/privacy/en/04-security.md",
        "documents/privacy/en/_05-mail-delivery.md",
        "documents/privacy/en/_05-mail-delivery-brevo.md",
        "documents/privacy/en/_05-mail-delivery-sweego.md",
        "documents/privacy/en/_05-mail-delivery-sendgrid.md",
        "documents/privacy/en/_05-mail-delivery-rapidmail.md",
        "documents/tos/de/01-grundlagen.md",
        "documents/tos/de/02-konto.md",
        "documents/tos/de/03-nutzungsregeln.md",
        "documents/tos/de/04-funktionen.md",
        "documents/tos/de/05-wachen.md",
        "documents/tos/de/06-betrieb.md",
        "documents/tos/de/_07-mailversand.md",
        "documents/tos/en/01-basics.md",
        "documents/tos/en/02-account.md",
        "documents/tos/en/03-conduct.md",
        "documents/tos/en/04-features.md",
        "documents/tos/en/05-stations.md",
        "documents/tos/en/06-operations.md",
        "documents/tos/en/_07-mail-delivery.md",
    };

    /**
     * Returns the sections of the bundled legal document template for the given type and locale,
     * in the order they are laid out. Sections carrying no content of their own - the generated
     * ones - are left out, so the result is what an administrator can actually load into the editor.
     *
     * @param typeSlug the document type as used in the data directory ({@code privacy}, {@code tos}, …)
     * @param locale   the desired locale (e.g. "de", "en")
     * @return the bundled sections, empty if Ember ships none for this combination
     */
    public static List<TemplateSection> documentTemplates(String typeSlug, String locale) {
        String directory = "documents/" + typeSlug + "/" + locale + "/";
        List<TemplateSection> sections = new ArrayList<>();
        for (String templateFile : TEMPLATE_FILES) {
            if (!templateFile.startsWith(directory)) continue;
            String content = readTemplate(templateFile);
            if (content == null || content.isBlank()) continue;
            String displayName = templateFile
                    .substring(directory.length())
                    .replaceFirst("^_?\\d+-", "")
                    .replaceFirst("\\.md$", "");
            sections.add(new TemplateSection(displayName, content));
        }
        return sections;
    }

    /**
     * Returns the bundled sections for a document type and locale in the order they are laid out,
     * generated ones included as empty content and switched-off ones left out. Unlike {@link #documentTemplates(String, String)}
     * this keeps the placeholders for generated sections, so a caller can render a complete
     * document from the templates alone.
     *
     * @param typeSlug the document type as used in the data directory ({@code privacy}, {@code tos}, …)
     * @param locale   the desired locale (e.g. "de", "en")
     * @return the bundled sections, empty if Ember ships none for this combination
     */
    public static List<TemplateSection> bundledDocument(String typeSlug, String locale) {
        String directory = "documents/" + typeSlug + "/" + locale + "/";
        List<TemplateSection> sections = new ArrayList<>();
        for (String templateFile : TEMPLATE_FILES) {
            if (!templateFile.startsWith(directory)) continue;
            String name = fileName(templateFile);
            // A section Ember ships switched off is not part of the document until someone enables it.
            if (name.startsWith("_")) continue;
            String content = readTemplate(templateFile);
            sections.add(new TemplateSection(name, content == null ? "" : content));
        }
        return sections;
    }

    /**
     * Opens a bundled template. Templates ship as a directory next to the application rather than
     * inside the jar, so the working directory is the source of truth and the classpath is only a
     * fallback for deployments that do package them.
     *
     * @return the open stream, or null if the template is nowhere to be found
     */
    private static InputStream openTemplate(String templateFile) throws IOException {
        Path onDisk = TEMPLATE_ROOT.resolve(templateFile);
        if (Files.isRegularFile(onDisk)) {
            return Files.newInputStream(onDisk);
        }
        return DataInitializer.class.getResourceAsStream("/templates/data/" + templateFile);
    }

    private static String readTemplate(String templateFile) {
        try (InputStream in = openTemplate(templateFile)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read template {}", templateFile, e);
            return null;
        }
    }

    /**
     * One section of a bundled legal document template.
     *
     * @param displayName the section name without ordering prefix or extension
     * @param content     the markdown the section carries
     */
    public record TemplateSection(String displayName, String content) {}

    /**
     * Copies bundled templates into the data directory and ensures the runtime directories exist.
     *
     * <p>Templates are laid down per document and locale, never file by file: a locale that already
     * holds a document keeps exactly what is there, and a locale that holds nothing gets the complete
     * bundled set. Copying single missing files would append a renamed or restructured template
     * alongside an operator's existing sections and publish both.
     */
    public void initialize() {
        Path dataDir = Path.of("data");
        int copied = 0;

        for (var group : groupedTemplates().entrySet()) {
            Path targetDir = targetDirectory(group.getKey());
            if (containsDocument(targetDir)) {
                continue;
            }
            for (String templateFile : group.getValue()) {
                if (copyTemplate(targetDir, fileName(templateFile), templateFile)) copied++;
            }
            log.info("Initialized legal document templates in {}", targetDir);
        }

        // Ensure runtime directories exist
        ensureDirectory(dataDir.resolve("images"));
        ensureDirectory(dataDir.resolve("kb-files"));

        if (copied > 0) {
            log.info("Initialized {} template files", copied);
        }
    }

    /**
     * Where a bundled group belongs. A legal document goes to the directory its type is configured
     * with; anything else keeps its place below {@code data/}.
     *
     * @param group the directory the template declares, e.g. {@code documents/privacy/de}
     */
    private Path targetDirectory(String group) {
        String[] parts = group.split("/");
        if (parts.length == 3 && "documents".equals(parts[0])) {
            Path configured = documentDirs.get(parts[1]);
            if (configured != null) return configured.resolve(parts[2]);
        }
        return Path.of("data").resolve(group);
    }

    private static String fileName(String templateFile) {
        return templateFile.substring(templateFile.lastIndexOf('/') + 1);
    }

    /**
     * Groups the bundled templates by the directory they belong to, keeping their declared order.
     */
    private static Map<String, List<String>> groupedTemplates() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String templateFile : TEMPLATE_FILES) {
            int lastSlash = templateFile.lastIndexOf('/');
            String directory = lastSlash < 0 ? "" : templateFile.substring(0, lastSlash);
            groups.computeIfAbsent(directory, _ -> new ArrayList<>()).add(templateFile);
        }
        return groups;
    }

    /**
     * Whether the given directory already holds a document, enabled or disabled.
     */
    private static boolean containsDocument(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            log.error("Failed to inspect {}", dir, e);
            return true;
        }
    }

    private boolean copyTemplate(Path targetDir, String name, String templateFile) {
        Path target = targetDir.resolve(name);
        try (InputStream in = openTemplate(templateFile)) {
            if (in == null) {
                log.warn("Template not found: {}", templateFile);
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
            return true;
        } catch (IOException e) {
            log.error("Failed to copy template {} to {}", templateFile, target, e);
            return false;
        }
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create directory {}", dir, e);
        }
    }
}
