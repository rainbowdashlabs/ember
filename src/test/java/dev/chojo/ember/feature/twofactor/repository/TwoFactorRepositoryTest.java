/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorRepositoryTest extends RepositoryTestBase {

    private int newAccountId(String suffix) {
        var account = accountRepo.create(
                "two-factor-" + suffix + "-" + UUID.randomUUID() + "@test.com", "Two", "Factor", true);
        return account.id();
    }

    @Test
    void factorLifecycle() {
        int accountId = newAccountId("factor");
        assertFalse(twoFactorRepo.isEnrolled(accountId));

        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.TOTP, "Authenticator");
        assertEquals(accountId, factor.accountId());
        assertEquals(TwoFactorKind.TOTP, factor.kind());
        assertTrue(twoFactorRepo.isEnrolled(accountId));
        assertEquals(1, twoFactorRepo.findActiveFactors(accountId).size());
        assertTrue(twoFactorRepo.findActiveFactor(accountId, TwoFactorKind.TOTP).isPresent());

        assertTrue(twoFactorRepo.touchFactorUsed(factor.id()));
        assertTrue(twoFactorRepo.renameFactor(factor.id(), accountId, "Renamed"));
        assertFalse(twoFactorRepo.renameFactor(99_999, accountId, "Renamed"));

        assertTrue(twoFactorRepo.disableFactor(factor.id()));
        assertFalse(twoFactorRepo.disableFactor(factor.id()), "disabling a disabled factor reports no-op");
        assertFalse(twoFactorRepo.isEnrolled(accountId));

        var second = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Key");
        assertTrue(twoFactorRepo.disableAllFactors(accountId));
        assertFalse(twoFactorRepo.disableAllFactors(accountId));
        assertTrue(twoFactorRepo
                .findActiveFactor(accountId, TwoFactorKind.WEBAUTHN)
                .isEmpty());
        // findWebAuthnByFactor for a non-WebAuthn factor returns empty
        assertTrue(twoFactorRepo.findWebAuthnByFactor(second.id()).isEmpty());
    }

    @Test
    void totpInsertAndRead() {
        int accountId = newAccountId("totp");
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.TOTP, "TOTP");
        byte[] encryptedSecret = "encrypted-secret-bytes".getBytes();
        twoFactorRepo.createTotp(factor.id(), encryptedSecret, (short) 1, (short) 6, (short) 30, "SHA1");
        var totp = twoFactorRepo.findTotp(factor.id()).orElseThrow();
        assertArrayEquals(encryptedSecret, totp.secretEncrypted());
        assertEquals(6, totp.digits());
        assertEquals("SHA1", totp.algorithm());
    }

    @Test
    void backupCodes() {
        int accountId = newAccountId("bc");
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.BACKUP_CODES, "Backup");
        twoFactorRepo.createBackupCode(factor.id(), "hash-1");
        twoFactorRepo.createBackupCode(factor.id(), "hash-2");

        assertEquals(2, twoFactorRepo.countUnusedBackupCodes(factor.id()));
        var unused = twoFactorRepo.findUnusedBackupCodes(factor.id());
        assertEquals(2, unused.size());

        // Markback by id with IPv4 (CIDR column requires a CIDR-shaped string)
        assertTrue(twoFactorRepo.markBackupCodeUsed(unused.getFirst().id(), "203.0.113.7"));
        assertFalse(
                twoFactorRepo.markBackupCodeUsed(unused.getFirst().id(), "203.0.113.7"), "second markUsed is a no-op");
        assertEquals(1, twoFactorRepo.countUnusedBackupCodes(factor.id()));

        // markAll wipes the remaining row
        twoFactorRepo.markAllBackupCodesUsed(accountId);
        assertEquals(0, twoFactorRepo.countUnusedBackupCodes(factor.id()));

        twoFactorRepo.deleteBackupCodes(factor.id());
        assertEquals(0, twoFactorRepo.findUnusedBackupCodes(factor.id()).size());
    }

    @Test
    void webAuthnCredentialRoundTrip() {
        int accountId = newAccountId("wa");
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.WEBAUTHN, "Yubikey");
        byte[] credentialId = new byte[] {1, 2, 3, 4};
        byte[] publicKey = new byte[] {5, 6, 7};
        byte[] userHandle = new byte[64];
        for (int i = 0; i < userHandle.length; i++) userHandle[i] = (byte) i;
        UUID aaguid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        twoFactorRepo.createWebAuthn(
                factor.id(),
                credentialId,
                publicKey,
                0,
                aaguid,
                List.of("usb", "nfc"),
                "packed",
                userHandle,
                false,
                true,
                null,
                false);

        var stored = twoFactorRepo.findWebAuthnByCredentialId(credentialId).orElseThrow();
        assertArrayEquals(credentialId, stored.credentialId());
        assertArrayEquals(publicKey, stored.publicKeyCose());
        assertEquals(aaguid, stored.aaguid());
        assertEquals(List.of("usb", "nfc"), stored.transports());
        assertEquals("packed", stored.attestationFormat());

        assertTrue(twoFactorRepo.findWebAuthnByFactor(factor.id()).isPresent());
        assertEquals(1, twoFactorRepo.findActiveWebAuthnForAccount(accountId).size());

        twoFactorRepo.updateWebAuthnSignatureCounter(factor.id(), 5);
        assertEquals(
                5L,
                twoFactorRepo.findWebAuthnByFactor(factor.id()).orElseThrow().signatureCounter());
        twoFactorRepo.updateWebAuthnSignatureCounter(factor.id(), 5);
        assertEquals(
                5L,
                twoFactorRepo.findWebAuthnByFactor(factor.id()).orElseThrow().signatureCounter(),
                "counter must strictly increase");
        twoFactorRepo.updateWebAuthnSignatureCounter(factor.id(), 1);
        assertEquals(
                5L,
                twoFactorRepo.findWebAuthnByFactor(factor.id()).orElseThrow().signatureCounter(),
                "lower counters must be rejected");

        assertArrayEquals(
                userHandle, twoFactorRepo.findUserHandleForAccount(accountId).orElseThrow());
        assertEquals(
                accountId, twoFactorRepo.findAccountByUserHandle(userHandle).orElseThrow());
        assertTrue(twoFactorRepo.findAccountByUserHandle(new byte[64]).isEmpty());
    }

    @Test
    void trustedDeviceLifecycle() {
        int accountId = newAccountId("td");
        Instant trustedUntil = Instant.now().plusSeconds(3600);
        var device = twoFactorRepo.createTrustedDevice(accountId, "hash-A", "ua-A", trustedUntil);
        twoFactorRepo.createTrustedDevice(accountId, "hash-B", "ua-B", trustedUntil);

        var lookup = twoFactorRepo.findTrustedDeviceByHash("hash-A").orElseThrow();
        assertEquals(device.id(), lookup.id());
        assertTrue(twoFactorRepo.touchTrustedDevice(device.id()));
        assertTrue(twoFactorRepo.findTrustedDeviceByHash("missing").isEmpty());

        assertEquals(2, twoFactorRepo.findActiveTrustedDevices(accountId).size());
        assertTrue(twoFactorRepo.revokeTrustedDevice(device.id(), accountId));
        assertFalse(twoFactorRepo.revokeTrustedDevice(device.id(), accountId), "re-revoke is a no-op");
        assertEquals(1, twoFactorRepo.findActiveTrustedDevices(accountId).size());

        twoFactorRepo.revokeAllTrustedDevices(accountId);
        assertEquals(0, twoFactorRepo.findActiveTrustedDevices(accountId).size());
    }

    @Test
    void policyUpsertAndDelete() {
        var instance = twoFactorRepo.upsertPolicy(
                TwoFactorPolicy.Scope.INSTANCE, null, StationUserType.MEMBER, true, (short) 7, null);
        assertEquals(TwoFactorPolicy.Scope.INSTANCE, instance.scope());
        assertTrue(instance.required());

        // Upsert again - should keep the same row but flip required to false
        var updated = twoFactorRepo.upsertPolicy(
                TwoFactorPolicy.Scope.INSTANCE, null, StationUserType.MEMBER, false, (short) 3, null);
        assertEquals(instance.id(), updated.id());
        assertFalse(updated.required());

        var instances = twoFactorRepo.findInstancePolicies();
        assertEquals(1, instances.size());

        // Station-scoped policy
        var station = stationRepo.create("policy-station");
        var stationPolicy = twoFactorRepo.upsertPolicy(
                TwoFactorPolicy.Scope.STATION, station.id(), StationUserType.MANAGER, true, (short) 5, null);
        assertEquals(1, twoFactorRepo.findStationPolicies(station.id()).size());
        assertTrue(twoFactorRepo
                .findPolicy(TwoFactorPolicy.Scope.STATION, station.id(), StationUserType.MANAGER)
                .isPresent());
        assertTrue(twoFactorRepo
                .findPolicy(TwoFactorPolicy.Scope.STATION, station.id(), StationUserType.MEMBER)
                .isEmpty());

        assertTrue(twoFactorRepo.deletePolicy(stationPolicy.id()));
        assertFalse(twoFactorRepo.deletePolicy(stationPolicy.id()));
        assertTrue(twoFactorRepo.deletePolicy(instance.id()));
    }

    @Test
    void sessionTwoFactorVerifiedMark() {
        int accountId = newAccountId("session");
        accountRepo.createSession(accountId, "session-bearer", Instant.now().plusSeconds(60), "ua", null);
        var session = accountRepo.findSession("session-bearer").orElseThrow();
        assertNull(session.twoFactorVerifiedAt());
        assertTrue(twoFactorRepo.setTwoFactorVerified(session.id()));
        var refreshed = accountRepo.findSession("session-bearer").orElseThrow();
        assertNotNull(refreshed.twoFactorVerifiedAt());
    }

    @Test
    void auditTrail() {
        int accountId = newAccountId("audit");
        twoFactorRepo.audit(accountId, null, TwoFactorEvent.ENROLLED, TwoFactorKind.TOTP, "ua", "DE");
        twoFactorRepo.audit(accountId, null, TwoFactorEvent.LOGIN_VERIFIED, TwoFactorKind.TOTP, "ua", "DE");
        twoFactorRepo.audit(accountId, null, TwoFactorEvent.TRUSTED_DEVICE_ADDED, null, "ua", "DE");

        var entries = twoFactorRepo.findAuditLog(accountId, 10, 0);
        assertEquals(3, entries.size());

        var recent = twoFactorRepo.findRecentAudit(2, 0);
        assertEquals(2, recent.size());
    }
}
