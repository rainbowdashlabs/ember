/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenHasherTest {

    private static final String PEPPER_A = "pepper-a-pepper-a-pepper-a-pepper-a";
    private static final String PEPPER_B = "pepper-b-pepper-b-pepper-b-pepper-b";

    @Test
    void outputIsSixtyFourLowercaseHexChars() {
        TokenHasher hasher = TokenHasher.forTesting(PEPPER_A);
        String hash = hasher.hash("some-session-token");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hashIsDeterministicForSameInputAndPepper() {
        TokenHasher first = TokenHasher.forTesting(PEPPER_A);
        TokenHasher second = TokenHasher.forTesting(PEPPER_A);
        assertEquals(first.hash("token-x"), second.hash("token-x"));
    }

    @Test
    void differentPeppersProduceDifferentHashes() {
        TokenHasher a = TokenHasher.forTesting(PEPPER_A);
        TokenHasher b = TokenHasher.forTesting(PEPPER_B);
        assertNotEquals(a.hash("same-token"), b.hash("same-token"));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        TokenHasher hasher = TokenHasher.forTesting(PEPPER_A);
        assertNotEquals(hasher.hash("token-one"), hasher.hash("token-two"));
    }

    @Test
    void productionBootRefusesBlankPepper() {
        Auth auth = newAuth("");
        Demo demo = newDemo(false, false);
        assertThrows(IllegalStateException.class, () -> new TokenHasher(auth, demo));
    }

    @Test
    void demoDevBootAllowsBlankPepper() {
        Auth auth = newAuth("");
        Demo demo = newDemo(true, false);
        TokenHasher hasher = new TokenHasher(auth, demo);
        assertEquals(64, hasher.hash("anything").length());
    }

    @Test
    void demoEnabledBootAllowsBlankPepper() {
        Auth auth = newAuth("");
        Demo demo = newDemo(false, true);
        TokenHasher hasher = new TokenHasher(auth, demo);
        assertEquals(64, hasher.hash("anything").length());
    }

    @Test
    void configuredPepperIsApplied() {
        Auth auth = newAuth(PEPPER_A);
        Demo demo = newDemo(false, false);
        TokenHasher prod = new TokenHasher(auth, demo);
        assertEquals(prod.hash("token-x"), TokenHasher.forTesting(PEPPER_A).hash("token-x"));
    }

    @Test
    void forTestingRefusesBlankPepper() {
        assertThrows(IllegalArgumentException.class, () -> TokenHasher.forTesting(""));
        assertThrows(IllegalArgumentException.class, () -> TokenHasher.forTesting("   "));
    }

    private static Auth newAuth(String pepper) {
        try {
            Auth auth = new Auth();
            Field field = Auth.class.getDeclaredField("tokenPepper");
            field.setAccessible(true);
            field.set(auth, pepper);
            return auth;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Demo newDemo(boolean dev, boolean enabled) {
        try {
            Demo demo = new Demo();
            Field devField = Demo.class.getDeclaredField("dev");
            devField.setAccessible(true);
            devField.set(demo, dev);
            Field enabledField = Demo.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(demo, enabled);
            return demo;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
