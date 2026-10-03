/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
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
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
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
 * The values a template of a station or an association can name, with the words the editor shows for
 * them.
 *
 * <p>Keys are English and never change; labels are in the language of the station, or of the
 * association's home station. The built-in keys are the same everywhere ({@link BuiltInPlaceholder}), the
 * association's only where there is an association to name. A profile question that holds an answer adds
 * a key for the member where it is put to the member, and one for each of the two guardians where it is
 * put to guardians ({@link OfferedFields}). A key the catalogue does not hold is refused when a template is
 * saved, so a template names only answers somebody is asked for.
 *
 * <p>A station's template names the station's own questions ({@code profile.<id>}) and the questions its
 * association puts to its members ({@code associationProfile.<id>}). An association's template names the
 * association's questions only, since it is used at every station of it and only those are asked at all
 * of them; at a station a question does not reach, its value is missing like any other.
 */
@Singleton
public class PlaceholderCatalogue {
    /** The prefix of an answer of the member to a question of the station. */
    public static final String PROFILE = "profile.";

    /** The prefix of an answer of the member to a question of the association. */
    public static final String ASSOCIATION_PROFILE = "associationProfile.";

    /** How the first and the second guardian are written in front of a key. */
    public static final List<String> GUARDIANS = List.of("guardian1.", "guardian2.");

    private final ProfileFieldRepository profileFields;
    private final ClusterProfileFieldService associationFields;
    private final StationRepository stations;
    private final OwnerStores stores;

    @Inject
    public PlaceholderCatalogue(
            ProfileFieldRepository profileFields,
            ClusterProfileFieldService associationFields,
            StationRepository stations,
            OwnerStores stores) {
        this.profileFields = profileFields;
        this.associationFields = associationFields;
        this.stations = stations;
        this.stores = stores;
    }

    /**
     * What an owner's templates are asked about.
     *
     * @param language       the language the labels are written in
     * @param station        the station's questions, none for an association
     * @param association    the association's questions, those that reach the station for a station
     * @param hasAssociation whether there is an association to name
     */
    private record Asked(String language, OfferedFields station, OfferedFields association, boolean hasAssociation) {}

    /**
     * Every placeholder a template of the owner can name, in the order the picker shows them: by
     * category, and within the member and each guardian the values every member has before the profile
     * answers in the order of the form, the station's before the association's.
     *
     * @param owner the station or the association that keeps the template
     * @return the placeholders
     */
    public List<Placeholder> forOwner(Owner owner) {
        var asked = asked(owner);
        String language = asked.language();
        var out = new ArrayList<Placeholder>();
        Arrays.stream(BuiltInPlaceholder.values())
                .filter(value -> asked.hasAssociation() || value.in(language).group() != PlaceholderGroup.ASSOCIATION)
                .map(value -> value.in(language))
                .forEach(out::add);
        PronounKey.all().stream().map(pronoun -> pronoun.in(language)).forEach(out::add);
        addAnswers(out, asked.station(), PROFILE, PlaceholderSubgroup.PROFILE, language);
        addAnswers(out, asked.association(), ASSOCIATION_PROFILE, PlaceholderSubgroup.ASSOCIATION_PROFILE, language);
        out.sort(Comparator.comparing(Placeholder::category));
        return out;
    }

    private static void addAnswers(
            List<Placeholder> out,
            OfferedFields offered,
            String prefix,
            PlaceholderSubgroup subgroup,
            String language) {
        offered.member()
                .forEach(field -> out.add(answer(field, prefix, PlaceholderCategory.MEMBER, subgroup, language)));
        for (int index = 0; index < GUARDIANS.size(); index++) {
            String guardian = GUARDIANS.get(index) + prefix;
            var category = PlaceholderCategory.guardian(index);
            offered.guardians().forEach(field -> out.add(answer(field, guardian, category, subgroup, language)));
        }
    }

    private Asked asked(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> {
                int stationId = station.stationId();
                yield new Asked(
                        languageOf(stationId),
                        OfferedFields.of(
                                profileFields.findByStation(stationId),
                                profileFields.findAssignmentsByStation(stationId)),
                        OfferedFields.reaching(Arrays.stream(ProfileFieldScope.values())
                                .flatMap(role -> associationFields.findForStation(stationId, role).stream())
                                .toList()),
                        stores.associationOf(stationId).isPresent());
            }
            case Owner.Association association ->
                new Asked(
                        languageOf(stores.libraryOf(association)),
                        OfferedFields.NONE,
                        OfferedFields.ofAssociation(
                                associationFields.findByCluster(association.clusterId()),
                                associationFields.findAssignmentsByCluster(association.clusterId())),
                        true);
            case Owner.Instance ignored -> new Asked("de", OfferedFields.NONE, OfferedFields.NONE, false);
        };
    }

    /**
     * The placeholder of an answer to a profile question, below the profile of the member or a guardian
     * and below the heading the question stands under on the form.
     */
    private static Placeholder answer(
            SectionedField sectioned,
            String prefix,
            PlaceholderCategory category,
            PlaceholderSubgroup subgroup,
            String language) {
        var field = sectioned.field();
        String who = category.word(language);
        var path = new ArrayList<>(List.of(who, subgroup.word(language)));
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
     * The placeholders of an owner by key.
     *
     * @param owner the station or the association that keeps the template
     * @return every placeholder, keyed
     */
    public Map<String, Placeholder> byKey(Owner owner) {
        var keyed = new LinkedHashMap<String, Placeholder>();
        forOwner(owner).forEach(placeholder -> keyed.put(placeholder.key(), placeholder));
        return keyed;
    }

    /**
     * The answers of the member to profile questions a template of the owner can name, which an import
     * recognises by the name of their question.
     *
     * @param owner the station or the association that keeps the template
     * @return the placeholders in the order of the member's form, labelled with the name of the question
     */
    public List<Placeholder> answerable(Owner owner) {
        return forOwner(owner).stream()
                .filter(placeholder -> placeholder.group() == PlaceholderGroup.PROFILE)
                .toList();
    }

    /**
     * Refuses a template naming a placeholder its owner does not have, a legal one naming the name a
     * member is called by, and one naming the values of an appointment without being meant for
     * appointments, where they would never be filled.
     *
     * @param owner the station or the association that keeps the template
     * @param draft the template
     */
    public void requireKnown(Owner owner, DocumentTemplateDraft draft) {
        var used = keysOf(draft.titlePattern(), draft.fileNamePattern(), draft.content());
        if (used.isEmpty()) return;
        var known = byKey(owner);
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

    private String languageOf(int stationId) {
        return StationFormat.languageOf(stations.findById(stationId).orElse(null));
    }
}
