/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Map;

/**
 * What the signers of a member's document confirm, as its template says.
 *
 * @param language the language the document is written in, which the default statements are worded in
 * @param byField  the statement by the name of the signature field, for the fields whose line or box
 *                 says one; every other field keeps the default statement of its signer
 */
public record FieldStatements(DocumentLanguage language, Map<String, String> byField) {

    public FieldStatements {
        byField = Map.copyOf(byField);
    }

    /**
     * @param language the language the document is written in
     * @return statements that leave every field to the default statement of its signer
     */
    public static FieldStatements defaults(DocumentLanguage language) {
        return new FieldStatements(language, Map.of());
    }
}
