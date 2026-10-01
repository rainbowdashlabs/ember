/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Where the page of a single-answer choice question leads, depending on the option picked.
 *
 * <p>An option without an entry follows the page's own target, which is what "otherwise" means in the
 * editor. Only one question per page carries a branch, and it names the options by their key.
 *
 * @param targets the page that follows, per option key
 */
public record QuestionBranch(@JsonValue Map<String, PageTarget> targets) {
    private static final Logger log = getLogger(QuestionBranch.class);
    private static final TypeReference<Map<String, PageTarget>> TARGETS = new TypeReference<>() {};

    /**
     * Keeps the targets in the order they were given, each with a kind: a target sent without one leads
     * on to the page below, so what is stored is what was checked and what the walk follows.
     */
    public QuestionBranch {
        var normalised = new LinkedHashMap<String, PageTarget>();
        if (targets != null) targets.forEach((key, target) -> normalised.put(key, PageTarget.orNext(target)));
        targets = normalised;
    }

    /**
     * A branch read from the map it is written as.
     *
     * @param targets the page that follows, per option key
     * @return the branch
     */
    @JsonCreator
    public static QuestionBranch fromMap(Map<String, PageTarget> targets) {
        return new QuestionBranch(targets);
    }

    /**
     * Reads a stored branch.
     *
     * @param json the stored value, possibly null
     * @return the branch, or null where the question has none
     */
    public static QuestionBranch parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new QuestionBranch(Json.MAPPER.readValue(json, TARGETS));
        } catch (Exception e) {
            log.error("Failed to parse question branch: {}", json, e);
            return null;
        }
    }

    /**
     * Writes the branch for storage.
     *
     * @param branch the branch, possibly none
     * @return the stored value, or null where there is no branch
     */
    public static String toJson(QuestionBranch branch) {
        if (branch == null) return null;
        try {
            return Json.MAPPER.writeValueAsString(branch.targets());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Where the given option leads, or null where it follows the page's own target.
     *
     * @param optionKey the option picked
     * @return the target, or null
     */
    public PageTarget targetOf(String optionKey) {
        return targets.get(optionKey);
    }
}
