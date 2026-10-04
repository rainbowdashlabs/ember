/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.DocumentIssuer;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.ResolvedValues;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a template into the PDF of one member, without filing it.
 *
 * <p>It works in two steps so a caller can refuse before anything is drawn: {@link #prepare} reads the
 * values of every placeholder the template names and reports the ones that are missing, and
 * {@link #render} draws the document from them. Filing the result is {@link DocumentGenerationService}'s.
 *
 * <p>A letter is drawn by {@link LetterRenderer}, where a placeholder without a value becomes a line to
 * fill in by hand. A PDF template fills its uploaded PDF through {@link PdfTemplateRenderer}, where the
 * form already has its own lines and a placeholder without a value stays empty.
 *
 * <p>A legal template is checked again here, not only when it is saved: a document that names a member
 * by the name they are called by is refused, whatever state the template got into.
 *
 * <p>A template carries its owner, a station or an association, which decides where its files and
 * pictures are read from, which fonts it reaches and what its placeholders are called. A document is
 * always drawn at the station of the member it is about: its values, the station logo and the day it
 * is dated come from there, whoever keeps the template.
 */
@Singleton
public class DocumentGeneratorService {
    /** Characters a file name may not carry on any system the file is downloaded to. */
    private static final Pattern UNSAFE_IN_FILE_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** The longest file name, before its extension. */
    private static final int MAX_FILE_NAME = 150;

    private final DocumentTemplateService templates;
    private final PlaceholderResolver resolver;
    private final PlaceholderCatalogue catalogue;
    private final LetterRenderer letters;
    private final PdfTemplateRenderer pdfs;
    private final StationRepository stations;
    private final RestrictionService restrictions;
    private final Clock clock;

    @Inject
    public DocumentGeneratorService(
            DocumentTemplateService templates,
            PlaceholderResolver resolver,
            PlaceholderCatalogue catalogue,
            LetterRenderer letters,
            PdfTemplateRenderer pdfs,
            StationRepository stations,
            RestrictionService restrictions) {
        this(templates, resolver, catalogue, letters, pdfs, stations, restrictions, Clock.systemUTC());
    }

    /**
     * @param clock what the day a document is dated is read from, which a test moves
     */
    public DocumentGeneratorService(
            DocumentTemplateService templates,
            PlaceholderResolver resolver,
            PlaceholderCatalogue catalogue,
            LetterRenderer letters,
            PdfTemplateRenderer pdfs,
            StationRepository stations,
            RestrictionService restrictions,
            Clock clock) {
        this.templates = templates;
        this.resolver = resolver;
        this.catalogue = catalogue;
        this.letters = letters;
        this.pdfs = pdfs;
        this.stations = stations;
        this.restrictions = restrictions;
        this.clock = clock;
    }

    /**
     * What a document is drawn from: the state of the template, the station it is drawn at, what of it
     * the member sees, and the values of one member.
     *
     * @param source    the template
     * @param stationId the station of the member, where the document is drawn and filed
     * @param view      which blocks of a letter the member sees and how many guardians sign for them
     * @param resolved  the values and what is missing
     */
    public record Prepared(Source source, int stationId, MemberView view, ResolvedValues resolved, IssuerUse issuer) {}

    /**
     * The issuer as one member's document names them.
     *
     * @param issuer who issues it, as the template names them or a manager picked them
     * @param named  whether the document names the issuer at all, by one of their values or by their
     *               signature field, which is what makes a missing issuer missing data
     * @param signs  whether the document carries the signature field named {@code issuer}
     * @param name   the issuer's official name as the document prints it, or null where nobody could be
     *               named: none was chosen, or the one chosen is no current member of the station
     */
    public record IssuerUse(
            DocumentIssuer issuer,
            boolean named,
            boolean signs,
            @Nullable String name) {

        /** @return the member the document is issued by, as the generation log keeps it, or null */
        public @Nullable Integer memberOfRecord() {
            return named && name != null ? issuer.memberId() : null;
        }

        /** @return the issuer as a preview shows it, or null where the document names none */
        public @Nullable PreviewIssuer preview() {
            return named ? new PreviewIssuer(memberOfRecord(), name, issuer.function(), issuer.fixed()) : null;
        }
    }

    /**
     * The issuer a drawn document names, for the screen to show beside it.
     *
     * @param memberId the member, or null where nobody could be named
     * @param name     their official name, or null where nobody could be named
     * @param function what they do at the station, or null where nothing is said
     * @param fixed    whether it is the issuer the template names, rather than one picked for this document
     */
    public record PreviewIssuer(
            @Nullable Integer memberId,
            @Nullable String name,
            @Nullable String function,
            boolean fixed) {}

    /**
     * A template as the generator reads it, saved or still a draft in the editor.
     *
     * @param owner           the station or the association that keeps it
     * @param name            what it is called, which a document without a title is called after
     * @param titlePattern    the title, with placeholders
     * @param fileNamePattern the file name, with placeholders
     * @param content         the letter, or the PDF with what is laid over it
     * @param language        the language its documents are written in
     * @param legal           whether it makes a legal document
     * @param cacheKey        the template and version a letter's texts are kept under, or null for a draft
     */
    public record Source(
            Owner owner,
            String name,
            String titlePattern,
            String fileNamePattern,
            TemplateContent content,
            DocumentLanguage language,
            boolean legal,
            LetterRenderer.@Nullable BodyKey cacheKey) {

        /**
         * The keys that stand for values in what a member sees of the template: the title, the file
         * name, and of a letter only the blocks meant for them.
         *
         * @param view what of a letter the member sees
         * @return the keys, in the order they first appear
         */
        public Set<String> valueKeys(MemberView view) {
            var texts =
                    switch (content) {
                        case LetterContent letter -> LetterLayout.visibleTexts(letter, view).stream();
                        case PdfContent pdf -> pdf.texts();
                    };
            return PlaceholderCatalogue.keysOf(titlePattern, fileNamePattern, texts);
        }

        /**
         * @param view what of a letter the member sees
         * @return whether the member's document carries the signature field of the issuer
         */
        public boolean asksIssuerToSign(MemberView view) {
            return switch (content) {
                case LetterContent letter ->
                    LetterLayout.signatureFields(letter, view).stream()
                            .anyMatch(SignatureRole.ISSUER.fieldNames(view.guardians())::contains);
                case PdfContent pdf ->
                    pdf.layout().fields().stream().anyMatch(field -> field.role() == SignatureRole.ISSUER);
            };
        }

        /** @return the uploaded PDF the document is filled from, or null for a letter */
        public @Nullable Integer pdfOriginalId() {
            if (!(content instanceof PdfContent pdf)) return null;
            var original = pdf.original();
            return original == null ? null : original.id();
        }
    }

    /**
     * A drawn document.
     *
     * @param pdf         the file
     * @param title       the title it is filed under
     * @param fileName    the file name it is filed under, ending in {@code .pdf}
     * @param resolved    the values it was drawn from
     * @param unprintable the characters of the values no font could print, which the file leaves out
     */
    public record Rendered(
            byte[] pdf, String title, String fileName, ResolvedValues resolved, List<String> unprintable) {}

    /**
     * A document drawn for a look before it is generated.
     *
     * @param pdfBase64   the PDF, Base64 encoded
     * @param missing     the placeholders without a value
     * @param unprintable the characters no font could print, which the document leaves out
     * @param issuer      the issuer the document names, or null where it names none or is drawn without a
     *                    member
     */
    public record PreviewResponse(
            String pdfBase64,
            List<MissingValue> missing,
            List<String> unprintable,
            @Nullable PreviewIssuer issuer) {}

    /**
     * @param template a saved template
     * @return the template as the generator reads it
     */
    public Source sourceOf(DocumentTemplate template) {
        return new Source(
                template.owner(),
                template.name(),
                template.titlePattern(),
                template.fileNamePattern(),
                templates.contentOf(template),
                template.language(),
                template.legal(),
                new LetterRenderer.BodyKey(template.id(), template.version()));
    }

    /**
     * @param owner the station or the association the editor works for
     * @param draft a template still in the editor
     * @return the template as the generator reads it
     */
    public static Source sourceOf(Owner owner, DocumentTemplateDraft draft) {
        return new Source(
                owner,
                draft.name(),
                draft.titlePattern(),
                draft.fileNamePattern(),
                draft.content(),
                draft.language(),
                draft.legal(),
                null);
    }

    /**
     * Reads the values of one member for a template.
     *
     * <p>A letter whose signature lines would ask one person of this member's to sign twice is refused
     * here: two lines for one signer may stand in a template as alternatives, but never meet in one
     * document.
     *
     * <p>A document that names its issuer, by a value or by the issuer's signature field, needs the
     * issuer's name: where nobody can be named, that name is missing like any other value.
     *
     * @param source   the template
     * @param memberId the member the document is about
     * @param context  who generates it, for which appointment and who issues it
     * @return the values and what is missing
     */
    public Prepared prepare(Source source, int memberId, GenerationContext context) {
        int stationId = resolver.stationOf(memberId).orElseThrow(MemberRefusal.MEMBER_NOT_HERE::raise);
        var member = restrictions.memberOf(memberId).orElse(null);
        Predicate<RestrictionAudience> audience =
                member == null ? restriction -> false : restriction -> restriction.includes(member);
        var view = MemberView.of(audience, resolver.guardians(memberId));
        if (source.content() instanceof LetterContent letter) {
            LetterLayout.requireSignersOnce(letter, view, DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER);
        }
        var keys = new LinkedHashSet<>(source.valueKeys(view));
        if (source.legal()) PlaceholderCatalogue.requireOfficial(keys);
        boolean signs = source.asksIssuerToSign(view);
        boolean named = signs || BuiltInPlaceholder.namesIssuer(keys);
        if (named) keys.add(BuiltInPlaceholder.ISSUER_FULL_NAME.key());
        var resolved = resolver.resolve(stationId, memberId, keys, source.language(), context);
        var issuer = new IssuerUse(
                context.issuer(), named, signs, resolved.values().get(BuiltInPlaceholder.ISSUER_FULL_NAME.key()));
        return new Prepared(source, stationId, view, resolved, issuer);
    }

    /**
     * Reads the values of a document from a saved template, as {@link #prepare(Source, int,
     * GenerationContext)} does.
     *
     * @param template the template
     * @param memberId the member the document is about
     * @param context  who generates it, for which appointment and who issues it
     * @return the values and what is missing
     */
    public Prepared prepare(DocumentTemplate template, int memberId, GenerationContext context) {
        return prepare(sourceOf(template), memberId, context);
    }

    /**
     * The placeholders without a value, in the words of the template's owner.
     *
     * @param prepared what a document is about to be drawn from
     * @return the missing values in template order
     */
    public List<MissingValue> missing(Prepared prepared) {
        var labels = catalogue.byKey(prepared.source().owner());
        return prepared.resolved().missing().stream()
                .map(key -> new MissingValue(key, PlaceholderCatalogue.labelOf(labels, key)))
                .toList();
    }

    /**
     * Refuses a document whose values are not all there, naming the missing ones in the words of the
     * template's owner.
     *
     * @param prepared what a document is about to be drawn from
     * @param refusal  what to refuse with
     */
    public void requireComplete(Prepared prepared, Refusal refusal) {
        var missing = missing(prepared);
        if (missing.isEmpty()) return;
        String labels = missing.stream().map(MissingValue::label).collect(Collectors.joining(", "));
        throw refusal.raise(RefusalDetail.text(labels));
    }

    /**
     * Draws the document of one member.
     *
     * @param prepared the template and the member's values
     * @return the document
     */
    public Rendered render(Prepared prepared) {
        var source = prepared.source();
        var values = prepared.resolved().values();
        String title = title(source, values);
        var labels = labels(source.owner(), prepared.resolved().missing());
        var drawn = draw(source, prepared.stationId(), prepared.view(), title, values, labels, false);
        return new Rendered(drawn.pdf(), title, fileName(source, values), prepared.resolved(), drawn.unprintable());
    }

    /**
     * Draws a template for a look in the editor, for a member or, without one, with the placeholders
     * shown by their labels. Without a member a station's template is drawn at that station, and an
     * association's at none: what only a station knows shows by its label.
     *
     * @param source   the template, saved or a draft
     * @param memberId the member to draw it for, or null for no member
     * @param context  who looks at it
     * @return the document and what is missing
     */
    public PreviewResponse preview(Source source, @Nullable Integer memberId, GenerationContext context) {
        if (memberId != null) {
            var prepared = prepare(source, memberId, context);
            var rendered = render(prepared);
            return new PreviewResponse(
                    encode(rendered.pdf()),
                    missing(prepared),
                    rendered.unprintable(),
                    prepared.issuer().preview());
        }
        var keys = PlaceholderCatalogue.keysOf(
                source.titlePattern(),
                source.fileNamePattern(),
                source.content().texts());
        var values = resolver.withoutMember(source.owner(), source.language(), keys);
        var labels = labels(source.owner(), List.copyOf(keys));
        Integer stationId = source.owner() instanceof Owner.Station station ? station.stationId() : null;
        var drawn = draw(source, stationId, MemberView.EVERYBODY, title(source, values), values, labels, true);
        return new PreviewResponse(encode(drawn.pdf()), List.of(), drawn.unprintable(), null);
    }

    private PdfStamper.Stamped draw(
            Source source,
            @Nullable Integer stationId,
            MemberView view,
            String title,
            Map<String, String> values,
            Map<String, String> labels,
            boolean showLabels) {
        return switch (source.content()) {
            case LetterContent letter ->
                new PdfStamper.Stamped(
                        letters.render(new LetterRenderer.LetterJob(
                                source.owner(),
                                stationId,
                                title,
                                letter,
                                source.cacheKey(),
                                view,
                                source.language(),
                                values,
                                labels,
                                showLabels,
                                today(stationId))),
                        List.of());
            case PdfContent pdf ->
                pdfs.render(
                        source.owner(),
                        pdf,
                        view.guardians(),
                        text -> PlaceholderTokens.replace(text, key -> {
                            String value = values.get(key);
                            if (value != null) return value;
                            return showLabels ? "[" + labels.getOrDefault(key, key) + "]" : "";
                        }));
        };
    }

    private static String encode(byte[] pdf) {
        return Base64.getEncoder().encodeToString(pdf);
    }

    private Map<String, String> labels(Owner owner, List<String> keys) {
        var known = catalogue.byKey(owner);
        var labels = new LinkedHashMap<String, String>();
        keys.forEach(key -> labels.put(key, PlaceholderCatalogue.labelOf(known, key)));
        return labels;
    }

    private static String title(Source source, Map<String, String> values) {
        String title = SPACES.matcher(PlaceholderTokens.fill(source.titlePattern(), values))
                .replaceAll(" ")
                .strip();
        return title.isEmpty() ? source.name() : title;
    }

    /**
     * The file name the document is filed under: the pattern filled in, stripped of what no file system
     * takes, and ending in {@code .pdf}.
     */
    static String fileName(Source source, Map<String, String> values) {
        String filled = PlaceholderTokens.fill(source.fileNamePattern(), values);
        String safe = SPACES.matcher(UNSAFE_IN_FILE_NAME.matcher(filled).replaceAll("_"))
                .replaceAll(" ")
                .strip();
        if (safe.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            safe = safe.substring(0, safe.length() - 4).strip();
        if (safe.isEmpty())
            safe = UNSAFE_IN_FILE_NAME.matcher(source.name()).replaceAll("_").strip();
        if (safe.length() > MAX_FILE_NAME)
            safe = safe.substring(0, MAX_FILE_NAME).strip();
        return safe + ".pdf";
    }

    private LocalDate today(@Nullable Integer stationId) {
        var station = stationId == null ? null : stations.findById(stationId).orElse(null);
        return LocalDate.now(clock.withZone(StationFormat.timezoneOf(station)));
    }
}
