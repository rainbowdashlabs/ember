/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import org.jspecify.annotations.Nullable;

/**
 * What the signer of a signature field confirms, as a template writes it: a letter's signature line and a
 * PDF template's signature field alike. A statement left empty stands for the default one of its signer,
 * which the signing feature words in the template's language.
 */
final class SignatureStatementChecks {
    /** The longest statement, short enough to read on a phone before signing. */
    static final int MAX_STATEMENT = 500;

    private SignatureStatementChecks() {}

    /**
     * @param raw the statement as the editor sent it
     * @return the statement without surrounding blanks, or null where it is empty
     */
    static @Nullable String checked(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        String statement = raw.strip();
        if (statement.length() > MAX_STATEMENT) throw DocumentRefusal.DOCUMENT_TEMPLATE_STATEMENT_TOO_LONG.raise();
        return statement;
    }
}
