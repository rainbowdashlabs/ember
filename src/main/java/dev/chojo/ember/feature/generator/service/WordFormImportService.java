/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.entity.SignerCaptions;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.font.BuiltInFonts;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.FontFiles;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.wordform.FontChoice;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Makes a PDF template out of a Word form, so the form can be filled out from inside Ember.
 *
 * <p>{@link WordForms} turns the form into a PDF and lays a field over every place of it: the member's
 * data where the label of a place is recognised, a question to the person generating everywhere else.
 * The template is named after the file, counted on where the name is taken, and opens in the editor,
 * where everything can be corrected with the usual tools.
 *
 * <p>Fonts the form names that the owner reaches are handed to LibreOffice, so the page is drawn in them.
 * For every other font the form sets text in, the person importing chooses the family it is drawn in and
 * the size, in percent of the form's, and the weight before the form is converted; nothing is replaced
 * unasked. A built-in family is offered first where one stands in for the font ({@link FontStandIns}),
 * marked where it has the same measure, such as Carlito for Calibri. A font whose name says light, such as
 * Frutiger 45 Light, is drawn in the light cut of the family chosen unless another weight is asked for.
 * Every replacement is reported. A field prints
 * in the family its place is drawn in where that family can print on a PDF, otherwise in the default font.
 *
 * <p>Before importing, the form can be drawn twice to compare: in its own fonts, those it embeds and font
 * files handed in for that one drawing only, and as it will be drawn with the replacements chosen.
 */
@Singleton
public class WordFormImportService {
    private static final Logger log = LoggerFactory.getLogger(WordFormImportService.class);

    /** The most font files a preview of a form in its own fonts takes. */
    static final int MAX_PREVIEW_FONTS = 8;

    private final DocumentTemplateService templates;
    private final PdfTemplateService pdfs;
    private final TemplateChecks checks;
    private final PlaceholderCatalogue catalogue;
    private final FontLibrary fonts;
    private final PdfTemplateRenderer renderer;

