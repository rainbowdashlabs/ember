/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.FieldDraft;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.PronounSet;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one gender field a station asks, its own or its association's, and the rules that keep it one.
 *
 * <p>A gender field is a choice whose answers carry the pronouns a document writes for the member. A
 * station has at most one: two would leave a letter to guess which of them speaks. Where an association
 * asks one, its stations do not ask their own, and an association does not ask one where a station of it
 * already does.
 *
 * <p>A field becomes a gender field when it is created as one, or when a choice field is turned into one;
 * its answers stay as they are, since the answers of the choice are the answers of the gender field.
 */
@Singleton
public class GenderFields {
    private final ProfileFieldCore core;
    private final StationRepository stations;

    @Inject
    public GenderFields(ProfileFieldCore core, StationRepository stations) {
        this.core = core;
        this.stations = stations;
    }

    /**
     * The gender field a station asks: its own, or else its association's.
     *
     * @param stationId the station
     * @return the field, or empty where the station asks none
     */
    public Optional<OwnedProfileField> askedAt(int stationId) {
        var own = ownOf(FieldOrigin.STATION, stationId);
        if (own.isPresent()) return own;
        var association = core.owner(FieldOrigin.CLUSTER);
        return association.ofType(FieldType.GENDER).stream()
                .filter(field -> association.askedAt(stationId, field.id()).isPresent())
                .findFirst();
    }

    /**
     * The answer a member gave to a gender field.
     *
     * @param memberId the member
     * @param field    the gender field
     * @return the answer as it is stored, or empty where the member gave none
     */
    public Optional<String> answerOf(int memberId, OwnedProfileField field) {
        return answerIn(core.owner(field.origin()).answersOf(memberId), field);
    }

    /**
     * The answer to a gender field among the answers of one member read before.
     *
     * @param answers every answer the member gave to the questions of the field's owner
     * @param field   the gender field
     * @return the answer as it is stored, or empty where the member gave none
     */
    public static Optional<String> answerIn(List<ProfileFieldValue> answers, OwnedProfileField field) {
        return answers.stream()
                .filter(value -> value.fieldId() == field.id())
                .findFirst()
                .map(value -> QuestionValues.read(value.value()))
                .filter(answer -> !answer.isBlank());
    }

    /**
     * Refuses a gender field the owner may not write: a second one, one turned from a type other than a
     * choice, or one whose pronouns are longer than a pronoun can be. A field of another type passes.
     *
     * @param owner    whose field it is
     * @param draft    the field as it is to be written
     * @param previous the type the field had before, or null for a new one
     * @param fieldId  the field being changed, 0 for a new one
     */
    public void requireAllowed(Owner owner, FieldDraft draft, @Nullable FieldType previous, int fieldId) {
        if (draft.type() != FieldType.GENDER) return;
        var origin = originOf(owner);
        var refusals = core.owner(origin).definitionRefusals();
        if (previous != null && previous != FieldType.GENDER && previous != FieldType.CHOICE) {
            throw refusals.genderNotFromChoice().raise();
        }
        if (pronounsTooLong(draft.config().pronouns()))
            throw refusals.pronounTooLong().raise();
        otherThan(owner, origin, fieldId).ifPresent(other -> {
            throw refusals.secondGender().raise(other.name());
        });
    }

    private Optional<OwnedProfileField> otherThan(Owner owner, FieldOrigin origin, int fieldId) {
        return switch (owner) {
            case Owner.Station station -> askedAt(station.stationId()).filter(field -> !isSame(field, origin, fieldId));
            case Owner.Association association ->
                ownOf(FieldOrigin.CLUSTER, association.clusterId())
                        .filter(field -> field.id() != fieldId)
                        .or(() -> stations.findByCluster(association.clusterId()).stream()
                                .map(Station::id)
                                .map(stationId -> ownOf(FieldOrigin.STATION, stationId))
                                .flatMap(Optional::stream)
                                .findFirst());
            case Owner.Instance ignored -> Optional.empty();
        };
    }

    private Optional<OwnedProfileField> ownOf(FieldOrigin origin, int ownerId) {
        return core.owner(origin).ofType(FieldType.GENDER).stream()
                .filter(field -> field.ownerId() == ownerId)
                .findFirst();
    }

    private static boolean isSame(OwnedProfileField field, FieldOrigin origin, int fieldId) {
        return field.origin() == origin && field.id() == fieldId;
    }

    private static boolean pronounsTooLong(@Nullable Map<String, Map<String, PronounSet>> pronouns) {
        if (pronouns == null) return false;
        return pronouns.values().stream()
                .filter(Objects::nonNull)
                .flatMap(byLanguage -> byLanguage.values().stream())
                .filter(Objects::nonNull)
                .anyMatch(PronounSet::tooLong);
    }

    private static FieldOrigin originOf(Owner owner) {
        return switch (owner) {
            case Owner.Station ignored -> FieldOrigin.STATION;
            case Owner.Association ignored -> FieldOrigin.CLUSTER;
            case Owner.Instance ignored -> throw new IllegalArgumentException("The instance asks no profile questions");
        };
    }
}
