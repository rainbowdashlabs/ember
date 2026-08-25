/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a stranger may leave behind for somebody else's shell to read.
 *
 * The installer puts whatever comes back into its own environment, so the guard is not that the
 * page sends sensible things: it is that nothing else survives being stored.
 */
class InstallPresetServiceTest {

    private final InstallPresetService service = new InstallPresetService();

    @Test
    void whatWasStoredComesBackUnderItsCode() {
        String code = service.store(Map.of("EMBER_MODE", "port", "EMBER_PORT", "8080"));

        var found = service.find(code).orElseThrow();

        assertEquals("port", found.get("EMBER_MODE"));
        assertEquals("8080", found.get("EMBER_PORT"));
    }

    @Test
    void aCodeIsSixCharactersWithNothingEasilyMisread() {
        String code = service.store(Map.of("EMBER_MODE", "port"));

        assertEquals(6, code.length());
        assertTrue(code.matches("[23456789BCDFGHJKLMNPQRSTVWXZ]{6}"), "no vowels and no look-alikes: " + code);
    }

    /** Read out over the phone and typed back, a code arrives in whatever shape it arrives in. */
    @Test
    void aCodeIsFoundHoweverItWasTypedBack() {
        String code = service.store(Map.of("EMBER_MODE", "port"));

        assertTrue(service.find(code.toLowerCase()).isPresent(), "in lower case");
        assertTrue(service.find(" " + code + " ").isPresent(), "with spaces around it");
        assertTrue(service.find(code.substring(0, 3) + "-" + code.substring(3)).isPresent(), "split by a dash");
    }

    @Test
    void anAnswerTheInstallerDoesNotKnowIsNotKept() {
        var answers = new HashMap<String, String>();
        answers.put("EMBER_MODE", "port");
        answers.put("EMBER_SOMETHING", "rm -rf /");
        answers.put("PATH", "/tmp/evil");

        var found = service.find(service.store(answers)).orElseThrow();

        assertEquals(1, found.size());
        assertTrue(found.containsKey("EMBER_MODE"));
    }

    /**
     * The value ends up on the right of an assignment the installer reads into itself, so anything
     * a shell would take as more than a value has no business being kept.
     */
    @Test
    void aValueThatWouldBeReadAsMoreThanAValueIsNotKept() {
        var answers = new HashMap<String, String>();
        answers.put("EMBER_HOST", "example.org; rm -rf /");
        answers.put("EMBER_TAG", "$(whoami)");
        answers.put("EMBER_MODE", "port");

        var found = service.find(service.store(answers)).orElseThrow();

        assertFalse(found.containsKey("EMBER_HOST"), "a semicolon starts a second command");
        assertFalse(found.containsKey("EMBER_TAG"), "a substitution runs something");
        assertEquals("port", found.get("EMBER_MODE"), "and the sound one is still there");
    }

    @Test
    void anEmptyAnswerIsNotKept() {
        var answers = new HashMap<String, String>();
        answers.put("EMBER_MODE", "port");
        answers.put("EMBER_HOST", "  ");

        var found = service.find(service.store(answers)).orElseThrow();

        assertFalse(found.containsKey("EMBER_HOST"));
    }

    @Test
    void aCodeNobodyMadeIsNotFound() {
        assertTrue(service.find("ZZZZZZ").isEmpty());
        assertTrue(service.find(null).isEmpty());
    }

    @Test
    void twoPresetsDoNotShareACode() {
        String first = service.store(Map.of("EMBER_MODE", "port"));
        String second = service.store(Map.of("EMBER_MODE", "traefik"));

        assertEquals("port", service.find(first).orElseThrow().get("EMBER_MODE"));
        assertEquals("traefik", service.find(second).orElseThrow().get("EMBER_MODE"));
    }

    @Test
    void theLifetimeIsSaidSoThePageCanShowIt() {
        assertTrue(service.lifetime().toHours() > 0);
    }

    /**
     * A six-character code is short enough to hunt for. Typing one in takes a lookup or two; going
     * through the space takes far more than an address is given.
     */
    @Test
    void lookupsFromOneAddressRunOut() {
        for (int i = 0; i < InstallPresetService.LOOKUP_CAPACITY; i++) {
            assertTrue(service.tryLookup("198.51.100.7").isEmpty(), "lookup " + i);
        }

        var retry = service.tryLookup("198.51.100.7");
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
        assertTrue(service.tryLookup("203.0.113.9").isEmpty());
    }
}
