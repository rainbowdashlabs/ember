/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.question.QuestionConfigs;
import dev.chojo.ember.feature.question.QuestionSettings;

import java.util.List;

/**
 * Configuration for a waiting list field, stored as JSONB.
 *
 * @param options     selectable values for choice-type fields
 * @param placeholder placeholder text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingListFieldConfig(List<String> options, String placeholder) {
    public static final WaitingListFieldConfig EMPTY = new WaitingListFieldConfig(null, null);

    public static WaitingListFieldConfig parse(String json) {
        return QuestionConfigs.parse(json, WaitingListFieldConfig.class, EMPTY);
    }

    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /**
     * What this field says about the question it asks. Whether it has to be answered is a column of
     * its own on a waiting list rather than a setting, so the field itself adds that.
     */
    public QuestionSettings settings() {
        return QuestionSettings.none().withOptions(options);
    }
}
