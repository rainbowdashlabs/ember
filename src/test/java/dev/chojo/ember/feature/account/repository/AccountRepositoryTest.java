/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountTies;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
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
    @Order(3)
    void findByUidAndResolveUid() {
        var account = accountRepo.findById(accountId).orElseThrow();
        var found = accountRepo.findByUid(account.uid()).orElseThrow();
        assertEquals(accountId, found.id());
        assertTrue(accountRepo.findByUid(UUID.randomUUID()).isEmpty());

        assertEquals(account.uid(), accountRepo.resolveUid(accountId));
        assertNull(accountRepo.resolveUid(99999));
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
    @Order(6)
    void searchForPickerEmptyReturnsRecent() {
        var rows = accountRepo.searchForPicker(null, 10);
        assertFalse(rows.isEmpty());
        var blank = accountRepo.searchForPicker("   ", 10);
        assertFalse(blank.isEmpty());
    }

    @Test
    @Order(6)
    void searchForPickerByFirstName() {
        var rows = accountRepo.searchForPicker("Test", 10);
        assertTrue(rows.stream().anyMatch(r -> "test@example.com".equals(r.email())));
    }

    @Test
    @Order(6)
    void searchForPickerByLastName() {
        var rows = accountRepo.searchForPicker("User", 10);
        assertTrue(rows.stream().anyMatch(r -> "test@example.com".equals(r.email())));
    }

    @Test
    @Order(6)
    void searchForPickerByEmailPartial() {
        var rows = accountRepo.searchForPicker("test@", 10);
        assertTrue(rows.stream().anyMatch(r -> "test@example.com".equals(r.email())));
    }

    @Test
    @Order(6)
    void searchForPickerNoMatches() {
        var rows = accountRepo.searchForPicker("xxx-no-such-account-zzz", 10);
        assertTrue(rows.isEmpty());
    }

    @Test
    @Order(6)
    void findPickerByUid() {
        var account = accountRepo.findById(accountId).orElseThrow();
        var picker = accountRepo.findPickerByUid(account.uid()).orElseThrow();
        assertEquals(account.id(), picker.id());
        assertEquals(account.email(), picker.email());
        assertTrue(accountRepo.findPickerByUid(UUID.randomUUID()).isEmpty());
    }

    @Test
    @Order(7)
    void update() {
        assertTrue(accountRepo.update(accountId, "updated@example.com", "Updated", "Name"));
        Account updated = accountRepo.findById(accountId).orElseThrow();
        assertEquals("updated@example.com", updated.email());
        assertEquals("Updated", updated.firstName());
        assertEquals("Name", updated.lastName());
        accountRepo.update(accountId, "test@example.com", "Test", "User");
    }

    @Test
    @Order(8)
    void setEmailVerified() {
        assertTrue(accountRepo.setEmailVerified(accountId));
        assertTrue(accountRepo.findById(accountId).orElseThrow().emailVerified());
    }

    @Test
    @Order(10)
    void setAndCheckInstanceUserType() {
        accountRepo.setInstanceUserType(accountId, InstanceUserType.ADMINISTRATOR);
        assertEquals(
                InstanceUserType.ADMINISTRATOR,
                accountRepo.findById(accountId).orElseThrow().instanceUserType());
        assertTrue(accountRepo.anyAdministratorExists());
    }

    @Test
    @Order(11)
    void instanceUserTypeResets() {
        accountRepo.setInstanceUserType(accountId, InstanceUserType.USER);
        assertEquals(
                InstanceUserType.USER,
                accountRepo.findById(accountId).orElseThrow().instanceUserType());
    }

    /** Only proves the query runs: accounts of other test classes may still be administrators. */
    @Test
    @Order(12)
    void anyAdministratorExistsCallable() {
        var result = accountRepo.anyAdministratorExists();
    }

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
    @Order(22)
    void updateLastBreachCheckPwnedForcesPasswordChange() {
        Instant when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertTrue(accountRepo.updateLastBreachCheck(accountId, when, true));
        var cred = accountRepo.findCredential(accountId).orElseThrow();
        assertTrue(cred.forcePasswordChange());
        accountRepo.setForcePasswordChange(accountId, false);
    }

    @Test
    @Order(22)
    void updateLastBreachCheckCleanLeavesFlagAlone() {
        Instant when = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        accountRepo.setForcePasswordChange(accountId, false);
        assertTrue(accountRepo.updateLastBreachCheck(accountId, when, false));
        var cred = accountRepo.findCredential(accountId).orElseThrow();
        assertFalse(cred.forcePasswordChange());
    }

    @Test
    @Order(23)
    void deleteCredential() {
        assertTrue(accountRepo.deleteCredential(accountId));
        assertTrue(accountRepo.findCredential(accountId).isEmpty());
    }

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
    @Order(40)
    void storedTokenIsHashed() {
        String raw = "raw-token-must-never-be-stored";
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createToken(accountId, raw, TokenType.VERIFY_EMAIL, expires);
        AccountToken stored = accountRepo.findToken(raw).orElseThrow();
        assertNotEquals(raw, stored.tokenHash());
        String expected = TokenHasher.forTesting("repository-test-pepper").hash(raw);
        assertEquals(expected, stored.tokenHash());
        accountRepo.deleteToken(raw);
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
    @Order(50)
    void storedSessionBearerIsHashed() {
        String raw = "raw-bearer-must-never-be-stored";
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createSession(accountId, raw, expires, "ua", null);
        AccountSession stored = accountRepo.findSession(raw).orElseThrow();
        assertNotEquals(raw, stored.tokenHash());
        String expected = TokenHasher.forTesting("repository-test-pepper").hash(raw);
        assertEquals(expected, stored.tokenHash());
        accountRepo.deleteSession(raw);
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
    @Order(52)
    void touchSessionWritesOncePerMinute() {
        accountRepo.createSession(accountId, "touch-tok", Instant.now().plus(1, ChronoUnit.HOURS), "Agent/1", "DE");

        assertFalse(accountRepo.touchSession("touch-tok", "Agent/1", "DE"));
        assertFalse(accountRepo.touchSession("touch-tok", "Agent/1", null));

        backdateLastUse("touch-tok");
        assertTrue(accountRepo.touchSession("touch-tok", "Agent/1", "DE"));
        assertFalse(accountRepo.touchSession("touch-tok", "Agent/1", "DE"));

        accountRepo.deleteSession("touch-tok");
    }

    @Test
    @Order(52)
    void touchSessionWritesAChangedUserAgentOrLocationAtOnce() {
        accountRepo.createSession(accountId, "change-tok", Instant.now().plus(1, ChronoUnit.HOURS), "Agent/1", "DE");

        assertTrue(accountRepo.touchSession("change-tok", "Agent/2", "DE"));
        assertEquals(
                "Agent/2", accountRepo.findSession("change-tok").orElseThrow().userAgent());

        assertTrue(accountRepo.touchSession("change-tok", "Agent/2", "AT"));
        assertEquals("AT", accountRepo.findSession("change-tok").orElseThrow().location());

        accountRepo.deleteSession("change-tok");
    }

    @Test
    @Order(52)
    void renewSessionWaitsForHalfTheLifetime() {
        accountRepo.createSession(accountId, "renew-fresh", Instant.now().plus(50, ChronoUnit.MINUTES), "ua", null);

        assertTrue(accountRepo.renewSession("renew-fresh", 60, 600).isEmpty());

        accountRepo.deleteSession("renew-fresh");
    }

    @Test
    @Order(52)
    void renewSessionExtendsByTheLifetimeItWasSignedInWith() {
        accountRepo.createSession(accountId, "renew-short", Instant.now().plus(10, ChronoUnit.MINUTES), "ua", null);
        accountRepo.createSession(
                accountId, "renew-long", Instant.now().plus(10, ChronoUnit.MINUTES), "ua", null, null, null, true);

        Instant shortExpiry = accountRepo.renewSession("renew-short", 60, 600).orElseThrow();
        Instant longExpiry = accountRepo.renewSession("renew-long", 60, 600).orElseThrow();

        assertTrue(shortExpiry.isAfter(Instant.now().plus(55, ChronoUnit.MINUTES)));
        assertTrue(shortExpiry.isBefore(Instant.now().plus(65, ChronoUnit.MINUTES)));
        assertTrue(longExpiry.isAfter(Instant.now().plus(595, ChronoUnit.MINUTES)));
        assertEquals(
                shortExpiry,
                accountRepo.findSession("renew-short").orElseThrow().expiresAt());

        accountRepo.deleteSession("renew-short");
        accountRepo.deleteSession("renew-long");
    }

    @Test
    @Order(52)
    void renewSessionNeverRevivesAnExpiredOne() {
        accountRepo.createSession(accountId, "renew-dead", Instant.now().minus(1, ChronoUnit.MINUTES), "ua", null);

        assertTrue(accountRepo.renewSession("renew-dead", 60, 600).isEmpty());
        assertTrue(accountRepo.renewSession("renew-unknown", 60, 600).isEmpty());

        accountRepo.deleteSession("renew-dead");
    }

    private static void backdateLastUse(String token) {
        query("UPDATE account_session SET last_used_at = now() - INTERVAL '2 minutes' WHERE token_hash = :token_hash;")
                .single(call().bind(
                                "token_hash",
                                TokenHasher.forTesting("repository-test-pepper").hash(token)))
                .update();
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
    @Order(55)
    void deleteSessionsExceptToken() {
        Instant exp = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createSession(accountId, "keep-tok", exp, null, null);
        accountRepo.createSession(accountId, "drop-tok-a", exp, null, null);
        accountRepo.createSession(accountId, "drop-tok-b", exp, null, null);
        assertTrue(accountRepo.deleteSessionsExceptToken(accountId, "keep-tok"));
        assertTrue(accountRepo.findSession("keep-tok").isPresent());
        assertTrue(accountRepo.findSession("drop-tok-a").isEmpty());
        assertTrue(accountRepo.findSession("drop-tok-b").isEmpty());
        accountRepo.deleteSession("keep-tok");
    }

    @Test
    @Order(56)
    void deleteSessionById() {
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createSession(accountId, "delete-by-id-tok", expires, "Agent", null);
        var session = accountRepo.findSession("delete-by-id-tok").orElseThrow();
        assertTrue(accountRepo.deleteSessionById(session.id(), accountId));
        assertTrue(accountRepo.findSession("delete-by-id-tok").isEmpty());
    }

    @Test
    @Order(56)
    void deleteSessionByIdWrongAccount() {
        Instant expires = Instant.now().plus(1, ChronoUnit.HOURS);
        accountRepo.createSession(accountId, "wrong-account-tok", expires, "Agent", null);
        var session = accountRepo.findSession("wrong-account-tok").orElseThrow();
        assertFalse(accountRepo.deleteSessionById(session.id(), 99999));
        accountRepo.deleteSession("wrong-account-tok");
    }

    @Test
    @Order(57)
    void deleteExpiredSessions() {
        accountRepo.createSession(accountId, "expired-s", Instant.now().minus(1, ChronoUnit.HOURS), null, null);
        assertTrue(accountRepo.deleteExpiredSessions());
        assertTrue(accountRepo.findSession("expired-s").isEmpty());
    }

    @Test
    @Order(70)
    void findLatestConsentEmpty() {
        assertTrue(accountRepo.findLatestConsent(accountId).isEmpty());
    }

    @Test
    @Order(71)
    void recordAndFindLatestConsent() {
        accountRepo.recordConsent(accountId, "1.0", "1.0", "1.0", "127.0.0.1", "DE", "TestAgent");
        accountRepo.recordConsent(accountId, "1.1", "1.1", "1.1", "127.0.0.1", "DE", "TestAgent");
        assertEquals(
                "1.1", accountRepo.findLatestConsent(accountId).orElseThrow().consentVersion());
    }

    @Test
    @Order(80)
    void aOneTimePasswordDemandsAChangeAndAChosenPasswordClearsItsDeadline() {
        var fresh = accountRepo.create("otp-repo-" + System.nanoTime() + "@test.com", "O", "T", true);
        Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        accountRepo.setOneTimePassword(fresh.id(), "{otp}", deadline);
        var issued = accountRepo.findCredential(fresh.id()).orElseThrow();
        assertEquals("{otp}", issued.passwordHash());
        assertTrue(issued.forcePasswordChange());
        assertEquals(deadline, issued.oneTimePasswordExpiresAt());

        accountRepo.setPasswordLoginDisabled(fresh.id(), true);
        accountRepo.setOneTimePassword(fresh.id(), "{again}", deadline);
        var again = accountRepo.findCredential(fresh.id()).orElseThrow();
        assertEquals("{again}", again.passwordHash());
        assertTrue(again.passwordLoginEnabled(), "a one-time password switches password sign-in back on");

        accountRepo.updateCredential(fresh.id(), "{chosen}");
        var chosen = accountRepo.findCredential(fresh.id()).orElseThrow();
        assertFalse(chosen.forcePasswordChange());
        assertNull(chosen.oneTimePasswordExpiresAt());
        accountRepo.delete(fresh.id());
    }

    @Test
    @Order(81)
    void tiesNameAnyOtherStationFormerOrNotAndAnyAssociation() {
        var fresh = accountRepo.create("ties-" + System.nanoTime() + "@test.com", "T", "I", true);
        var home = stationRepo.create("Ties home " + System.nanoTime());
        var other = stationRepo.create("Ties other " + System.nanoTime());
        stationMemberRepo.create(home.id(), fresh.id());
        assertEquals(new AccountTies(false, false), accountRepo.findTies(fresh.id(), home.id()));

        var elsewhere = stationMemberRepo.create(other.id(), fresh.id());
        stationMemberRepo.setFormer(elsewhere.id(), true);
        assertEquals(new AccountTies(true, false), accountRepo.findTies(fresh.id(), home.id()));

        var association = clusterRepo.create("Ties association " + System.nanoTime(), null, home.id());
        clusterRepo.addMember(association.id(), fresh.id(), ClusterUserType.CLUSTER_USER);
        assertEquals(new AccountTies(true, true), accountRepo.findTies(fresh.id(), home.id()));
        accountRepo.delete(fresh.id());
    }

    @Test
    @Order(82)
    void anAccountWaitsForItsOwnerUntilConfirmedOnce() {
        var fresh = accountRepo.create("unconfirmed-" + System.nanoTime() + "@test.com", "U", "C", true);
        assertFalse(accountRepo.isUnconfirmed(fresh.id()), "an account made any other way never waits");
        assertFalse(accountRepo.confirm(fresh.id()), "confirming an account that never waited changes nothing");

        accountRepo.markUnconfirmed(fresh.id());
        assertTrue(accountRepo.isUnconfirmed(fresh.id()));

        assertTrue(accountRepo.confirm(fresh.id()));
        assertFalse(accountRepo.isUnconfirmed(fresh.id()));
        assertFalse(accountRepo.confirm(fresh.id()));
        accountRepo.delete(fresh.id());
    }

    @Test
    @Order(99)
    void deleteAccount() {
        assertTrue(accountRepo.delete(accountId));
        assertTrue(accountRepo.findById(accountId).isEmpty());
    }
}
