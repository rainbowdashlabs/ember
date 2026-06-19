/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void exactlyMinLengthAccepted() {
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH)));
    }

    @Test
    void oneCharBelowMinRejected() {
        assertEquals(
                PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH - 1)));
    }

    @Test
    void emptyRejected() {
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.validate(""));
    }

    @Test
    void nullRejected() {
        assertEquals(PasswordPolicy.Result.TOO_SHORT, PasswordPolicy.validate(null));
    }

    @Test
    void longPassphraseAccepted() {
        assertEquals(PasswordPolicy.Result.OK, PasswordPolicy.validate("x".repeat(200)));
    }

    @Test
    void messageMentionsMinLength() {
        assertTrue(PasswordPolicy.Result.TOO_SHORT.message().contains(Integer.toString(PasswordPolicy.MIN_LENGTH)));
    }
}
