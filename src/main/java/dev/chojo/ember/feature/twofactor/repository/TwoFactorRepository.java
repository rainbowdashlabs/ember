/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.repository;

import dev.chojo.ember.feature.twofactor.entity.BackupCode;
import dev.chojo.ember.feature.twofactor.entity.TotpFactor;
import dev.chojo.ember.feature.twofactor.entity.TrustedDevice;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorAuditEntry;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy;
import dev.chojo.ember.feature.twofactor.entity.WebAuthnCredential;
import dev.chojo.ember.api.auth.StationUserType;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

@Singleton
public class TwoFactorRepository {

    // -- Factor CRUD --

    public TwoFactorFactor createFactor(int accountId, TwoFactorKind kind, String label) {
        return query("""
                INSERT INTO account_2fa_factor (account_id, kind, label)
                VALUES (:account_id, CAST(:kind AS two_factor_kind), :label)
                RETURNING *;""")
                .single(call().bind("account_id", accountId)
                        .bind("kind", kind.name())
                        .bind("label", label))
                .map(TwoFactorFactor.map())
                .first()
                .orElseThrow();
    }

    public List<TwoFactorFactor> findActiveFactors(int accountId) {
        return query("SELECT * FROM account_2fa_factor WHERE account_id = :account_id AND disabled_at IS NULL;")
                .single(call().bind("account_id", accountId))
                .map(TwoFactorFactor.map())
                .all();
    }

    public Optional<TwoFactorFactor> findActiveFactor(int accountId, TwoFactorKind kind) {
        return query("""
                SELECT * FROM account_2fa_factor
                WHERE account_id = :account_id AND kind = CAST(:kind AS two_factor_kind) AND disabled_at IS NULL;""")
                .single(call().bind("account_id", accountId).bind("kind", kind.name()))
                .map(TwoFactorFactor.map())
                .first();
    }

    public boolean disableFactor(int factorId) {
        return query("UPDATE account_2fa_factor SET disabled_at = now() WHERE id = :id AND disabled_at IS NULL;")
                .single(call().bind("id", factorId))
                .update()
                .changed();
    }

    public boolean touchFactorUsed(int factorId) {
        return query("UPDATE account_2fa_factor SET last_used_at = now() WHERE id = :id;")
                .single(call().bind("id", factorId))
                .update()
                .changed();
    }

    public boolean disableAllFactors(int accountId) {
        return query(
                        "UPDATE account_2fa_factor SET disabled_at = now() WHERE account_id = :account_id AND disabled_at IS NULL;")
                .single(call().bind("account_id", accountId))
                .update()
                .changed();
    }

