/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The timestamp services and their roots as the configuration file hands them over: blank entries left
 * out, addresses stripped, nothing at all when the keys are missing.
 */
class SigningTest {

    @Test
    void blankServiceEntriesAreLeftOutAndTheRestStripped() {
        var signing = new Signing();
        set(signing, "timestampUrls", Arrays.asList(" http://first.example ", "", "   ", "http://second.example"));

        assertEquals(List.of("http://first.example", "http://second.example"), signing.timestampUrls());
    }

    @Test
    void aMissingServiceListIsEmpty() {
        var signing = new Signing();
        set(signing, "timestampUrls", null);

        assertEquals(List.of(), signing.timestampUrls());
    }

    @Test
    void rootAddressesAreStrippedAndIncompleteEntriesLeftOut() {
        var signing = new Signing();
        var roots = new HashMap<String, String>();
        roots.put(" http://first.example ", "/etc/ember/first.pem");
        roots.put("http://second.example", null);
        set(signing, "timestampRoots", roots);

        assertEquals(Map.of("http://first.example", "/etc/ember/first.pem"), signing.timestampRoots());
    }

    @Test
    void missingRootsAreEmpty() {
        var signing = new Signing();
        set(signing, "timestampRoots", null);

        assertEquals(Map.of(), signing.timestampRoots());
        assertEquals(Map.of(), new Signing().timestampRoots());
    }

    private static void set(Signing signing, String name, Object value) {
        try {
            var field = Signing.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(signing, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + name, e);
        }
    }
}
