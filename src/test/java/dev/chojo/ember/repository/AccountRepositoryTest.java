/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.roles.InstanceUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountExternalAuth;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountRepositoryTest extends RepositoryTestBase {
    private static int accountId;

    @Test
    @Order(1)
    void createAccount() {
        Account account = accountRepo.create("test@example.com", "Test", "User");
        assertNotNull(account);
        assertTrue(account.id() > 0);
        assertEquals("test@example.com", account.email());
        assertEquals("Test", account.firstName());
        assertEquals("User", account.lastName());
        assertFalse(account.emailVerified());
        accountId = account.id();
    }

    @Test
    @Order(2)
    void createAccountWithVerified() {
        Account account = accountRepo.create("verified@example.com", "Verified", "User", true);
        assertTrue(account.emailVerified());
    }

    @Test
    @Order(3)
    void findById() {
        assertTrue(accountRepo.findById(accountId).isPresent());
        assertTrue(accountRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findByEmail() {
        assertTrue(accountRepo.findByEmail("test@example.com").isPresent());
        assertTrue(accountRepo.findByEmail("nonexistent@example.com").isEmpty());
    }

    @Test
    @Order(6)
    void findAll() {
        assertTrue(accountRepo.findAll().size() >= 2);
    }

    @Test
    @Order(7)
    void update() {
        assertTrue(accountRepo.update(accountId, "updated@example.com", "Updated", "Name"));
        Account updated = accountRepo.findById(accountId).orElseThrow();
        assertEquals("updated@example.com", updated.email());
        assertEquals("Updated", updated.firstName());
        assertEquals("Name", updated.lastName());
        // restore
        accountRepo.update(accountId, "test@example.com", "Test", "User");
    }

    @Test
    @Order(8)
    void setEmailVerified() {
        assertTrue(accountRepo.setEmailVerified(accountId));
        assertTrue(accountRepo.findById(accountId).orElseThrow().emailVerified());
    }

    // -- Instance User Type --

    @Test
    @Order(10)
    void setAndCheckInstanceUserType() {
        accountRepo.setInstanceUserType(accountId, InstanceUserType.ADMINISTRATOR);
        assertTrue(accountRepo.isAdministrator(accountId));
    }

    @Test
    @Order(11)
    void isAdministratorFalseAfterReset() {
        accountRepo.setInstanceUserType(accountId, InstanceUserType.USER);
        assertFalse(accountRepo.isAdministrator(accountId));
    }

    // -- Credentials --

    @Test
    @Order(20)
    void createAndFindCredential() {
        accountRepo.createCredential(accountId, "{bcrypt:hash:salt}");
        AccountCredential cred = accountRepo.findCredential(accountId).orElseThrow();
        assertEquals(accountId, cred.accountId());
        assertEquals("{bcrypt:hash:salt}", cred.passwordHash());
        assertFalse(cred.forcePasswordChange());
    }

    @Test
    @Order(21)
    void updateCredential() {
        assertTrue(accountRepo.updateCredential(accountId, "{bcrypt:newhash:salt}"));
        assertEquals(
                "{bcrypt:newhash:salt}",
                accountRepo.findCredential(accountId).orElseThrow().passwordHash());
    }

    @Test
    @Order(22)
    void setForcePasswordChange() {
        assertTrue(accountRepo.setForcePasswordChange(accountId, true));
        assertTrue(accountRepo.findCredential(accountId).orElseThrow().forcePasswordChange());
        accountRepo.setForcePasswordChange(accountId, false);
    }

    @Test
    @Order(23)
    void deleteCredential() {
        assertTrue(accountRepo.deleteCredential(accountId));
        assertTrue(accountRepo.findCredential(accountId).isEmpty());
    }

    // -- External Auth --

    @Test
    @Order(30)
    void createAndFindExternalAuth() {
        accountRepo.createExternalAuth(accountId, "google", "ext123");
        var auths = accountRepo.findExternalAuths(accountId);
        assertEquals(1, auths.size());
        assertEquals("google", auths.getFirst().provider());
    }

    @Test
    @Order(31)
    void findExternalAuthByProviderAndId() {
        assertTrue(accountRepo.findExternalAuth("google", "ext123").isPresent());
        assertTrue(accountRepo.findExternalAuth("google", "nonexistent").isEmpty());
    }

    @Test
    @Order(32)
    void deleteExternalAuth() {
        AccountExternalAuth auth =
                accountRepo.findExternalAuth("google", "ext123").orElseThrow();
        assertTrue(accountRepo.deleteExternalAuth(auth.id()));
        assertTrue(accountRepo.findExternalAuths(accountId).isEmpty());
    }

    // -- Tokens --

    @Test
    @Order(40)
    void createAndFindToken() {
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createToken(accountId, "tok123", TokenType.VERIFY_EMAIL, expires);
        AccountToken token = accountRepo.findToken("tok123").orElseThrow();
        assertEquals(accountId, token.accountId());
        assertEquals(TokenType.VERIFY_EMAIL, token.tokenType());
        assertFalse(token.isExpired());
    }

    @Test
    @Order(41)
    void deleteToken() {
        assertTrue(accountRepo.deleteToken("tok123"));
        assertTrue(accountRepo.findToken("tok123").isEmpty());
    }

    @Test
    @Order(42)
    void deleteTokensByAccountAndType() {
        accountRepo.createToken(
                accountId, "tok-a", TokenType.RESET_PASSWORD, Instant.now().plus(1, ChronoUnit.HOURS));
        accountRepo.createToken(
                accountId, "tok-b", TokenType.RESET_PASSWORD, Instant.now().plus(1, ChronoUnit.HOURS));
        assertTrue(accountRepo.deleteTokensByAccountAndType(accountId, TokenType.RESET_PASSWORD));
        assertTrue(accountRepo.findToken("tok-a").isEmpty());
        assertTrue(accountRepo.findToken("tok-b").isEmpty());
    }

    @Test
    @Order(43)
    void deleteExpiredTokens() {
        accountRepo.createToken(
                accountId, "expired-tok", TokenType.VERIFY_EMAIL, Instant.now().minus(1, ChronoUnit.HOURS));
        assertTrue(accountRepo.deleteExpiredTokens());
        assertTrue(accountRepo.findToken("expired-tok").isEmpty());
    }

    // -- Sessions --

    @Test
    @Order(50)
    void createAndFindSession() {
        Instant expires = Instant.now().plus(30, ChronoUnit.MINUTES);
        accountRepo.createSession(accountId, "session-tok", expires, "TestAgent/1.0", null);
        AccountSession session = accountRepo.findSession("session-tok").orElseThrow();
        assertEquals(accountId, session.accountId());
        assertEquals("TestAgent/1.0", session.userAgent());
        assertFalse(session.isExpired());
    }

    @Test
    @Order(51)
    void findSessionsByAccount() {
        var sessions = accountRepo.findSessionsByAccount(accountId);
        assertFalse(sessions.isEmpty());
    }

    @Test
    @Order(52)
    void touchSession() {
        assertTrue(accountRepo.touchSession("session-tok", "UpdatedAgent/2.0", null));
        AccountSession session = accountRepo.findSession("session-tok").orElseThrow();
        assertEquals("UpdatedAgent/2.0", session.userAgent());
    }

    @Test
    @Order(53)
    void rotateSessionToken() {
        Instant newExpiry = Instant.now().plus(2, ChronoUnit.HOURS);
        assertTrue(accountRepo.rotateSessionToken("session-tok", "rotated-tok", newExpiry));
        assertTrue(accountRepo.findSession("session-tok").isEmpty());
        var rotated = accountRepo.findSession("rotated-tok");
        assertTrue(rotated.isPresent());
        assertEquals(accountId, rotated.get().accountId());
    }

    @Test
    @Order(54)
    void deleteSession() {
        assertTrue(accountRepo.deleteSession("rotated-tok"));
        assertTrue(accountRepo.findSession("rotated-tok").isEmpty());
    }

    @Test
    @Order(55)
    void deleteSessionsByAccount() {
        accountRepo.createSession(accountId, "s1", Instant.now().plus(1, ChronoUnit.HOURS), null, null);
        accountRepo.createSession(accountId, "s2", Instant.now().plus(1, ChronoUnit.HOURS), null, null);
        assertTrue(accountRepo.deleteSessionsByAccount(accountId));
        assertTrue(accountRepo.findSessionsByAccount(accountId).isEmpty());
    }

    @Test
    @Order(56)
    void deleteExpiredSessions() {
        accountRepo.createSession(accountId, "expired-s", Instant.now().minus(1, ChronoUnit.HOURS), null, null);
        assertTrue(accountRepo.deleteExpiredSessions());
        assertTrue(accountRepo.findSession("expired-s").isEmpty());
    }

    // -- Delete account --

    @Test
    @Order(99)
    void deleteAccount() {
        assertTrue(accountRepo.delete(accountId));
        assertTrue(accountRepo.findById(accountId).isEmpty());
    }
}
