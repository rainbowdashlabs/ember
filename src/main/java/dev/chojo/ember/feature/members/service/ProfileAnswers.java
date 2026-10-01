/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * One answer to a profile question on its way into storage, whether the station asked it or its
 * association did.
 *
 * <p>Both are the same kind of question asked by somebody else, so both measure an answer the same
 * way and keep it in the same shape: yes or no as a boolean, a number as a number, everything else as
 * a string, and nothing at all where nothing was said. An association's answers used to be kept as
 * whatever arrived, unmeasured.
 *
 * <p>Only what a save changes is measured. The profile screens send every answer back on each save,
 * so an answer stored before anything checked it would otherwise turn the whole save away over a
 * field the member never touched; {@link #unchanged} is what the services ask first.
 */
public final class ProfileAnswers {

    private ProfileAnswers() {}

    /**
     * What an answer says, as plain text, from the JSON document the profile screens send.
     *
     * @param sent the answer as a JSON document, or null
     * @return the answer as plain text, empty where nothing was said
     * @throws BadRequestResponse where it is not a JSON document
     */
    public static String said(@Nullable String sent) {
        return QuestionValues.read(Json.document(sent));
    }

    /**
     * Whether an answer says what is already stored, however each of the two is written.
     *
     * @param stored the stored answer as its JSON text, or null where there is none
     * @param said   the new answer as plain text
     */
    public static boolean unchanged(@Nullable String stored, String said) {
        return QuestionValues.read(stored).equals(said);
    }

    /**
     * The answer as it is kept, once it is measured against the question.
     *
     * <p>Whether the question had to be answered is not asked here: a profile is filled in over
     * time, and a required question only counts towards whether a profile is complete.
     *
     * @param type     the question's type
     * @param question the question, or nothing for a type that holds no value
     * @param said     the answer as plain text
     * @return the document to keep, or null where nothing is kept
     * @throws BadRequestResponse naming the field and what is wrong with the answer
     */
    public static @Nullable JsonNode kept(ProfileFieldType type, Optional<Question> question, String said) {
        if (type.isCalculated() && !said.isEmpty()) {
            throw Refusal.PROFILE_AGE_TAKES_NO_ANSWER.raise();
        }
        question.flatMap(asked -> QuestionCheck.answerIfGiven(asked, said)).ifPresent(problem -> {
            throw new BadRequestResponse(problem.message());
        });
        return QuestionValues.write(type.fieldType(), said);
    }

    /**
     * The kept answer as the change history records it.
     *
     * @param kept the document kept, or null where nothing is
     */
    public static String recorded(@Nullable JsonNode kept) {
        return kept == null ? "null" : kept.toString();
    }
}
