/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.account.entity.Account;

/**
 * The halves a member's name is written from, and the four ways of writing them.
 * <p>
 * One person is written differently depending on who is reading: a station talking about its own
 * people uses the name they are called by, a list of people has to say who somebody is as well, a
 * document carries the register name, and a mail greets by a first name alone. Keeping the halves
 * apart is what lets one reading of the database answer all four.
 * <p>
 * A member who has left has no account left to read from and carries one frozen string instead,
 * which is the name the register held when they left. Every form gives that string back unchanged,
 * because there is nothing left to take apart.
 *
 * @param firstName the register's first name, absent for somebody who has left
 * @param lastName the surname, absent for somebody who has left
 * @param frozen the whole name of somebody who has left, absent for everybody else
 */
public record NameParts(String firstName, String lastName, String frozen) {

    private static final NameParts UNKNOWN = new NameParts(null, null, null);

    /** The halves of a member whose account can still be read. */
    public static NameParts of(String firstName, String lastName) {
        return new NameParts(blankToNull(firstName), blankToNull(lastName), null);
    }

    /**
     * The halves of an account, for the places that hold one without knowing which membership it is
     * being read for.
     *
     * <p>A nickname belongs to a member at a station rather than to an account, so a name written
     * from an account alone is the register name. Where the station is known, ask the resolver for
     * the member instead.
     */
    public static NameParts of(Account account) {
        return account == null ? UNKNOWN : of(account.firstName(), account.lastName());
    }

    /** The single name a member keeps after leaving. */
    public static NameParts frozen(String name) {
        return new NameParts(null, null, blankToNull(name));
    }

    /** A member nothing is known about. */
    public static NameParts unknown() {
        return UNKNOWN;
    }

    /** Whether anything at all is known, which is what decides if this is worth keeping. */
    public boolean known() {
        return frozen != null || firstName != null || lastName != null;
    }

    /** The name a station reads on its own screens. */
    public String called() {
        return join(firstName);
    }

    /**
     * The name that says who somebody is and what they are called at once.
     *
     * <p>Identical to the register name until there is a second first name to put beside it.
     */
    public String identified() {
        return official();
    }

    /** The register name, as a document carries it. */
    public String official() {
        return join(firstName);
    }

    /** The first name a mail says hello to, standing on its own. */
    public String greeting() {
        return frozen != null ? frozen : firstName;
    }

    private String join(String first) {
        if (frozen != null) return frozen;
        if (first == null) return lastName;
        if (lastName == null) return first;
        return first + " " + lastName;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
