/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A field of a generated document that the signer of one signature field types into when they sign it,
 * such as a phone number or an emergency contact.
 *
 * <p>It is an empty PDF text field named {@code fill-<signature field>-<number>}: the signature field whose
 * signer fills it in, and the number of the field in its template, which keeps two fields of one signer
 * apart. Its label is the field's alternate name, which is also what a screen reader announces for it,
 * whether it must be filled in is the field's required flag, and the most characters it takes is the
 * field's maximum length. All of it is part of the document, so the hash a signer binds to covers which
 * fields there are and how they are labelled.
 *
 * @param name           the name of the text field in the document
 * @param signatureField the name of the signature field whose signer fills it in
 * @param label          what the field asks for, as the signer is shown it
 * @param required       whether the signer has to fill it in to sign
 * @param maxLength      the most characters the value may have
 */
public record FillInField(String name, String signatureField, String label, boolean required, int maxLength) {

    /** The most characters any value may have, whatever a template allows. */
    public static final int LONGEST_VALUE = 500;

    /** The longest label a field may have. */
    public static final int LONGEST_LABEL = 100;

    /** How many fields one signer can be asked to fill in. */
    public static final int MOST_PER_SIGNER = 20;

    private static final String PREFIX = "fill-";
    private static final Pattern NAME = Pattern.compile("fill-([A-Za-z0-9]+)-([0-9]{1,4})");

    /**
     * @param signatureField the signature field whose signer fills it in
     * @param number         the number of the field in its template
     * @return the name of the text field
     */
    public static String nameOf(String signatureField, int number) {
        return PREFIX + signatureField + "-" + number;
    }

    /**
     * @param name the name of a text field
     * @return the signature field whose signer fills it in, or empty for a text field that is not one to
     *     fill in at signing
     */
    public static Optional<String> signatureFieldOf(String name) {
        var matcher = NAME.matcher(name);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * @param maxLength the most characters a template allows, or null where it sets no limit of its own
     * @return the limit a field holds the value to
     */
    public static int effectiveMaxLength(@Nullable Integer maxLength) {
        return maxLength == null ? LONGEST_VALUE : Math.min(maxLength, LONGEST_VALUE);
    }
}