    public boolean isEnrolled(int accountId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1 FROM account_2fa_factor
                    WHERE account_id = :account_id AND disabled_at IS NULL
                    AND kind != CAST('BACKUP_CODES' AS two_factor_kind)
                ) AS enrolled;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBoolean("enrolled"))
                .first()
                .orElse(false);
    }

    // -- TOTP --

    public void createTotp(
            int factorId, byte[] secretEncrypted, short kid, short digits, short periodSeconds, String algorithm) {
        query("""
                INSERT INTO account_2fa_totp (factor_id, secret_encrypted, secret_kid, digits, period_seconds, algorithm)
                VALUES (:factor_id, :secret, :kid, :digits, :period, :algorithm);""")
                .single(call().bind("factor_id", factorId)
                        .bind("secret", secretEncrypted)
                        .bind("kid", kid)
                        .bind("digits", digits)
                        .bind("period", periodSeconds)
                        .bind("algorithm", algorithm))
                .insert();
    }

    public Optional<TotpFactor> findTotp(int factorId) {
        return query("SELECT * FROM account_2fa_totp WHERE factor_id = :factor_id;")
                .single(call().bind("factor_id", factorId))
                .map(TotpFactor.map())
                .first();
    }

    // -- WebAuthn --

    public void createWebAuthn(
            int factorId,
            byte[] credentialId,
            byte[] publicKeyCose,
            long signatureCounter,
            UUID aaguid,
            List<String> transports,
            String attestationFormat,
            byte[] userHandle) {
        query("""
                INSERT INTO account_2fa_webauthn (
                    factor_id, credential_id, public_key_cose, signature_counter,
                    aaguid, transports, attestation_format, user_handle
                ) VALUES (
                    :factor_id, :credential_id, :public_key, :counter,
                    :aaguid, :transports, :attestation_format, :user_handle
                );""")
                .single(call().bind("factor_id", factorId)
                        .bind("credential_id", credentialId)
                        .bind("public_key", publicKeyCose)
                        .bind("counter", signatureCounter)
                        .bind("aaguid", aaguid, UUID_STRING)
                        .bind("transports", transports, PostgreSqlTypes.TEXT)
                        .bind("attestation_format", attestationFormat)
                        .bind("user_handle", userHandle))
                .insert();
    }

    public Optional<WebAuthnCredential> findWebAuthnByCredentialId(byte[] credentialId) {
        return query("SELECT * FROM account_2fa_webauthn WHERE credential_id = :credential_id;")
                .single(call().bind("credential_id", credentialId))
                .map(WebAuthnCredential.map())
                .first();
    }

    public Optional<WebAuthnCredential> findWebAuthnByFactor(int factorId) {
        return query("SELECT * FROM account_2fa_webauthn WHERE factor_id = :factor_id;")
                .single(call().bind("factor_id", factorId))
                .map(WebAuthnCredential.map())
                .first();
    }

    public List<WebAuthnCredential> findActiveWebAuthnForAccount(int accountId) {
        return query("""
                SELECT w.*
                FROM account_2fa_webauthn w
                JOIN account_2fa_factor f ON f.id = w.factor_id
                WHERE f.account_id = :account_id AND f.disabled_at IS NULL;""")
                .single(call().bind("account_id", accountId))
                .map(WebAuthnCredential.map())
                .all();
    }

    public boolean updateWebAuthnSignatureCounter(int factorId, long newCounter) {
        return query("""
                UPDATE account_2fa_webauthn
                SET signature_counter = :counter
                WHERE factor_id = :factor_id AND signature_counter < :counter;""")
                .single(call().bind("factor_id", factorId).bind("counter", newCounter))
                .update()
                .changed();
    }

    /**
     * Returns the stable 64-byte user handle reused across the account's WebAuthn credentials,
     * or empty when no credentials are registered yet (the caller mints one in that case).
     */
    public Optional<byte[]> findUserHandleForAccount(int accountId) {
        return query("""
                SELECT w.user_handle
                FROM account_2fa_webauthn w
                JOIN account_2fa_factor f ON f.id = w.factor_id
                WHERE f.account_id = :account_id
                ORDER BY f.created_at ASC
                LIMIT 1;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getBytes("user_handle"))
                .first();
    }

    public Optional<Integer> findAccountByUserHandle(byte[] userHandle) {
        return query("""
                SELECT DISTINCT f.account_id
                FROM account_2fa_webauthn w
                JOIN account_2fa_factor f ON f.id = w.factor_id
                WHERE w.user_handle = :user_handle
                LIMIT 1;""")
                .single(call().bind("user_handle", userHandle))
                .map(row -> row.getInt("account_id"))
                .first();
    }

    public boolean renameFactor(int factorId, int accountId, String label) {
        return query("""
                UPDATE account_2fa_factor SET label = :label
                WHERE id = :id AND account_id = :account_id AND disabled_at IS NULL;""")
                .single(call().bind("id", factorId)
                        .bind("account_id", accountId)
                        .bind("label", label))
                .update()
                .changed();
    }

    // -- Backup codes --

    public void createBackupCode(int factorId, String codeHash) {
        query("INSERT INTO account_2fa_backup_code (factor_id, code_hash) VALUES (:factor_id, :hash);")
                .single(call().bind("factor_id", factorId).bind("hash", codeHash))
                .insert();
    }

    public List<BackupCode> findUnusedBackupCodes(int factorId) {
        return query("SELECT * FROM account_2fa_backup_code WHERE factor_id = :factor_id AND used_at IS NULL;")
                .single(call().bind("factor_id", factorId))
                .map(BackupCode.map())
                .all();
    }

    public int countUnusedBackupCodes(int factorId) {
        return query(
                        "SELECT COUNT(*) AS cnt FROM account_2fa_backup_code WHERE factor_id = :factor_id AND used_at IS NULL;")
                .single(call().bind("factor_id", factorId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public boolean markBackupCodeUsed(int codeId, String ip) {
        return query(
                        "UPDATE account_2fa_backup_code SET used_at = now(), used_via_ip = CAST(:ip AS CIDR) WHERE id = :id AND used_at IS NULL;")
                .single(call().bind("id", codeId).bind("ip", ip))
                .update()
                .changed();
    }

    public void deleteBackupCodes(int factorId) {
        query("DELETE FROM account_2fa_backup_code WHERE factor_id = :factor_id;")
                .single(call().bind("factor_id", factorId))
                .delete();
    }

    public void markAllBackupCodesUsed(int accountId) {
        query("""
                UPDATE account_2fa_backup_code SET used_at = now()
                WHERE used_at IS NULL AND factor_id IN (
                    SELECT id FROM account_2fa_factor WHERE account_id = :account_id AND kind = CAST('BACKUP_CODES' AS two_factor_kind)
                );""").single(call().bind("account_id", accountId)).update();
    }

    // -- Trusted devices --

    public TrustedDevice createTrustedDevice(int accountId, String tokenHash, String userAgent, Instant trustedUntil) {
        return query("""
                INSERT INTO account_2fa_trusted_device (account_id, token_hash, user_agent, trusted_until)
                VALUES (:account_id, :token_hash, :user_agent, :trusted_until)
                RETURNING *;""")
                .single(call().bind("account_id", accountId)
                        .bind("token_hash", tokenHash)
                        .bind("user_agent", userAgent)
                        .bind("trusted_until", trustedUntil, INSTANT_TIMESTAMP))
                .map(TrustedDevice.map())
                .first()
                .orElseThrow();
    }

    public List<TrustedDevice> findActiveTrustedDevices(int accountId) {
        return query(
                        "SELECT * FROM account_2fa_trusted_device WHERE account_id = :account_id AND revoked_at IS NULL AND trusted_until > now();")
                .single(call().bind("account_id", accountId))
                .map(TrustedDevice.map())
                .all();
    }

    public boolean revokeTrustedDevice(int deviceId, int accountId) {
        return query(
                        "UPDATE account_2fa_trusted_device SET revoked_at = now() WHERE id = :id AND account_id = :account_id AND revoked_at IS NULL;")
                .single(call().bind("id", deviceId).bind("account_id", accountId))
                .update()
                .changed();
    }

    public void revokeAllTrustedDevices(int accountId) {
        query(
                        "UPDATE account_2fa_trusted_device SET revoked_at = now() WHERE account_id = :account_id AND revoked_at IS NULL;")
                .single(call().bind("account_id", accountId))
                .update();
    }

    // -- Policy --

    public List<TwoFactorPolicy> findInstancePolicies() {
        return query("SELECT * FROM two_factor_policy WHERE scope = 'INSTANCE' ORDER BY user_type NULLS FIRST;")
                .single(call())
                .map(TwoFactorPolicy.map())
                .all();
    }

    public List<TwoFactorPolicy> findStationPolicies(int stationId) {
        return query("""
                SELECT * FROM two_factor_policy
                WHERE scope = 'STATION' AND station_id = :station_id
                ORDER BY user_type NULLS FIRST;""")
                .single(call().bind("station_id", stationId))
                .map(TwoFactorPolicy.map())
                .all();
    }

    /**
     * Looks up the (scope, station, user-type) row. {@code stationId} must be {@code null} for
     * instance scope and set for station scope; {@code userType} may be {@code null} to mean
     * "all user types in this scope".
     */
    public Optional<TwoFactorPolicy> findPolicy(
            TwoFactorPolicy.Scope scope, Integer stationId, StationUserType userType) {
        return query("""
                SELECT * FROM two_factor_policy
                WHERE scope = CAST(:scope AS TEXT)
                  AND COALESCE(station_id, 0) = COALESCE(:station_id, 0)
                  AND COALESCE(user_type, '') = COALESCE(:user_type, '');""")
                .single(call().bind("scope", scope.name())
                        .bind("station_id", stationId)
                        .bind("user_type", userType == null ? null : userType.name()))
                .map(TwoFactorPolicy.map())
                .first();
    }

    /**
     * Inserts or updates a policy row keyed by (scope, station, user-type). Returns the live row.
     */
    public TwoFactorPolicy upsertPolicy(
            TwoFactorPolicy.Scope scope,
            Integer stationId,
            StationUserType userType,
            boolean required,
            short graceDays,
            Integer createdBy) {
        return query("""
                INSERT INTO two_factor_policy (scope, station_id, user_type, required, grace_days, created_by)
                VALUES (:scope, :station_id, :user_type, :required, :grace_days, :created_by)
                ON CONFLICT (COALESCE(station_id, 0), COALESCE(user_type, ''))
                DO UPDATE SET required = EXCLUDED.required,
                              grace_days = EXCLUDED.grace_days,
                              created_by = EXCLUDED.created_by,
                              created_at = now()
                RETURNING *;""")
                .single(call().bind("scope", scope.name())
                        .bind("station_id", stationId)
                        .bind("user_type", userType == null ? null : userType.name())
                        .bind("required", required)
                        .bind("grace_days", graceDays)
                        .bind("created_by", createdBy))
                .map(TwoFactorPolicy.map())
                .first()
                .orElseThrow();
    }

    public boolean deletePolicy(int id) {
        return query("DELETE FROM two_factor_policy WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Session 2FA timestamp --

    public boolean setTwoFactorVerified(int sessionId) {
        return query("UPDATE account_session SET two_factor_verified_at = now() WHERE id = :id;")
                .single(call().bind("id", sessionId))
                .update()
                .changed();
    }

    // -- Audit --

    public void audit(
            int accountId,
            Integer actorId,
            TwoFactorEvent event,
            TwoFactorKind factorKind,
            String userAgent,
            String country) {
        query("""
                INSERT INTO account_2fa_audit (account_id, actor_id, event, factor_kind, user_agent, country)
                VALUES (:account_id, :actor_id, CAST(:event AS two_factor_event),
                        CAST(:factor_kind AS two_factor_kind), :user_agent, :country);""")
                .single(call().bind("account_id", accountId)
                        .bind("actor_id", actorId)
                        .bind("event", event.name())
                        .bind("factor_kind", factorKind != null ? factorKind.name() : null)
                        .bind("user_agent", userAgent)
                        .bind("country", country))
                .insert();
    }

    public List<TwoFactorAuditEntry> findAuditLog(int accountId, int limit, int offset) {
        return query(
                        "SELECT * FROM account_2fa_audit WHERE account_id = :account_id ORDER BY created_at DESC LIMIT :limit OFFSET :offset;")
                .single(call().bind("account_id", accountId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(TwoFactorAuditEntry.map())
                .all();
    }
}
