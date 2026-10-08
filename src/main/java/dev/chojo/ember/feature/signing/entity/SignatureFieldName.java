/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The name of a signature field in a generated document, and who it asks to sign.
 *
 * @param name  the name as the document carries it
 * @param role  who signs it
 * @param place for a guardian field the guardian's place in the member's order, counted from 1; 0 for every
 *              other field
 */
public record SignatureFieldName(String name, FieldRole role, int place) {
    private static final Pattern GUARDIAN = Pattern.compile("guardian([1-9][0-9]?)");

    /**
     * Reads who a field asks to sign from its name.
     *
     * @param name the field's name
     * @return what it asks for, or empty for a name the generator never writes
     */
    public static Optional<SignatureFieldName> of(String name) {
        return switch (name) {
            case "participant" -> Optional.of(new SignatureFieldName(name, FieldRole.PARTICIPANT, 0));
            case "issuer" -> Optional.of(new SignatureFieldName(name, FieldRole.ISSUER, 0));
            case "anyGuardian" -> Optional.of(new SignatureFieldName(name, FieldRole.ANY_GUARDIAN, 0));
            default -> {
                var guardian = GUARDIAN.matcher(name);
                yield guardian.matches()
                        ? Optional.of(
                                new SignatureFieldName(name, FieldRole.GUARDIAN, Integer.parseInt(guardian.group(1))))
                        : Optional.empty();
            }
        };
    }
}
