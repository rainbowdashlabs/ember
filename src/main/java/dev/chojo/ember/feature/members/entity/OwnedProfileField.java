/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.Question;

import java.util.Optional;

/**
 * A profile question as the shared answer pipeline reads it, whichever owner asked it.
 *
 * @param origin  who asked it, which also says which table its answers live in
 * @param id      the question, within its owner's numbering
 * @param ownerId the station or association that asked it
 * @param name    what it is called
 * @param type    what kind of answer it takes
 * @param config  its settings
 * @param required whether an answer is expected
 */
public record OwnedProfileField(
        FieldOrigin origin,
        int id,
        int ownerId,
        String name,
        FieldType type,
        ProfileFieldConfig config,
        boolean required) {

    /**
     * The question every answer is measured against, or nothing for a heading, a spacer or an age.
     *
     * @return the question
     */
    public Optional<Question> question() {
        return config.settings(required).asQuestion(name, type);
    }

    /**
     * Whether a change to an answer waits for somebody at the station to confirm they saw it.
     *
     * @return the question's own setting
     */
    public boolean notifyOnChange() {
        return config.notifyOnChange();
    }
}