    @Inject
    public WordFormImportService(
            DocumentTemplateService templates,
            PdfTemplateService pdfs,
            TemplateChecks checks,
            PlaceholderCatalogue catalogue,
            FontLibrary fonts,
            PdfTemplateRenderer renderer) {
        this.templates = templates;
        this.pdfs = pdfs;
        this.checks = checks;
        this.catalogue = catalogue;
        this.fonts = fonts;
        this.renderer = renderer;
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
     * @param size        the size it is drawn in, in percent of the form's
     */
    public record FontReplacement(String family, String replacement, int size) {}

    /**
     * The fonts of a Word form the owner cannot draw it in.
     *
     * @param missing       the families the form sets text in that the owner neither uploaded nor reaches
     * @param suggested     the built-in family offered first in place of each missing one, by the family the
     *                      form names, for those a built-in family stands in for
     * @param defaultFamily the family a missing one is drawn in where the default font is chosen
     * @param embedded      the missing families the form carries font files for, which draw its preview in
     *                      its own fonts without a file handed in
     * @param light         the missing families whose names say they are a light cut, such as Frutiger 45 Light
     * @param lightCuts     the families that can be drawn in a light weight
     */
    public record MissingFonts(
            List<String> missing,
            Map<String, Suggestion> suggested,
            String defaultFamily,
            List<String> embedded,
            List<String> light,
            List<String> lightCuts) {}

    /**
     * A built-in family offered first in place of a missing one.
     *
     * @param family      the built-in family
     * @param sameMeasure whether it sets every letter as wide as the missing one, so the form keeps its lines
     */
    public record Suggestion(String family, boolean sameMeasure) {}

    /**
     * A replacement as it is asked for.
     *
     * @param family the family to draw in, empty or null for the default font
     * @param size   the size to draw in, in percent of the form's, null for the form's own size
     * @param weight the weight to draw in, null for the one the name of the missing family says
     */
    public record ChosenFont(
            @Nullable String family,
            @Nullable Integer size,
            @Nullable FontWeight weight) {}

    /** The weight a replacement is drawn in. */
    public enum FontWeight {
        /** The light cut of the family. */
        LIGHT,
        /** The family as it is. */
        REGULAR
    }

    /**
     * A font file handed in for the original of a form, for one preview only.
     *
     * @param family the family the form names that the file draws
     * @param file   the font file
     */
    public record OriginalFont(String family, UploadedFile file) {}

    /** Which drawing of a form a preview shows. */
    public enum PreviewVersion {
        /** The form in its own fonts. */
        ORIGINAL,
        /** The form with the replacements chosen. */
        REPLACED
    }

    /**
     * How a form is drawn with the replacements chosen.
     *
     * @param chosen        the family and size each missing family is drawn in, by the family the form names
     * @param choices       the same, as the form is rewritten with them
     * @param drawn         every family the form is drawn in that LibreOffice needs files of
     * @param fieldFamilies the family the fields of a place print in, by the family the form names
     */
    private record Drawing(
            Map<String, Replacement> chosen,
            Map<String, FontChoice> choices,
            List<FontFamily> drawn,
            Map<String, String> fieldFamilies) {}

    /**
     * A family and size a missing family is drawn in.
     *
     * @param family the family
     * @param size   the size in percent of the form's
     * @param bold   the family its bold text is drawn in where the family is a light cut without a bold face,
     *               otherwise null
     */
    private record Replacement(
            FontFamily family, int size, @Nullable FontFamily bold) {

        /** How the form is rewritten for it. */
        FontChoice choice() {
            return new FontChoice(family.name(), size, bold == null ? null : bold.name());
        }

        /** The families LibreOffice needs files of to draw it. */
        List<FontFamily> families() {
            return bold == null ? List.of(family) : List.of(family, bold);
        }
    }

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
            suggestionFor(family, reachable).ifPresent(suggestion -> suggested.put(family, suggestion));
        }
        return new MissingFonts(
                missing,
                suggested,
                fonts.defaultFont().printedFamily(),
                embeddedAmong(owner, upload.data(), missing),
                missing.stream().filter(FontStandIns::light).toList(),
                BuiltInFonts.withLightCut().stream()
                        .flatMap(family -> reachable.find(family).stream())
                        .map(FontFamily::name)
                        .toList());
    }

    private static Optional<Suggestion> suggestionFor(String family, ReachableFonts reachable) {
        return FontStandIns.of(family).flatMap(standIn -> reachable
                .find(standIn.family())
                .map(found -> new Suggestion(found.name(), standIn.sameMeasure())));
    }

    private List<String> embeddedAmong(Owner owner, byte[] docx, List<String> missing) {
        var embedded = embeddedIn(owner, docx);
        return missing.stream()
                .filter(family -> embedded.stream().anyMatch(family::equalsIgnoreCase))
                .toList();
    }

    /**
     * Makes a PDF template from a Word form.
     *
     * <p>Every family the form names that the owner cannot draw needs a replacement: one of the families the
     * owner reaches, or none for the default font, and a size from {@link FontChoice#MIN_SIZE} to
     * {@link FontChoice#MAX_SIZE} percent. A missing family without one is refused by name, so no form is
     * ever drawn in a font nobody chose.
     *
     * @param owner    the station or the association that keeps the template
     * @param file     the upload, or null where the request carried none
     * @param asked    the replacement of each missing family, by the family the form names
     * @param authorId the account that makes it
     * @return the template and what was recognised
     */
    public WordFormImport importForm(
            Owner owner, @Nullable UploadedFile file, Map<String, ChosenFont> asked, int authorId) {
        var upload = wordForm(owner, file);
        var drawing = drawing(owner, upload.data(), asked);
        var form = converted(owner, upload.data(), drawing);
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
                drawing.chosen().entrySet().stream()
                        .map(entry -> new FontReplacement(
                                entry.getKey(),
                                entry.getValue().family().name(),
                                entry.getValue().size()))
                        .toList());
    }

    private WordForms.Converted converted(Owner owner, byte[] docx, Drawing drawing) {
        try {
            return WordForms.convert(new WordForms.Form(
                    docx,
                    drawing.choices(),
                    fontDirectories(drawing.drawn()),
                    drawing.fieldFamilies(),
                    GapWords.fieldsOf(catalogue.answerable(owner))));
        } catch (IOException e) {
            log.warn("A Word form could not be converted for {}", owner, e);
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FAILED.raise();
        }
    }

    /**
     * Draws a Word form to compare its fonts, every place filled with the same example value on both sides.
     * The original is filled in as by hand in Word, in the form's own fonts; the replacement is the template
     * as it would be made, its fields filled the way a generated document is, in their own font and size.
     * Nothing is kept: neither the form, nor the drawing, nor a font file handed in.
     *
     * @param owner         the station or the association that would keep the template
     * @param file          the upload, or null where the request carried none
     * @param asked         the replacement of each missing family, as for the import; only read for
     *                      {@link PreviewVersion#REPLACED}
     * @param version       which drawing to make
     * @param originalFonts font files of missing families, each for the family the form names, only read for
     *                      {@link PreviewVersion#ORIGINAL}
     * @return the PDF
     */
    public byte[] preview(
            Owner owner,
            @Nullable UploadedFile file,
            Map<String, ChosenFont> asked,
            PreviewVersion version,
            List<OriginalFont> originalFonts) {
        var upload = wordForm(owner, file);
        return switch (version) {
            case REPLACED -> replaced(owner, upload.data(), asked);
            case ORIGINAL -> original(owner, upload.data(), originalFonts);
        };
    }

    /** The template as it would be made, its fields filled with their example values. */
    private byte[] replaced(Owner owner, byte[] docx, Map<String, ChosenFont> asked) {
        var converted = converted(owner, docx, drawing(owner, docx, asked));
        var content = new PdfContent(null, new PdfLayout(converted.fields(), List.of()));
        var samples = converted.samples();
        UnaryOperator<String> fill = text -> {
            String filled = text;
            for (var sample : samples.entrySet())
                filled = filled.replace("{{" + sample.getKey() + "}}", sample.getValue());
            return filled;
        };
        return renderer.render(
                        new PdfTemplateRenderer.Original(owner, content, converted.pdf(), renderer.fieldFontsOf(owner)),
                        0,
                        fill,
                        SignerCaptions.roles(DocumentLanguage.DE))
                .pdf();
    }

    /**
     * The form in its own fonts. A missing family with a file handed in is renamed to the family the file
     * carries, so LibreOffice finds the file whatever it calls itself, such as Frutiger LT Std for Frutiger 45
     * Light. A missing family the form embeds is left to LibreOffice. Any other missing family is drawn in the
     * family offered first in its place, so the original never shows whatever LibreOffice falls back to, which
     * for a family Word describes as nothing is a serif font.
     */
    private byte[] original(Owner owner, byte[] docx, List<OriginalFont> originalFonts) {
        if (originalFonts.size() > MAX_PREVIEW_FONTS) {
            throw DocumentRefusal.DOCUMENT_FORM_PREVIEW_TOO_MANY_FONTS.raise();
        }
        var files = new LinkedHashMap<String, byte[]>();
        originalFonts.forEach(font -> files.put(font.family(), DocumentFontService.readUpload(font.file())));
        var reachable = fonts.reachable(owner);
        var named = namedIn(owner, docx);
        var missing = missing(named, reachable);
        var embedded = embeddedAmong(owner, docx, missing);
        var choices = new LinkedHashMap<String, FontChoice>();
        var drawn = new ArrayList<FontFamily>();
        for (String family : named) {
            byte[] file = files.get(family);
            if (file != null) {
                choices.put(family, new FontChoice(FontFiles.inspect(file).internalFamily(), FontChoice.SAME_SIZE));
            } else if (!missing.contains(family)) {
                reachable.find(family).ifPresent(drawn::add);
            } else if (!embedded.contains(family)) {
                var lookalike = lookalike(family, reachable);
                drawn.addAll(lookalike.families());
                choices.put(family, lookalike.choice());
            }
        }
        var directories = new ArrayList<>(fontDirectories(drawn));
        Path handedIn = null;
        try {
            if (!files.isEmpty()) {
                handedIn = Files.createTempDirectory("form-preview-fonts-");
                int index = 0;
                for (byte[] file : files.values()) Files.write(handedIn.resolve("font-" + index++), file);
                directories.add(handedIn);
            }
            return WordForms.filledByHand(new WordForms.Form(
                    docx, choices, directories, Map.of(), GapWords.fieldsOf(catalogue.answerable(owner))));
        } catch (IOException e) {
            log.warn("The original of a Word form could not be drawn for {}", owner, e);
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FAILED.raise();
        } finally {
            if (handedIn != null) remove(handedIn);
        }
    }

    /** How the form is drawn with the replacements asked for, refused where one is missing or out of range. */
    private Drawing drawing(Owner owner, byte[] docx, Map<String, ChosenFont> asked) {
        var reachable = fonts.reachable(owner);
        var named = namedIn(owner, docx);
        var chosen = replacementsFor(missing(named, reachable), asked, reachable);
        var drawn = new ArrayList<FontFamily>();
        var choices = new LinkedHashMap<String, FontChoice>();
        var fieldFamilies = new LinkedHashMap<String, String>();
        chosen.forEach((family, replacement) -> {
            drawn.addAll(replacement.families());
            choices.put(family, replacement.choice());
            printing(reachable, replacement.family().name()).ifPresent(name -> fieldFamilies.put(family, name));
            if (replacement.bold() != null) {
                String bold = replacement.bold().name();
                printing(reachable, bold).ifPresent(name -> fieldFamilies.put(bold, name));
            }
        });
        for (String family : named) {
            if (chosen.containsKey(family)) continue;
            reachable.find(family).ifPresent(drawn::add);
            printing(reachable, family).ifPresent(name -> fieldFamilies.put(family, name));
        }
        return new Drawing(chosen, choices, drawn, fieldFamilies);
    }

    /** The name a field prints a family under, where the owner reaches it and it can print on a PDF. */
    private static Optional<String> printing(ReachableFonts reachable, String family) {
        return reachable.find(family).filter(FontFamily::printsOnPdf).map(FontFamily::name);
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

    /** The families a form carries files for, refused as unreadable where they cannot be read. */
    private List<String> embeddedIn(Owner owner, byte[] docx) {
        try {
            return WordForms.embeddedFonts(docx);
        } catch (IOException e) {
            log.warn("The embedded fonts of a Word form could not be read for {}", owner, e);
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
     * The family and size each missing one is drawn in, checked: every missing family has one, each is a
     * family the owner reaches, and each size is in range; none chosen stands for the default font, no size
     * for the form's own.
     */
    private Map<String, Replacement> replacementsFor(
            List<String> missing, Map<String, ChosenFont> asked, ReachableFonts reachable) {
        var chosen = new LinkedHashMap<String, Replacement>();
        for (String family : missing) {
            var choice = asked.get(family);
            if (choice == null) {
                throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FONT_UNCHOSEN.raise(RefusalDetail.text(family));
            }
            int size = Objects.requireNonNullElse(choice.size(), FontChoice.SAME_SIZE);
            if (size < FontChoice.MIN_SIZE || size > FontChoice.MAX_SIZE) {
                throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FONT_SIZE_INVALID.raise(RefusalDetail.text(family));
            }
            String replacement = Objects.requireNonNullElse(choice.family(), "").strip();
            var found = replacement.isEmpty()
                    ? defaultFamily(reachable)
                    : reachable
                            .find(replacement)
                            .orElseThrow(() -> DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN.raise(
                                    RefusalDetail.text(replacement)));
            chosen.put(family, weighted(family, found, choice.weight(), size));
        }
        return chosen;
    }

    /**
     * A family in the weight asked for: its light cut for {@link FontWeight#LIGHT}, refused where it has none;
     * the family itself for {@link FontWeight#REGULAR}; and where none is asked, the light cut where the name
     * of the missing family says light and the family ships one. Bold text of a light cut is drawn in the
     * family itself, whose bold face is a real one.
     */
    private static Replacement weighted(String missing, FontFamily family, @Nullable FontWeight weight, int size) {
        boolean light = weight == null ? FontStandIns.light(missing) : weight == FontWeight.LIGHT;
        if (!light) return new Replacement(family, size, null);
        var cut = BuiltInFonts.lightCut(family.name());
        if (cut.isPresent()) return new Replacement(cut.get(), size, family);
        if (weight == FontWeight.LIGHT) {
            throw DocumentRefusal.DOCUMENT_FORM_IMPORT_FONT_WEIGHT_UNAVAILABLE.raise(RefusalDetail.text(family.name()));
        }
        return new Replacement(family, size, null);
    }

    /** The family a missing one is drawn in where nobody chose: the one offered first, else the default font. */
    private Replacement lookalike(String missing, ReachableFonts reachable) {
        var offered = suggestionFor(missing, reachable)
                .flatMap(suggestion -> reachable.find(suggestion.family()))
                .orElseGet(() -> defaultFamily(reachable));
        return weighted(missing, offered, null, FontChoice.SAME_SIZE);
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

    private static void remove(Path directory) {
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.warn("The font files of a form preview could not be removed from {}", directory, e);
        }
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
