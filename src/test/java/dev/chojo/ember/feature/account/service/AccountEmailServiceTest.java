/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Writing an address onto somebody else's account.
 *
 * <p>The point is that it lands at once. Both callers that reach this, a guardian and an
 * administrator putting a wrong address right, are acting for somebody who cannot confirm from the
 * address being replaced.
 */
class AccountEmailServiceTest extends RepositoryTestBase {

    private static AccountEmailService service;
    private static Account account;
    private static Account other;

    @BeforeAll
    static void setup() {
        service = new AccountEmailService(
                accountRepo,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                mock(EmailService.class));
        account = accountRepo.create("wrong-address@test.com", "Anna", "Adresse");
        other = accountRepo.create("taken@test.com", "Otto", "Anders");
    }

    @AfterAll
    static void cleanup() {
        accountRepo.delete(account.id());
        accountRepo.delete(other.id());
    }

    @Test
    void theAddressLandsAtOnce() {
        assertTrue(service.setEmail(account.id(), "  Right-Address@Test.com "));

        assertEquals(
                "right-address@test.com",
                accountRepo.findById(account.id()).orElseThrow().email(),
                "trimmed and folded to lower case, which is how an address is compared");
    }

    @Test
    void theSameAddressAgainChangesNothing() {
        service.setEmail(account.id(), "settled@test.com");

        assertFalse(service.setEmail(account.id(), "SETTLED@test.com"), "nothing to write and nobody to tell");
    }

    @Test
    void anAddressThatIsNotOneIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(account.id(), "no-at-sign"));
    }

    @Test
    void anAddressAnotherAccountHasIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(account.id(), other.email()));
    }

    /** Shaped like an address, but nothing can be delivered to it, which is the whole objection. */
    @Test
    void aMadeUpAddressIsRefused() {
        assertEquals(
                AccountEmailService.AddressProblem.UNREACHABLE,
                service.problemWith(account.id(), "somebody@made.local"));
        assertThrows(BadRequestResponse.class, () -> service.setEmail(account.id(), "somebody@made.local"));
    }
}
