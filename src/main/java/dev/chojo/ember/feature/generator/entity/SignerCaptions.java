/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The name printed under each signature field of a generated document, so it shows at a glance who signs
 * where. It is the official name of the signer as the document is generated: the member for
 * {@code participant}, the guardian at that place for {@code guardian<n>}, the issuer for {@code issuer},
 * and for {@code anyGuardian} any one guardian of the member by the member's name. Where nobody is known for
 * a field, such as a guardian place the member has nobody for or a document about nobody in particular, the
 * signer's role is printed in plain words instead.
 *
 * @param language  the language the document is written in, which the words for a role are in
 * @param member    the official name of the member the document is about, or null where it is about nobody
 * @param guardians the official names of the member's guardians in their set order
 * @param issuer    the official name of the issuer, or null where nobody is named
 */
public record SignerCaptions(
        DocumentLanguage language,
        @Nullable String member,
        List<String> guardians,
        @Nullable String issuer) {

    public SignerCaptions {
        guardians = List.copyOf(guardians);
    }

    /**
     * Captions that name nobody, for a document drawn without a member: every field prints its role.
     *
     * @param language the language the document is written in
     * @return the captions
     */
    public static SignerCaptions roles(DocumentLanguage language) {
        return new SignerCaptions(language, null, List.of(), null);
    }

    /**
     * @param fieldName the name of a signature field ({@link SignatureRole#fieldNames})
     * @return what is printed under it: the signer's name, or their role where nobody is known
     */
    public String of(String fieldName) {
        var place = SignatureRole.guardianPlace(fieldName);
        if (place.isPresent()) return guardian(place.getAsInt());
        return switch (fieldName) {
            case "participant" -> member == null ? language.pick("Teilnehmende Person", "Participant") : member;
            case "issuer" -> issuer == null ? language.pick("Ausstellende Person", "Issuer") : issuer;
            case "anyGuardian" ->
                member == null
                        ? language.pick("Eine erziehungsberechtigte Person", "A guardian")
                        : language.pick("Eine erziehungsberechtigte Person von ", "A guardian of ") + member;
            default -> fieldName;
        };
    }

    private String guardian(int place) {
        if (place <= guardians.size()) return guardians.get(place - 1);
        return language.pick("Erziehungsberechtigte Person ", "Guardian ") + place;
    }
}
