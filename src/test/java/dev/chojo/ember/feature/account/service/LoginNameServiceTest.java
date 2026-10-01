/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a name somebody signs in with may look like. The at sign is the rule that matters: without
 * one, a name can never be mistaken for an address nor collide with one.
 */
class LoginNameServiceTest extends RepositoryTestBase {

    private static LoginNameService service;
    private static Account holder;
    private static Account other;

    @BeforeAll
    static void setup() {
        service = new LoginNameService(accountRepo);
        holder = accountRepo.create("name-holder@test.com", "Nina", "Name");
        other = accountRepo.create("name-other@test.com", "Otto", "Other");
        accountRepo.updateUsername(holder.id(), "nina.name");
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(holder.id());
        accountRepo.delete(other.id());
    }

    @Test
    void nothingGivenIsNoName() {
        assertNull(service.validated(null, other.id()));
        assertNull(service.validated("   ", other.id()));
    }

    @Test
    void aNameIsTrimmedAndKeptAsTyped() {
        assertEquals("Otto.Other", service.validated("  Otto.Other  ", other.id()));
    }

    @Test
    void anAddressIsNotAName() {
        assertRefused(Refusal.USERNAME_CHARACTERS_NOT_TAKEN, () -> service.validated("otto@test.com", other.id()));
    }

    @Test
    void tooShortAndTooLongAreRefused() {
        assertRefused(Refusal.USERNAME_LENGTH_NOT_TAKEN, () -> service.validated("ab", other.id()));
        assertRefused(Refusal.USERNAME_LENGTH_NOT_TAKEN, () -> service.validated("a".repeat(33), other.id()));
    }

    @Test
    void spacesAndPunctuationAreRefused() {
        assertRefused(Refusal.USERNAME_CHARACTERS_NOT_TAKEN, () -> service.validated("otto other", other.id()));
        assertRefused(Refusal.USERNAME_CHARACTERS_NOT_TAKEN, () -> service.validated("otto!", other.id()));
    }

    @Test
    void somebodyElsesNameIsRefusedWhateverItsCase() {
        assertRefused(Refusal.USERNAME_TAKEN, () -> service.validated("NINA.NAME", other.id()));
    }

    @Test
    void theirOwnNameIsTheirsToKeep() {
        assertEquals("nina.name", service.validated("nina.name", holder.id()));
    }

    @Test
    void anAccountWithNoAddressMayNotLoseItsName() {
        var noAddress = accountRepo.create("kid@managed.local", "Kim", "Kind");
        accountRepo.updateUsername(noAddress.id(), "kim.kind");
        var stored = accountRepo.findById(noAddress.id()).orElseThrow();

        assertRefused(Refusal.USERNAME_IS_THE_ONLY_WAY_IN, () -> service.validatedFor(stored, ""));

        accountRepo.delete(noAddress.id());
    }

    private static void assertRefused(Refusal expected, Executable call) {
        assertEquals(expected, assertThrows(RefusalResponse.class, call).refusal());
    }

    @Test
    void anAccountWithAnAddressMayLoseItsName() {
        var stored = accountRepo.findById(holder.id()).orElseThrow();

        assertNull(service.validatedFor(stored, ""));
    }

    @Test
    void whatWasTypedAtTheLoginScreenIsReadFromTheAtSign() {
        assertTrue(LoginNameService.looksLikeEmail("someone@example.org"));
        assertFalse(LoginNameService.looksLikeEmail("someone"));
    }
}
