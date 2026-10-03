/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.PossessiveEnding;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.PronounRole;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the body of a letter template from a Word (.docx) or OpenDocument (.odt) text document.
 *
 * <p>Which of the two a file is, is read from the file itself through {@link PandocConverter#formatOf},
 * the one detection every document import shares; anything else is refused. Pandoc turns the body into
 * markdown; the header and the footer are not part of it and are set up in the editor once. The body
 * arrives as one text block, split where a picture stands on a line of its own: the pictures go into the
 * media library of the template's owner (an association's into that of its home station) and become
 * picture blocks, and a picture inside a paragraph stays in its text.
 *
 * <p>Gaps written in brackets, the way a form marks them ({@code [Vorname Nachname]},
 * {@code [Geburtsdatum]}, {@code [er/sie]}), become placeholders where their words are recognised:
 * the common German names of member data, the pronouns, and the names of the profile questions the
 * owner's templates can name. Anything else stays as text and is listed, so it can be replaced by hand.
 */
@Singleton
public class LetterImportService {
    private static final Logger log = LoggerFactory.getLogger(LetterImportService.class);

    /** The formats a letter body is read from. */
    private static final Set<String> TAKEN = Set.of("docx", "odt");

    /** A gap in brackets, as Pandoc writes it escaped, or bare where it is not followed by a link. */
    private static final Pattern GAP =
            Pattern.compile("\\\\\\[([^\\[\\]\\\\\\n]{1,60})\\\\]|\\[([^\\[\\]\\n]{1,60})](?![(\\[])");

    /** A picture of the media library standing on a line of its own; group 1 is its content hash. */
    private static final Pattern PICTURE_LINE = Pattern.compile(
            "^[ \\t]*!\\[[^\\]\\n]*]\\(/api/v1/public/media/[^/\\s)]+/([^/\\s)]+)\\)(?:\\{[^}\\n]*})?[ \\t]*$",
            Pattern.MULTILINE);

    /** The placeholder keys the words of a gap stand for, the words written in lower case. */
    private static final Map<String, String> WORDS = words();

    private final MediaLibraryService mediaLibrary;
    private final PlaceholderCatalogue catalogue;
    private final DocumentIntake intake;
    private final OwnerStores stores;
    private final StationRepository stations;

    @Inject
    public LetterImportService(
            MediaLibraryService mediaLibrary,
            PlaceholderCatalogue catalogue,
            DocumentIntake intake,
            OwnerStores stores,
            StationRepository stations) {
        this.mediaLibrary = mediaLibrary;
        this.catalogue = catalogue;
        this.intake = intake;
        this.stores = stores;
        this.stations = stations;
    }

    /**
     * Who imports a document into a template.
     *
     * @param owner    the station or the association that keeps the template, whose media library takes
     *                 the pictures and whose profile questions the gaps are matched against
     * @param memberId the member who brings the pictures in, or null where nobody of a station does
     */
    public record Importer(Owner owner, @Nullable Integer memberId) {

        /**
         * @param session an editor of a station's templates
         * @return the station, with the editor as the one who brings pictures in
         */
        public static Importer of(StationSession session) {
            return new Importer(session.owner(), session.member().id());
        }
    }

    /**
     * Where the pictures of an import go.
     *
     * @param stationId  the station whose media library takes them
     * @param stationUid that station's uid, which their addresses carry
     * @param memberId   the member who brings them in, or null
     */
    private record Library(
            int stationId, UUID stationUid, @Nullable Integer memberId) {}

    /**
     * A document read in as the body of a template.
     *
     * @param rows         the body as rows of one block each, with placeholders where gaps were recognised
     * @param recognised   the gaps that became placeholders, as they were written
     * @param unrecognised the gaps that stayed text, as they were written
     */
    public record LetterImport(List<ContentRow> rows, List<String> recognised, List<String> unrecognised) {}

