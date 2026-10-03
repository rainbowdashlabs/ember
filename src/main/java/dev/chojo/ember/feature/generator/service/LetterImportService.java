/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.util.PandocConverter;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the body of a letter template from a Word (.docx) or OpenDocument (.odt) text document.
 *
 * <p>Which of the two a file is, is read from the file itself through {@link PandocConverter#formatOf},
 * the one detection every document import shares; anything else is refused. Pandoc turns the body into
 * markdown; the header and the footer are not part of it and are set up in the letterhead builder once.
 * The pictures of the body go into the station's media library, so the body points at them the way a
 * picture inserted in the editor does.
 *
 * <p>Gaps written in brackets, the way a form marks them ({@code [Vorname Nachname]},
 * {@code [Geburtsdatum]}, {@code [er/sie]}), become placeholders where their words are recognised:
 * the common German names of member data, the pronouns, and the names of the station's own profile
 * questions. Anything else stays as text and is listed, so it can be replaced by hand.
 */
@Singleton
public class LetterImportService {
    private static final Logger log = LoggerFactory.getLogger(LetterImportService.class);

    /** The formats a letter body is read from. */
    private static final Set<String> TAKEN = Set.of("docx", "odt");

    /** A gap in brackets, as Pandoc writes it escaped, or bare where it is not followed by a link. */
    private static final Pattern GAP =
            Pattern.compile("\\\\\\[([^\\[\\]\\\\\\n]{1,60})\\\\]|\\[([^\\[\\]\\n]{1,60})](?![(\\[])");

    /** The words of a gap the built-in placeholders stand for, written in lower case. */
    private static final Map<String, BuiltInPlaceholder> WORDS = words();

    private final MediaLibraryService mediaLibrary;
    private final PlaceholderCatalogue catalogue;
    private final DocumentIntake intake;

    @Inject
    public LetterImportService(
            MediaLibraryService mediaLibrary, PlaceholderCatalogue catalogue, DocumentIntake intake) {
        this.mediaLibrary = mediaLibrary;
        this.catalogue = catalogue;
        this.intake = intake;
    }

    /**
     * A document read in as the body of a template.
     *
     * @param bodyMarkdown the body, with placeholders where gaps were recognised
     * @param recognised   the gaps that became placeholders, as they were written
     * @param unrecognised the gaps that stayed text, as they were written
     */
    public record LetterImport(String bodyMarkdown, List<String> recognised, List<String> unrecognised) {}

    private static Map<String, BuiltInPlaceholder> words() {
        var words = new LinkedHashMap<String, BuiltInPlaceholder>();
        words.put("vorname nachname", BuiltInPlaceholder.MEMBER_FULL_NAME);
        words.put("vor- und nachname", BuiltInPlaceholder.MEMBER_FULL_NAME);
        words.put("name", BuiltInPlaceholder.MEMBER_FULL_NAME);
        words.put("vorname", BuiltInPlaceholder.MEMBER_FIRST_NAME);
        words.put("nachname", BuiltInPlaceholder.MEMBER_LAST_NAME);
        words.put("geburtsdatum", BuiltInPlaceholder.MEMBER_BIRTH_DATE);
        words.put("alter", BuiltInPlaceholder.MEMBER_AGE);
        words.put("eintrittsdatum", BuiltInPlaceholder.MEMBER_JOIN_DATE);
        words.put("monat/jahr", BuiltInPlaceholder.MEMBER_JOIN_MONTH);
        words.put("datum", BuiltInPlaceholder.TODAY);
        words.put("er/sie", BuiltInPlaceholder.PRONOUN_SUBJECT);
        words.put("ihn/sie", BuiltInPlaceholder.PRONOUN_OBJECT);
        words.put("ihm/ihr", BuiltInPlaceholder.PRONOUN_DATIVE);
        words.put("sein/ihr", BuiltInPlaceholder.PRONOUN_POSSESSIVE);
        words.put("seine/ihre", BuiltInPlaceholder.PRONOUN_POSSESSIVE);
        return Map.copyOf(words);
    }

    /**
     * Reads an uploaded document into the body of a template.
     *
     * @param session the editor of templates, whose station keeps the pictures
     * @param file    the upload, or null where the request carried none
     * @return the body and which gaps were recognised
     */
    public LetterImport read(StationSession session, @Nullable UploadedFile file) {
        var upload = intake.read(session.stationId(), file, DocumentDoor.STATION.intake());
        return read(session, upload.fileName(), upload.declaredType(), upload.data());
    }

    /**
     * Reads a document into the body of a template.
     *
     * @param session  the editor of templates, whose station keeps the pictures
     * @param fileName the name the file arrived under
     * @param mimeType the type it was declared as
     * @param data     the bytes of the file
     * @return the body and which gaps were recognised
     */
    public LetterImport read(
            StationSession session, @Nullable String fileName, @Nullable String mimeType, byte[] data) {
        String format = PandocConverter.formatOf(data, fileName, mimeType)
                .filter(TAKEN::contains)
                .orElseThrow(DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN::raise);
        PandocConverter.WithMedia converted;
        try {
            converted = PandocConverter.toMarkdownWithMedia(data, format);
        } catch (IOException e) {
            log.warn("A {} document could not be read for station {}", format, session.stationId(), e);
            throw DocumentRefusal.DOCUMENT_IMPORT_UNREADABLE.raise();
        }
        return placeholders(session.stationId(), placePictures(session, converted));
    }

    /**
     * Stores every picture of the body in the media library and points the body at it. A picture the
     * library does not take is left out, and the body keeps its alternative text.
     */
    private String placePictures(StationSession session, PandocConverter.WithMedia converted) {
        String markdown = converted.markdown();
        for (var picture : converted.media().entrySet()) {
            String url = store(session, picture.getKey(), picture.getValue()).orElse("");
            markdown = markdown.replace(picture.getKey(), url);
        }
        return markdown;
    }

    private Optional<String> store(StationSession session, String path, byte[] data) {
        var format = ImageFormat.sniff(data);
        if (format.isEmpty()) return Optional.empty();
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            var file = mediaLibrary.upload(
                    session.stationId(),
                    null,
                    session.member().id(),
                    name,
                    format.get().mimeType(),
                    data);
            return Optional.of("/api/v1/public/media/%s/%s".formatted(session.stationUid(), file.contentHash()));
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "A picture of an imported document was not taken by the media library of station {}",
                    session.stationId(),
                    e);
            return Optional.empty();
        }
    }

    private LetterImport placeholders(int stationId, String markdown) {
        var fields = new LinkedHashMap<String, String>();
        catalogue
                .answerable(stationId)
                .forEach(field -> fields.putIfAbsent(
                        field.name().toLowerCase(Locale.ROOT), PlaceholderCatalogue.PROFILE + field.id()));
        var recognised = new LinkedHashSet<String>();
        var unrecognised = new LinkedHashSet<String>();
        Matcher matcher = GAP.matcher(markdown);
        var out = new StringBuilder();
        while (matcher.find()) {
            String gap = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String key = keyOf(gap, fields);
            if (key == null) {
                unrecognised.add(gap.strip());
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                recognised.add(gap.strip());
                matcher.appendReplacement(out, Matcher.quoteReplacement("{{" + key + "}}"));
            }
        }
        matcher.appendTail(out);
        return new LetterImport(out.toString(), new ArrayList<>(recognised), new ArrayList<>(unrecognised));
    }

    private static @Nullable String keyOf(String gap, Map<String, String> fields) {
        String words = gap.strip().replaceAll("\\s+", " ");
        String lower = words.toLowerCase(Locale.ROOT);
        if (lower.equals("er/sie") && Character.isUpperCase(words.charAt(0))) {
            return BuiltInPlaceholder.PRONOUN_SUBJECT_START.key();
        }
        var builtIn = WORDS.get(lower);
        if (builtIn != null) return builtIn.key();
        return fields.get(lower);
    }
}
