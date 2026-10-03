/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.OwnedProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link ProfileFieldCore} asks of one owner of profile questions: a station or an association.
 *
 * <p>The core decides: which answers are written, how they are measured and recorded, when a definition
 * is fit to be written down. An owner only answers questions about itself: which tables hold its
 * questions and answers, which of its questions reach a station, what it offers, which refusal codes it
 * speaks, and who it tells about a change. One implementation per {@link FieldOrigin}, bound by origin.
 */
public interface ProfileFieldOwner {

    /**
     * Which owner this is, and so which table an answer to one of its questions lives in.
     *
     * @return the origin its questions carry
     */
    FieldOrigin origin();

    /**
     * The owner's questions put to one kind of member at a station, in the order that audience sees them.
     *
     * @param stationId the station
     * @param role      the kind of member
     * @return the questions as that audience meets them
     */
    List<ProfileFieldService.MergedField> putTo(int stationId, ProfileFieldScope role);

    /**
     * The owner's questions put to a group the member is in, which only a station can do.
     *
     * @param member the member
     * @return the questions, each once however many of their groups it reaches
     */
    List<ProfileFieldService.MergedField> putToGroupsOf(StationMember member);

    /**
     * One of the owner's questions, where it reaches the station.
     *
     * @param stationId the member's station
     * @param fieldId   the question
     * @return the question, or empty where it is not this owner's or does not reach that station
     */
    Optional<OwnedProfileField> askedAt(int stationId, int fieldId);

    /**
     * The owner's refusal for an answer to a question it does not ask at the member's station.
     *
     * @return the refusal
     */
    Refusal notAskedHere();

    /**
     * Every answer one member gave to the owner's questions that still reach them.
     *
     * @param memberId the member
     * @return the answers
     */
    List<ProfileFieldValue> answersOf(int memberId);

    /**
     * Every answer given to one of the owner's questions, whoever gave it.
     *
     * @param fieldId the question
     * @return the answers
     */
    List<ProfileFieldValue> answersTo(int fieldId);

    /**
     * Keeps an answer, or removes it.
     *
     * @param memberId the member
     * @param fieldId  the question
     * @param answer   the answer as kept, or {@code null} where nothing is kept
     */
    void keep(int memberId, int fieldId, @Nullable JsonNode answer);

    /**
     * Removes the member's answers to every question not marked to be kept when a member leaves.
     *
     * @param memberId the member who leaves
     */
    void clearOnArchive(int memberId);

    /**
     * Every one of the owner's questions of one type, across all stations or associations.
     *
     * @param type the type
     * @return the questions
     */
    List<OwnedProfileField> ofType(FieldType type);

    /**
     * Tells whoever this owner tells about a change to a member's answers.
     *
     * @param member     whose answers changed
     * @param author     the author's membership at the member's station, or {@code null} where they have none
     * @param writer     who wrote them
     * @param fieldNames every question whose answer changed in this save, whoever asked it
     */
    void changed(StationMember member, @Nullable Integer author, ProfileWriter writer, List<String> fieldNames);

    /**
     * The types this owner may ask.
     *
     * @return the offered types
     */
    Set<FieldType> offeredTypes();

    /**
     * The names this owner's questions already carry, which a numbered spacer has to avoid.
     *
     * @param ownerId the station or association
     * @return the names
     */
    Set<String> namesTaken(int ownerId);

    /**
     * The owner's refusals for a definition the shared checks turn away.
     *
     * @return the refusals
     */
    DefinitionRefusals definitionRefusals();

    /**
     * The codes an owner raises for a definition that cannot be written down. They stay per owner because
     * the codes are public.
     *
     * @param nameMissing        a question other than a spacer given no name
     * @param typeNotOffered     a type the owner does not ask
     * @param defaultNotAccepted a starting value the question would refuse as an answer
     * @param expiryOutOfRange   expiry settings that count backwards or repeat without a gap
     * @param secondGender       a gender field where the station already has one, its own or its
     *                           association's
     * @param genderNotFromChoice a field of another type than a choice turned into a gender field
     * @param pronounTooLong     a pronoun of a gender field longer than a pronoun may be
     */
    record DefinitionRefusals(
            Refusal nameMissing,
            Refusal typeNotOffered,
            Refusal defaultNotAccepted,
            Refusal expiryOutOfRange,
            Refusal secondGender,
            Refusal genderNotFromChoice,
            Refusal pronounTooLong) {}
}
