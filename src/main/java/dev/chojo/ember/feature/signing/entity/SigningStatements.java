/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.generator.entity.FieldStatements;

import java.util.Map;

/**
 * What the signers of one document confirm, exactly as it is shown to them.
 *
 * <p>A template may word the statement of each of its signature lines or boxes; a field it leaves alone
 * asks for the default statement of its signer. The defaults are worded in the document's language: the
 * member confirms having read the document and agreeing to it, a guardian declares that they hold custody
 * of the member, by name, and agree on the member's behalf, and the issuer confirms issuing it for the
 * station.
 *
 * @param own      the default statement of somebody signing their own field: the member, also through a
 *                 guardian's account
 * @param guardian the default statement of a guardian signing on behalf of the member, which declares that
 *                 they hold custody of them
 * @param issuer   the default statement of whoever issues the document for the station
 * @param written  the statements the template words itself, by the name of the field
 */
public record SigningStatements(String own, String guardian, String issuer, Map<String, String> written) {

    public SigningStatements {
        written = Map.copyOf(written);
    }

    /**
     * Statements with nothing worded by a template, the issuer confirming what the member does.
     *
     * @param own      what the member confirms, and the issuer
     * @param guardian what a guardian confirms
     */
    public SigningStatements(String own, String guardian) {
        this(own, guardian, own, Map.of());
    }

    /**
     * @param template   what the template words itself, and the language of the document
     * @param memberName the official name of the member the document is about, which a guardian declares
     *                   custody of
     * @return the statements of a document
     */
    public static SigningStatements of(FieldStatements template, String memberName) {
        var language = template.language();
        return new SigningStatements(
                language.pick(
                        "Ich habe dieses Dokument gelesen und stimme seinem Inhalt zu.",
                        "I have read this document and agree to its content."),
                language.pick(
                                "Ich bin für %s erziehungsberechtigt und stimme dem Inhalt dieses Dokuments in dieser Eigenschaft zu.",
                                "I hold parental responsibility for %s and agree to the content of this document in that capacity.")
                        .formatted(memberName),
                language.pick(
                        "Ich stelle dieses Dokument für die Wache aus.",
                        "I issue this document on behalf of the station."),
                template.byField());
    }

    /**
     * @param field the signature field
     * @return the statement its signer confirms: the template's own wording, else the default of its signer
     */
    public String of(SignatureFieldName field) {
        var worded = written.get(field.name());
        if (worded != null) return worded;
        return switch (field.role()) {
            case PARTICIPANT -> own;
            case GUARDIAN, ANY_GUARDIAN -> guardian;
            case ISSUER -> issuer;
        };
    }
}
