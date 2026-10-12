/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.font.BuiltInFonts;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.wordform.WordForms;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.PandocConverter;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Makes a PDF template out of a Word form, so the form can be filled out from inside Ember.
 *
 * <p>{@link WordForms} turns the form into a PDF and lays a field over every place of it: the member's
 * data where the label of a place is recognised, a question to the person generating everywhere else.
 * The template is named after the file, counted on where the name is taken, and opens in the editor,
 * where everything can be corrected with the usual tools.
 *
 * <p>Fonts the form names that the owner reaches are handed to LibreOffice, so the page is drawn in them.
 * For every other font the form sets text in, the person importing chooses the family it is drawn in
 * before the form is converted; nothing is replaced unasked. A built-in family is offered first where one
 * stands in for the font ({@link FontStandIns}), marked where it has the same measure, such as Carlito for
 * Calibri. A light weight such as Frutiger 45 Light is drawn in the light cut of the family chosen where it
 * ships one. Every replacement is reported.
 */
@Singleton
public class WordFormImportService {
    private static final Logger log = LoggerFactory.getLogger(WordFormImportService.class);

    private final DocumentTemplateService templates;
    private final PdfTemplateService pdfs;
    private final TemplateChecks checks;
    private final PlaceholderCatalogue catalogue;
    private final FontLibrary fonts;

    @Inject
    public WordFormImportService(
            DocumentTemplateService templates,
            PdfTemplateService pdfs,
            TemplateChecks checks,
            PlaceholderCatalogue catalogue,
            FontLibrary fonts) {
        this.templates = templates;
        this.pdfs = pdfs;
        this.checks = checks;
        this.catalogue = catalogue;
        this.fonts = fonts;
    }

    /**
     * A template made from a Word form.
     *
     * @param template   the template as it was made
     * @param recognised the labels of the places that print the member's data
     * @param asked      the labels of the places the person generating fills in
     * @param unplaced   the labels of the places that got no field, to be drawn by hand
     * @param fonts      the families the form names that were drawn in another, as they were chosen
     */
    public record WordFormImport(
            DocumentTemplateResponse template,
            List<String> recognised,
            List<String> asked,
            List<String> unplaced,
            List<FontReplacement> fonts) {}

    /**
     * A family a form names that is drawn in another one.
     *
     * @param family      the family the form names
     * @param replacement the family it is drawn in
     */
    public record FontReplacement(String family, String replacement) {}

    /**
     * The fonts of a Word form the owner cannot draw it in.
     *
     * @param missing       the families the form sets text in that the owner neither uploaded nor reaches
     * @param suggested     the built-in family offered first in place of each missing one, by the family the
     *                      form names, for those a built-in family stands in for
     * @param defaultFamily the family a missing one is drawn in where the default font is chosen
     */
    public record MissingFonts(List<String> missing, Map<String, Suggestion> suggested, String defaultFamily) {}

    /**
     * A built-in family offered first in place of a missing one.
     *
     * @param family      the built-in family
     * @param sameMeasure whether it sets every letter as wide as the missing one, so the form keeps its lines
     */
    public record Suggestion(String family, boolean sameMeasure) {}

    /**
     * Which fonts a Word form names that its owner cannot draw it in, so a replacement can be chosen for
     * each before it is imported.
     *
     * @param owner the station or the association that would keep the template
     * @param file  the upload, or null where the request carried none
     * @return the families without a font to draw them in
     */
    public MissingFonts fontsOf(Owner owner, @Nullable UploadedFile file) {
        var upload = wordForm(owner, file);
        var reachable = fonts.reachable(owner);
        var missing = missing(namedIn(owner, upload.data()), reachable);
        var suggested = new LinkedHashMap<String, Suggestion>();
        for (String family : missing) {
            FontStandIns.of(family).ifPresent(standIn -> reachable
                    .find(standIn.family())
                    .ifPresent(found -> suggested.put(family, new Suggestion(found.name(), standIn.sameMeasure()))));
        }
        return new MissingFonts(missing, suggested, fonts.defaultFont().printedFamily());
    }

    /**
     * Makes a PDF template from a Word form.
     *
     * <p>Every family the form names that the owner cannot draw needs a replacement: one of the families the
     * owner reaches, or none for the default font. A missing family without one is refused by name, so no
     * form is ever drawn in a font nobody chose.
     *
     * @param owner        the station or the association that keeps the template
     * @param file         the upload, or null where the request carried none
     * @param replacements the family each missing one is drawn in, empty for the default font, by the
     *                     family the form names
     * @param authorId     the account that makes it
     * @return the template and what was recognised
     */
    public WordFormImport importForm(
            Owner owner, @Nullable UploadedFile file, Map<String, String> replacements, int authorId) {
        var upload = wordForm(owner, file);
        var reachable = fonts.reachable(owner);
        var named = namedIn(owner, upload.data());
        var chosen = replacementsFor(missing(named, reachable), replacements, reachable);
        chosen.replaceAll((family, replacement) -> FontStandIns.light(family)
                ? BuiltInFonts.lightCut(replacement.name()).orElse(replacement)
                : replacement);
        var drawn = new ArrayList<FontFamily>(chosen.values());
        named.stream()
                .filter(family -> !chosen.containsKey(family))
                .forEach(family -> reachable.find(family).ifPresent(drawn::add));
        WordForms.Converted form;
        try {
            form = WordForms.convert(new WordForms.Form(
                    upload.data(),
                    namesOf(chosen),
                    fontDirectories(drawn),
                    GapWords.fieldsOf(catalogue.answerable(owner))));
        } catch (IOException e) {
            log.warn("A Word form could not be converted for {}", owner, e);
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FAILED.raise();
        }
        if (form.fields().isEmpty()) throw DocumentRefusal.DOCUMENT_FORM_IMPORT_NO_FIELDS.raise();
        String name = checks.freeCopyName(owner, nameOf(upload.fileName()));
        var made = templates.create(owner, request(name, null, form.questions()), authorId);
        pdfs.takeMade(owner, made.id(), name + ".pdf", form.pdf(), authorId);
        var template = templates.update(owner, made.id(), request(name, form.fields(), form.questions()), authorId);
        log.info("Document template {} made from a Word form for {}", template.id(), owner);
        return new WordFormImport(
                template,
                form.recognised(),
                form.questions().stream().map(TemplateQuestion::label).toList(),
                form.unplaced(),
                chosen.entrySet().stream()
                        .map(entry -> new FontReplacement(
                                entry.getKey(), entry.getValue().name()))
                        .toList());
    }

