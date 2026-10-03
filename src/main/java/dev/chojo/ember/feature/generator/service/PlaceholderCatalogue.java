/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterCell;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PlaceholderGroup;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The values a template of one station can name, with the words the editor shows for them.
 *
 * <p>Keys are English and never change; labels are in the station's language. The built-in keys are
 * the same everywhere ({@link BuiltInPlaceholder}), and every profile question of the station that
 * holds an answer adds one key for the member and one for each of the two guardians.
 */
@Singleton
public class PlaceholderCatalogue {
    /** The prefix of a profile answer of the member. */
    public static final String PROFILE = "profile.";

    /** How the first and the second guardian are written in front of a key. */
    public static final List<String> GUARDIANS = List.of("guardian1.", "guardian2.");

    private final ProfileFieldRepository profileFields;
    private final StationRepository stations;

    @Inject
    public PlaceholderCatalogue(ProfileFieldRepository profileFields, StationRepository stations) {
        this.profileFields = profileFields;
        this.stations = stations;
    }

    /**
     * Every placeholder a template of the station can name, in the order the picker shows them.
     *
     * @param stationId the station
     * @return the placeholders
     */
    public List<Placeholder> forStation(int stationId) {
        String language = languageOf(stationId);
        var out = new ArrayList<Placeholder>();
        Arrays.stream(BuiltInPlaceholder.values())
                .map(value -> value.in(language))
                .forEach(out::add);
        out.add(new Placeholder(
                SignatureRole.ISSUER.token(),
                "en".equals(language) ? "Signature field: issuer" : "Unterschriftsfeld: Ausstellende Person",
                PlaceholderGroup.SIGNATURE,
                false,
                false));
        var fields = answerable(stationId);
        for (var field : fields) {
            out.add(new Placeholder(PROFILE + field.id(), field.name(), PlaceholderGroup.PROFILE, false, false));
        }
        for (int index = 0; index < GUARDIANS.size(); index++) {
            String prefix = GUARDIANS.get(index);
            String who = guardianWord(language, index + 1);
            for (var field : fields) {
                out.add(new Placeholder(
                        prefix + PROFILE + field.id(),
                        who + ": " + field.name(),
                        PlaceholderGroup.GUARDIAN,
                        false,
                        false));
            }
        }
        return out;
    }

    /**
     * The placeholders of the station by key.
     *
     * @param stationId the station
     * @return every placeholder, keyed
     */
    public Map<String, Placeholder> byKey(int stationId) {
        var keyed = new LinkedHashMap<String, Placeholder>();
        forStation(stationId).forEach(placeholder -> keyed.put(placeholder.key(), placeholder));
        return keyed;
    }

    /**
     * The profile questions of the station that hold an answer, which is every one a template can name.
     *
     * @param stationId the station
     * @return the questions
     */
    public List<ProfileField> answerable(int stationId) {
        return profileFields.findByStation(stationId).stream()
                .filter(field -> field.fieldType().holdsValue())
                .toList();
    }

    /**
     * The choice questions of the station, which are the ones pronouns can follow.
     *
     * @param stationId the station
     * @return the questions with their answers
     */
    public List<ProfileField> choiceFields(int stationId) {
        return profileFields.findByStation(stationId).stream()
                .filter(field -> field.fieldType() == FieldType.CHOICE)
                .toList();
    }

    /**
     * Refuses a template naming a placeholder the station does not have, and a legal one naming the
     * name a member is called by.
     *
     * <p>A signature field stands only where a letter's body names it, each signer once: it is a box
     * on the page, and in a title, a file name, a letterhead cell or a text on a PDF there is nowhere to
     * put one. A PDF template places its signature fields as fields of their own.
     *
     * @param stationId the station
     * @param draft     the template
     */
    public void requireKnown(int stationId, DocumentTemplateDraft draft) {
        var used = keysOf(draft.titlePattern(), draft.fileNamePattern(), draft.content());
        if (used.isEmpty()) return;
        var known = byKey(stationId);
        for (String key : used) {
            if (!known.containsKey(key)) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN.raise(RefusalDetail.text(key));
            }
        }
        requireSignaturesInBody(draft);
        if (draft.legal()) requireOfficial(used);
    }

    private static void requireSignaturesInBody(DocumentTemplateDraft draft) {
        String body = draft.content() instanceof LetterContent letter ? letter.bodyMarkdown() : "";
        Stream<String> outsideTheBody =
                switch (draft.content()) {
                    case LetterContent letter ->
                        letter.letterhead().cells().map(LetterCell::text).filter(Objects::nonNull);
                    case PdfContent pdf -> pdf.texts();
                };
        var elsewhere = Stream.concat(Stream.of(draft.titlePattern(), draft.fileNamePattern()), outsideTheBody)
                .map(PlaceholderTokens::keysIn)
                .flatMap(Set::stream);
        if (elsewhere.anyMatch(SignatureRole::isToken)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_SIGNATURE_IN_TEXT.raise();
        }
        var signers = PlaceholderTokens.occurrences(body).stream()
                .filter(SignatureRole::isToken)
                .toList();
        if (signers.size() != new HashSet<>(signers).size()) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE.raise();
        }
    }

    /**
     * Refuses the name a member is called by among the keys of a legal document.
     *
     * @param keys the keys a legal template names
     */
    public static void requireOfficial(Set<String> keys) {
        if (keys.contains(BuiltInPlaceholder.MEMBER_CALLED_NAME.key())) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL.raise();
        }
    }

    /**
     * Every key a template names anywhere: in its title, its file name and every text of its content.
     *
     * @param titlePattern    the title pattern
     * @param fileNamePattern the file name pattern
     * @param content         the letter, or the fields laid over the PDF
     * @return the keys, in the order they first appear
     */
    public static Set<String> keysOf(String titlePattern, String fileNamePattern, TemplateContent content) {
        var keys = new LinkedHashSet<String>();
        Stream.concat(Stream.of(titlePattern, fileNamePattern), content.texts())
                .map(PlaceholderTokens::keysIn)
                .forEach(keys::addAll);
        return keys;
    }

    /**
     * The words the station shows for some keys, for a message naming what is missing.
     *
     * @param stationId the station
     * @param keys      the keys
     * @return the labels in the order of the keys, the key itself where the station has no label for it
     */
    public List<String> labelsOf(int stationId, Iterable<String> keys) {
        var known = byKey(stationId);
        var labels = new ArrayList<String>();
        for (String key : keys) {
            var placeholder = known.get(key);
            labels.add(placeholder == null ? key : placeholder.label());
        }
        return labels;
    }

    private String languageOf(int stationId) {
        return StationFormat.languageOf(stations.findById(stationId).orElse(null));
    }

    private static String guardianWord(String language, int number) {
        return ("en".equals(language) ? "Guardian " : "Erziehungsberechtigte ") + number;
    }
}