    private static Map<String, String> words() {
        var words = new LinkedHashMap<String, String>();
        words.put("vorname nachname", BuiltInPlaceholder.MEMBER_FULL_NAME.key());
        words.put("vor- und nachname", BuiltInPlaceholder.MEMBER_FULL_NAME.key());
        words.put("name", BuiltInPlaceholder.MEMBER_FULL_NAME.key());
        words.put("vorname", BuiltInPlaceholder.MEMBER_FIRST_NAME.key());
        words.put("nachname", BuiltInPlaceholder.MEMBER_LAST_NAME.key());
        words.put("geburtsdatum", BuiltInPlaceholder.MEMBER_BIRTH_DATE.key());
        words.put("alter", BuiltInPlaceholder.MEMBER_AGE.key());
        words.put("eintrittsdatum", BuiltInPlaceholder.MEMBER_JOIN_DATE.key());
        words.put("monat/jahr", BuiltInPlaceholder.MEMBER_JOIN_MONTH.key());
        words.put("datum", BuiltInPlaceholder.TODAY.key());
        words.put("er/sie", pronoun(PronounRole.SUBJECT, PossessiveEnding.NONE));
        words.put("ihn/sie", pronoun(PronounRole.OBJECT, PossessiveEnding.NONE));
        words.put("ihm/ihr", pronoun(PronounRole.DATIVE, PossessiveEnding.NONE));
        words.put("sein/ihr", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.NONE));
        words.put("seine/ihre", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.E));
        words.put("seinen/ihren", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.EN));
        words.put("seinem/ihrem", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.EM));
        words.put("seiner/ihrer", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.ER));
        words.put("seines/ihres", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.ES));
        return Map.copyOf(words);
    }

    private static String pronoun(PronounRole role, PossessiveEnding ending) {
        return new PronounKey(role, false, ending).key();
    }

    /**
     * Reads an uploaded document into the body of a template.
     *
     * @param importer who imports it, and for whose template
     * @param file     the upload, or null where the request carried none
     * @return the body and which gaps were recognised
     */
    public LetterImport read(Importer importer, @Nullable UploadedFile file) {
        var upload = intake.read(stores.libraryOf(importer.owner()), file, DocumentDoor.STATION.intake());
        return read(importer, upload.fileName(), upload.declaredType(), upload.data());
    }

    /**
     * Reads a document into the body of a template.
     *
     * @param importer who imports it, and for whose template
     * @param fileName the name the file arrived under
     * @param mimeType the type it was declared as
     * @param data     the bytes of the file
     * @return the body and which gaps were recognised
     */
    public LetterImport read(Importer importer, @Nullable String fileName, @Nullable String mimeType, byte[] data) {
        String format = PandocConverter.formatOf(data, fileName, mimeType)
                .filter(TAKEN::contains)
                .orElseThrow(DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN::raise);
        PandocConverter.WithMedia converted;
        try {
            converted = PandocConverter.toMarkdownWithMedia(data, format);
        } catch (IOException e) {
            log.warn("A {} document could not be read for {}", format, importer.owner(), e);
            throw DocumentRefusal.DOCUMENT_IMPORT_UNREADABLE.raise();
        }
        int library = stores.libraryOf(importer.owner());
        var pictures = new Library(library, stations.requireUid(library), importer.memberId());
        return placeholders(importer.owner(), placePictures(pictures, converted));
    }

    /**
     * Stores every picture of the body in the media library and points the body at it. A picture the
     * library does not take is left out, and the body keeps its alternative text.
     */
    private String placePictures(Library library, PandocConverter.WithMedia converted) {
        String markdown = converted.markdown();
        for (var picture : converted.media().entrySet()) {
            String url = store(library, picture.getKey(), picture.getValue()).orElse("");
            markdown = markdown.replace(picture.getKey(), url);
        }
        return markdown;
    }

    private Optional<String> store(Library library, String path, byte[] data) {
        var format = ImageFormat.sniff(data);
        if (format.isEmpty()) return Optional.empty();
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            var file = mediaLibrary.upload(
                    library.stationId(),
                    null,
                    library.memberId(),
                    name,
                    format.get().mimeType(),
                    data);
            return Optional.of("/api/v1/public/media/%s/%s".formatted(library.stationUid(), file.contentHash()));
        } catch (IOException | RuntimeException e) {
            log.warn(
                    "A picture of an imported document was not taken by the media library of station {}",
                    library.stationId(),
                    e);
            return Optional.empty();
        }
    }

    private LetterImport placeholders(Owner owner, String markdown) {
        var fields = new LinkedHashMap<String, String>();
        catalogue
                .answerable(owner)
                .forEach(field -> fields.putIfAbsent(field.label().toLowerCase(Locale.ROOT), field.key()));
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
        return new LetterImport(blocks(out.toString()), new ArrayList<>(recognised), new ArrayList<>(unrecognised));
    }

    /**
     * The body as rows of one block each: the text in between as text blocks, and a picture standing on a
     * line of its own as a picture block, which the letter can then size and place by itself.
     */
    static List<ContentRow> blocks(String markdown) {
        var rows = new ArrayList<ContentRow>();
        Matcher matcher = PICTURE_LINE.matcher(markdown);
        int from = 0;
        while (matcher.find()) {
            addText(rows, markdown.substring(from, matcher.start()));
            rows.add(row(rows.size(), CellContentType.IMAGE, matcher.group(1)));
            from = matcher.end();
        }
        addText(rows, markdown.substring(from));
        return List.copyOf(rows);
    }

    private static void addText(List<ContentRow> rows, String text) {
        String stripped = text.strip();
        if (!stripped.isEmpty()) rows.add(row(rows.size(), CellContentType.MARKDOWN, stripped));
    }

    private static ContentRow row(int sortOrder, CellContentType type, String content) {
        return new ContentRow(
                0, 0, sortOrder, List.of(new ContentCell(0, 0, 0, 100.0, type, content, type.emptyConfig())));
    }

    private static @Nullable String keyOf(String gap, Map<String, String> fields) {
        String words = gap.strip().replaceAll("\\s+", " ");
        String lower = words.toLowerCase(Locale.ROOT);
        var key = WORDS.get(lower);
        if (key == null) return fields.get(lower);
        var pronoun = PronounKey.parse(key);
        boolean capital = Character.isUpperCase(words.charAt(0));
        if (pronoun.isEmpty() || !capital) return key;
        return new PronounKey(pronoun.get().role(), true, pronoun.get().ending()).key();
    }
}
