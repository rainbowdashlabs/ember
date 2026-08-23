/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTest {

    /**
     * The settings screen refuses to save an untrusted duration longer than a trusted one, so the
     * defaults may not ship that way either. They did, which turned the "stay signed in" box into a
     * penalty: ticking it halved the session instead of extending it.
     */
    @Test
    @DisplayName("vouching for a device does not shorten the session")
    void defaultsKeepTrustedSessionsLongest() {
        var auth = new Auth();
        assertTrue(
                auth.sessionMinutes(true) >= auth.sessionMinutes(false),
                "a device somebody vouched for must not keep a session for less time than one nobody did");
    }

    @Test
    @DisplayName("an untrusted session cannot outlast a trusted one, however the config was written")
    void untrustedNeverOutlastsTrusted() {
        var auth = new Auth();
        setField(auth, "sessionMinutes", 60);
        setField(auth, "untrustedSessionMinutes", 43200);

        assertEquals(60, auth.untrustedSessionMinutes());
        assertEquals(60, auth.sessionMinutes(false));
    }

    @Test
    @DisplayName("both durations keep a floor of five minutes")
    void bothDurationsKeepAFloor() {
        var auth = new Auth();
        setField(auth, "sessionMinutes", 1);
        setField(auth, "untrustedSessionMinutes", 1);

        assertEquals(5, auth.sessionMinutes());
        assertEquals(5, auth.untrustedSessionMinutes());
    }

    private static void setField(Auth auth, String name, int value) {
        try {
            var field = Auth.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(auth, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + name, e);
        }
    }
}
