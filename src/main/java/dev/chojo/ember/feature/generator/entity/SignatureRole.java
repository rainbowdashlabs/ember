/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Who signs in a signature field of a generated document.
 *
 * <p>A signature field is an empty PDF signature field named after its role, so whatever signs the
 * document later finds the field of each signer by that name alone. The names are part of that
 * contract and never change.
 */
public enum SignatureRole {
    /** The member the document is about. */
    PARTICIPANT("participant"),
    /** The first guardian of that member. */
    GUARDIAN_1("guardian1"),
    /** The second guardian of that member. */
    GUARDIAN_2("guardian2"),
    /** Whoever issues the document for the station. */
    ISSUER("issuer");

    /** How a signature field is written in the body of a letter, before its role. */
    public static final String TOKEN_PREFIX = "signature.";

    private final String fieldName;

    SignatureRole(String fieldName) {
        this.fieldName = fieldName;
    }

    /** @return the name of the signature field in the PDF */
    public String fieldName() {
        return fieldName;
    }

    /** @return the placeholder key that stands for this field in the body of a letter */
    public String token() {
        return TOKEN_PREFIX + fieldName;
    }

    /**
     * @param name the name of a signature field
     * @return the role of that name, or empty where it is none
     */
    public static Optional<SignatureRole> ofFieldName(String name) {
        return Arrays.stream(values())
                .filter(role -> role.fieldName.equals(name))
                .findFirst();
    }

    /**
     * @param key a placeholder key
     * @return whether it stands for a signature field rather than a value
     */
    public static boolean isToken(String key) {
        return key.startsWith(TOKEN_PREFIX);
    }
}