    private static Map<String, String> namesOf(Map<String, FontFamily> chosen) {
        var names = new LinkedHashMap<String, String>();
        chosen.forEach((family, replacement) -> names.put(family, replacement.name()));
        return names;
    }

    /** The upload, refused where it is no Word document or one without a place to fill in. */
    private DocumentIntake.Upload wordForm(Owner owner, @Nullable UploadedFile file) {
        var upload = pdfs.receive(owner, file);
        String format = PandocConverter.formatOf(upload.data(), upload.fileName(), upload.declaredType())
                .orElse("");
        if (!"docx".equals(format)) throw DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN.raise();
        boolean form;
        try {
            form = WordForms.hasPlaces(upload.data());
        } catch (IOException e) {
            log.warn("A Word form could not be read for {}", owner, e);
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FAILED.raise();
        }
        if (!form) throw DocumentRefusal.DOCUMENT_FORM_IMPORT_NO_FIELDS.raise();
        return upload;
    }

    /** The families a form names, refused as unreadable where they cannot be read. */
    private List<String> namedIn(Owner owner, byte[] docx) {
        try {
            return WordForms.fontsOf(docx);
        } catch (IOException e) {
            log.warn("The fonts of a Word form could not be read for {}", owner, e);
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FAILED.raise();
        }
    }

    /** The families the owner cannot draw: neither reached nor the default font, and no font of symbols. */
    private List<String> missing(List<String> named, ReachableFonts reachable) {
        String defaultFamily = fonts.defaultFont().printedFamily();
        return named.stream()
                .filter(family -> reachable.find(family).isEmpty())
                .filter(family -> !family.equalsIgnoreCase(defaultFamily))
                .filter(family -> !FontStandIns.symbols(family))
                .toList();
    }

    /**
     * The family each missing one is drawn in, checked: every missing family has one, and each is a family
     * the owner reaches; none chosen stands for the default font.
     */
    private Map<String, FontFamily> replacementsFor(
            List<String> missing, Map<String, String> asked, ReachableFonts reachable) {
        var chosen = new LinkedHashMap<String, FontFamily>();
        for (String family : missing) {
            if (!asked.containsKey(family)) {
                throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FONT_UNCHOSEN.raise(RefusalDetail.text(family));
            }
            String replacement =
                    Objects.requireNonNullElse(asked.get(family), "").strip();
            if (replacement.isEmpty()) {
                chosen.put(family, defaultFamily(reachable));
                continue;
            }
            chosen.put(
                    family,
                    reachable
                            .find(replacement)
                            .orElseThrow(() -> DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN.raise(
                                    RefusalDetail.text(replacement))));
        }
        return chosen;
    }

    /**
     * The default font as a family: the one the owner reaches under its name, or the instance's default font
     * without files of its own, which LibreOffice finds in the default font's directory.
     */
    private FontFamily defaultFamily(ReachableFonts reachable) {
        String name = fonts.defaultFont().printedFamily();
        return reachable.find(name).orElseGet(() -> new FontFamily(name, FontOrigin.INSTANCE, Map.of()));
    }

    /**
     * The directories LibreOffice finds the families in: one holding the files of the families drawn, and
     * the default font's own directory where it is one of them.
     */
    private List<Path> fontDirectories(List<FontFamily> drawn) {
        var files = new ArrayList<Path>();
        drawn.forEach(family ->
                family.files().values().forEach(face -> fonts.keptFile(face).ifPresent(files::add)));
        var directories = new ArrayList<Path>();
        if (!files.isEmpty()) directories.add(fonts.directoryOf(files));
        var defaultFont = fonts.defaultFont();
        if (drawn.stream().anyMatch(family -> family.name().equalsIgnoreCase(defaultFont.printedFamily()))) {
            defaultFont.directory().ifPresent(directories::add);
        }
        return directories;
    }

    private static String nameOf(@Nullable String fileName) {
        String name = fileName == null ? "" : fileName.strip();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = name.replace('_', ' ').strip();
        return name.isEmpty() ? "Formular" : name;
    }

    private static DocumentTemplateRequest request(
            String name, @Nullable List<PdfField> fields, List<TemplateQuestion> questions) {
        return new DocumentTemplateRequest(
                DocumentTemplateKind.PDF,
                name,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                fields,
                null,
                null,
                questions);
    }
}
