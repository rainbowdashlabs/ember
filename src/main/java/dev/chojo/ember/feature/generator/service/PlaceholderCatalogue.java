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
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PlaceholderCategory;
import dev.chojo.ember.feature.generator.entity.PlaceholderGroup;
import dev.chojo.ember.feature.generator.entity.PlaceholderSubgroup;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.service.OfferedFields.SectionedField;
import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The values a template of one station can name, with the words the editor shows for them.
 *
 * <p>Keys are English and never change; labels are in the station's language. The built-in keys are
 * the same everywhere ({@link BuiltInPlaceholder}). A profile question that holds an answer adds a key for
 * the member where it is put to the member, and one for each of the two guardians where it is put to
 * guardians ({@link OfferedFields}). A key the catalogue does not hold is refused when a template is saved,
 * so a template names only answers somebody is asked for.
 *
 * <p>The questions of an association are not offered: a template is the station's and names the
 * station's questions only.
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
     * Every placeholder a template of the station can name, in the order the picker shows them: by
     * category, and within the member and each guardian the values every member has before the profile
     * answers in the order of the form.
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
        PronounKey.all().stream().map(pronoun -> pronoun.in(language)).forEach(out::add);
        var offered = offered(stationId);
        offered.member().forEach(field -> out.add(answer(field, PROFILE, PlaceholderCategory.MEMBER, language)));
        for (int index = 0; index < GUARDIANS.size(); index++) {
            String prefix = GUARDIANS.get(index) + PROFILE;
            var category = PlaceholderCategory.guardian(index);
            offered.guardians().forEach(field -> out.add(answer(field, prefix, category, language)));
        }
        out.sort(Comparator.comparing(Placeholder::category));
        return out;
    }

    /**
     * The placeholder of an answer to a profile question, below the profile of the member or a guardian
     * and below the heading the question stands under on the form.
     */
    private static Placeholder answer(
            SectionedField sectioned, String prefix, PlaceholderCategory category, String language) {
        var field = sectioned.field();
        String who = category.word(language);
        var path = new ArrayList<>(List.of(who, PlaceholderSubgroup.PROFILE.word(language)));
        if (sectioned.section() != null) path.add(sectioned.section());
        path.add(field.name());
        boolean member = category == PlaceholderCategory.MEMBER;
        return new Placeholder(
                prefix + field.id(),
                member ? field.name() : who + ": " + field.name(),
                member ? PlaceholderGroup.PROFILE : PlaceholderGroup.GUARDIAN,
                category,
                List.copyOf(path),
                false,
                false);
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
     * The profile questions of the station a template can name, for the member and for a guardian: those
     * that hold an answer and are put to the member or to guardians.
     *
     * @param stationId the station
     * @return the questions by whom they are put to
     */
    public OfferedFields offered(int stationId) {
        return OfferedFields.of(
                profileFields.findByStation(stationId), profileFields.findAssignmentsByStation(stationId));
    }

    /**
     * The profile questions a template can name for the member.
     *
     * @param stationId the station
     * @return the questions in the order of the member's form
     */
    public List<ProfileField> answerable(int stationId) {
        return offered(stationId).member().stream().map(SectionedField::field).toList();
    }

    /**
     * Refuses a template naming a placeholder the station does not have, a legal one naming the name a
     * member is called by, and one naming the values of an appointment without being meant for
     * appointments, where they would never be filled.
     *
     * @param stationId the station
     * @param draft     the template
     */
    public void requireKnown(int stationId, DocumentTemplateDraft draft) {
        var used = keysOf(draft.titlePattern(), draft.fileNamePattern(), draft.content());
        if (used.isEmpty()) return;
        var known = byKey(stationId);
        for (String key : used) {
            var placeholder = known.get(key);
            if (placeholder == null) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN.raise(RefusalDetail.text(key));
            }
            if (placeholder.eventOnly() && !draft.forAppointments()) {
                throw DocumentRefusal.DOCUMENT_TEMPLATE_APPOINTMENT_VALUES_OUTSIDE.raise(
                        RefusalDetail.text(placeholder.label()));
            }
        }
        if (draft.legal()) requireOfficial(used);
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
}
