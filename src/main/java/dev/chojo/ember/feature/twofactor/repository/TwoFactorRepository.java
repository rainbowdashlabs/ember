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
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

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
