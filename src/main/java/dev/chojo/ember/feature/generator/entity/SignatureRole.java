/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Who signs in a signature field of a generated document.
 *
 * <p>A signature field is an empty PDF signature field named after whoever signs it, so whatever signs
 * the document later finds the field of each signer by that name alone. The names are part of that
 * contract and never change: {@code participant}, {@code issuer}, {@code guardian1}, {@code guardian2}
 * and further {@code guardian<n>} for the guardian at that place in the member's order, and
 * {@code anyGuardian} for a field any of the member's guardians may sign.
 *
 * <p>How many fields a signer asks for depends on the member: a second guardian signs only where the
 * member has one, and every guardian signs where each of them is asked to. A signer that names one
 * particular person, such as a team member, is a further constant here with what it names kept beside
 * the signer in the block that asks for it.
 */
public enum SignatureRole {
    /** The member the document is about. */
    PARTICIPANT,
    /** The first guardian of that member, whose line stays where the member has no guardian yet. */
    GUARDIAN_1,
    /** The second guardian of that member, left out where the member has fewer than two. */
    GUARDIAN_2,
    /** Every guardian of that member, one field each and all of them to be signed. */
    EACH_GUARDIAN,
    /** Any one guardian of that member, in a single field. */
    ANY_GUARDIAN,
    /** Whoever issues the document for the station. */
    ISSUER;

    /**
     * The most guardians a check of a template's signers looks at, which is enough for every signer to
     * show each field it can ask for beside another signer.
     */
    public static final int GUARDIANS_CHECKED = 2;

    /**
     * The signature fields this signer asks for in the document of one member.
     *
     * @param guardians how many guardians the member has
     * @return the names of the fields, none where the signer has nobody to sign
     */
    public List<String> fieldNames(int guardians) {
        return switch (this) {
            case PARTICIPANT -> List.of("participant");
            case GUARDIAN_1 -> List.of(guardian(1));
            case GUARDIAN_2 -> guardians >= 2 ? List.of(guardian(2)) : List.of();
            case EACH_GUARDIAN ->
                IntStream.rangeClosed(1, Math.max(1, guardians))
                        .mapToObj(SignatureRole::guardian)
                        .toList();
            case ANY_GUARDIAN -> List.of("anyGuardian");
            case ISSUER -> List.of("issuer");
        };
    }

    /**
     * @param names the names of the signature fields of one document
     * @return whether no field is asked for twice, which would ask one person to sign twice
     */
    public static boolean distinct(Collection<String> names) {
        return new HashSet<>(names).size() == names.size();
    }

    /**
     * @param roles the signers of a document whose fields always all apply, as on a PDF template
     * @return whether no member's document would ask for one field twice
     */
    public static boolean distinctForEveryMember(Collection<SignatureRole> roles) {
        return IntStream.rangeClosed(0, GUARDIANS_CHECKED)
                .allMatch(guardians -> distinct(roles.stream()
                        .flatMap(role -> role.fieldNames(guardians).stream())
                        .toList()));
    }

    private static String guardian(int place) {
        return "guardian" + place;
    }
}
