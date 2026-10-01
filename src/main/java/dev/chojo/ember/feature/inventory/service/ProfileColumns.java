/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.members.entity.ProfileField;
import dev.chojo.ember.feature.members.entity.ProfileFieldValue;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.question.QuestionText;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The profile questions an inventory sheet carries as extra columns beside the gear.
 *
 * <p>The member list and the movement sheet both offer them, and both print an answer the way every
 * other export does: a yes as a word in the station's language, a date as a day. A question that no
 * longer exists is left out, so the headers and the cells always line up.
 *
 * @param fields   the questions, in the order they were chosen
 * @param language the station's language
 */
record ProfileColumns(List<ProfileField> fields, String language) {

    /**
     * The chosen questions as columns.
     *
     * @param repository where the questions are read from
     * @param fieldIds   the questions chosen, in column order
     * @param language   the station's language
     * @return the columns, without the questions that are gone
     */
    static ProfileColumns of(ProfileFieldRepository repository, List<Integer> fieldIds, String language) {
        var fields = fieldIds.stream()
                .map(repository::findById)
                .flatMap(Optional::stream)
                .toList();
        return new ProfileColumns(fields, language);
    }

    /** The column headers, one per question. */
    List<String> names() {
        return fields.stream().map(ProfileField::name).toList();
    }

    /**
     * One member's answers as cells, empty where they gave none.
     *
     * @param answers whatever the member answered on their profile
     * @return one cell per column
     */
    List<String> cellsOf(List<ProfileFieldValue> answers) {
        return fields.stream().map(field -> cellOf(field, answers)).toList();
    }

    private String cellOf(ProfileField field, List<ProfileFieldValue> answers) {
        return answers.stream()
                .filter(answer -> answer.fieldId() == field.id())
                .findFirst()
                .map(answer -> QuestionText.format(field.fieldType().fieldType(), answer.value(), Map.of(), language))
                .orElse("");
    }
}
