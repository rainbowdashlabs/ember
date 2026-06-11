/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.api.roles.InstanceUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountExternalAuth;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for account-related database operations including accounts, credentials,
 * external auth providers, tokens, sessions, and GDPR consent records.
 */
@Singleton
public class AccountRepository {

    private static final String ACCOUNT_COLUMNS =
            "id, email, first_name, last_name, email_verified, instance_user_type, full_name";
    private static final String CONSENT_COLUMNS =
            "id, account_id, consent_version, privacy_version, tos_version, ip_address, country, user_agent, consented_at";

    /**
     * Finds an account by its unique identifier.
     *
     * @param id the account identifier
     * @return the account, or empty if not found
     */
    public Optional<Account> findById(int id) {
        return query("SELECT %s FROM account WHERE id = :id;", ACCOUNT_COLUMNS)
                .single(call().bind("id", id))
                .map(Account.map())
                .first();
    }

    /**
     * Finds an account by its email address.
     *
     * @param email the email address
     * @return the account, or empty if not found
     */
    public Optional<Account> findByEmail(String email) {
        return query("SELECT %s FROM account WHERE email = :email;", ACCOUNT_COLUMNS)
                .single(call().bind("email", email))
                .map(Account.map())
                .first();
    }

    /**
     * Retrieves all accounts.
     *
     * @return list of all accounts
     */
    public List<Account> findAll() {
        return query("SELECT %s FROM account;", ACCOUNT_COLUMNS)
                .single()
                .map(Account.map())
                .all();
    }

    /**
     * Creates a new account with email unverified by default.
     *
     * @param email     the email address
     * @param firstName the first name
     * @param lastName  the last name
     * @return the created account
     */
    public Account create(String email, String firstName, String lastName) {
        return query("""
                INSERT
                        INTO
                            account(email, first_name, last_name)
                        VALUES
                            (:email, :first_name, :last_name)
                        RETURNING %s;""", ACCOUNT_COLUMNS)
                .single(call().bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName))
                .map(Account.map())
                .first()
                .orElseThrow();
    }

    /**
     * Creates a new account with an explicit email verification status.
     *
     * @param email         the email address
     * @param firstName     the first name
     * @param lastName      the last name
     * @param emailVerified whether the email should be marked as already verified
     * @return the created account
     */
    public Account create(String email, String firstName, String lastName, boolean emailVerified) {
        return query("""
                INSERT
                INTO
                    account(email, first_name, last_name, email_verified)
                VALUES
                    (:email, :first_name, :last_name, :verified)
                RETURNING %s;""", ACCOUNT_COLUMNS)
                .single(call().bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("verified", emailVerified))
                .map(Account.map())
                .first()
                .orElseThrow();
    }

    // -- Instance User Type --

    /**
     * Checks whether an account is an instance administrator.
     */
    public boolean isAdministrator(int accountId) {
        return query("SELECT 1 FROM account WHERE id = :id AND instance_user_type = 'ADMINISTRATOR';")
                .single(call().bind("id", accountId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    /**
     * Checks whether any account in the system is an administrator.
     */
    public boolean anyAdministratorExists() {
        return query("SELECT 1 FROM account WHERE instance_user_type = 'ADMINISTRATOR' LIMIT 1;")
                .single()
                .map(row -> true)
                .first()
                .isPresent();
    }

    /**
     * Sets the instance user type for an account.
     */
    public boolean setInstanceUserType(int accountId, InstanceUserType userType) {
        return query("UPDATE account SET instance_user_type = :user_type WHERE id = :id;")
                .single(call().bind("user_type", userType).bind("id", accountId))
                .update()
                .changed();
    }

    /**
     * Updates account information (email, first name, last name).
     *
     * @param id        the account identifier
     * @param email     the new email address
     * @param firstName the new first name
     * @param lastName  the new last name
     * @return {@code true} if the account was updated
     */
    public boolean update(int id, String email, String firstName, String lastName) {
        return query("""
                UPDATE account
                SET
                    email      = :email,
                    first_name = :first_name,
                    last_name  = :last_name
                WHERE id = :id;""")
                .single(call().bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates only the email address of an account.
     *
     * @param id    the account identifier
     * @param email the new email address
     * @return {@code true} if the account was updated
     */
    public boolean updateEmail(int id, String email) {
        return query("UPDATE account SET email = :email WHERE id = :id;")
                .single(call().bind("email", email).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Marks an account's email as verified.
     *
     * @param id the account identifier
     * @return {@code true} if the account was updated
     */
    public boolean setEmailVerified(int id) {
        return query("""
                UPDATE account SET email_verified = TRUE WHERE id = :id;""").single(call().bind("id", id)).update().changed();
    }

    // -- Credentials --

    /**
     * Deletes an account by its identifier.
     *
     * @param id the account identifier
     * @return {@code true} if the account was deleted
     */
    public boolean delete(int id) {
        return query("""
                DELETE FROM account WHERE id = :id;""").single(call().bind("id", id)).delete().changed();
    }

    /**
     * Finds the password credential for an account.
     *
     * @param accountId the account identifier
     * @return the credential, or empty if none exists
     */
    public Optional<AccountCredential> findCredential(int accountId) {
        return query("""
                SELECT
                    account_id,
                    password_hash,
                    force_password_change
                FROM
                    account_credential
                WHERE account_id = :id;""")
                .single(call().bind("id", accountId))
                .map(AccountCredential.map())
                .first();
    }

    /**
     * Creates a password credential for an account.
     *
     * @param accountId    the account identifier
     * @param passwordHash the hashed password
     * @return the insertion result
     */
    public InsertionResult createCredential(int accountId, String passwordHash) {
        return query("""
                INSERT INTO account_credential(account_id, password_hash) VALUES(:account_id, :hash);""")
                .single(call().bind("account_id", accountId).bind("hash", passwordHash))
                .insert();
    }

    /**
     * Updates the password hash for an account and clears the force-password-change flag.
     *
     * @param accountId    the account identifier
     * @param passwordHash the new hashed password
     * @return {@code true} if the credential was updated
     */
    public boolean updateCredential(int accountId, String passwordHash) {
        return query("""
                UPDATE account_credential
                SET
                    password_hash         = :hash,
                    force_password_change = FALSE
                WHERE account_id = :id;""")
                .single(call().bind("hash", passwordHash).bind("id", accountId))
                .update()
                .changed();
    }

    /**
     * Sets or clears the force-password-change flag on an account's credential.
     *
     * @param accountId the account identifier
     * @param force     {@code true} to force a password change on next login
     * @return {@code true} if the credential was updated
     */
    public boolean setForcePasswordChange(int accountId, boolean force) {
        return query("UPDATE account_credential SET force_password_change = :force WHERE account_id = :id;")
                .single(call().bind("force", force).bind("id", accountId))
                .update()
                .changed();
    }

    // -- External Auth --

    /**
     * Deletes the password credential for an account.
     *
     * @param accountId the account identifier
     * @return {@code true} if a credential was deleted
     */
    public boolean deleteCredential(int accountId) {
        return query("DELETE FROM account_credential WHERE account_id = :id;")
                .single(call().bind("id", accountId))
                .delete()
                .changed();
    }

    /**
     * Retrieves all external authentication links for an account.
     *
     * @param accountId the account identifier
     * @return list of external auth records
     */
    public List<AccountExternalAuth> findExternalAuths(int accountId) {
        return query("SELECT id, account_id, provider, external_id FROM account_external_auth WHERE account_id = :id;")
                .single(call().bind("id", accountId))
                .map(AccountExternalAuth.map())
                .all();
    }

    /**
     * Finds an external authentication record by provider and external ID.
     *
     * @param provider   the provider name
     * @param externalId the external user identifier
     * @return the external auth record, or empty if not found
     */
    public Optional<AccountExternalAuth> findExternalAuth(String provider, String externalId) {
        return query("""
                SELECT
                    id,
                    account_id,
                    provider,
                    external_id
                FROM
                    account_external_auth
                WHERE provider = :provider
                  AND external_id = :external_id;""")
                .single(call().bind("provider", provider).bind("external_id", externalId))
                .map(AccountExternalAuth.map())
                .first();
    }

    /**
     * Creates a new external authentication link for an account.
     *
     * @param accountId  the account identifier
     * @param provider   the provider name
     * @param externalId the external user identifier
     * @return the insertion result
     */
    public InsertionResult createExternalAuth(int accountId, String provider, String externalId) {
        return query("""
                INSERT
                INTO
                    account_external_auth(account_id, provider, external_id)
                VALUES
                    (:account_id, :provider, :external_id);""")
                .single(call().bind("account_id", accountId)
                        .bind("provider", provider)
                        .bind("external_id", externalId))
                .insert();
    }

    // -- Tokens --

    /**
     * Deletes an external authentication record by its identifier.
     *
     * @param id the external auth record identifier
     * @return {@code true} if the record was deleted
     */
    public boolean deleteExternalAuth(int id) {
        return query("DELETE FROM account_external_auth WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Finds a token by its token string.
     *
     * @param token the token string
     * @return the account token, or empty if not found
     */
    public Optional<AccountToken> findToken(String token) {
        return query("""
                SELECT
                    id,
                    account_id,
                    token,
                    token_type,
                    metadata,
                    expires_at,
                    created_at
                FROM
                    account_token
                WHERE token = :token;""")
                .single(call().bind("token", token))
                .map(AccountToken.map())
                .first();
    }

    /**
     * Creates a token without metadata.
     *
     * @param accountId the account identifier
     * @param token     the token string
     * @param tokenType the type of token
     * @param expiresAt when the token expires
     * @return the insertion result
     */
    public InsertionResult createToken(int accountId, String token, TokenType tokenType, Instant expiresAt) {
        return createToken(accountId, token, tokenType, null, expiresAt);
    }

    /**
     * Creates a token with optional metadata.
     *
     * @param accountId the account identifier
     * @param token     the token string
     * @param tokenType the type of token
     * @param metadata  optional metadata (e.g. new email for email change, station ID for deletion)
     * @param expiresAt when the token expires
     * @return the insertion result
     */
    public InsertionResult createToken(
            int accountId, String token, TokenType tokenType, String metadata, Instant expiresAt) {
        return query("""
                INSERT
                        INTO
                            account_token(account_id, token, token_type, metadata, expires_at)
                        VALUES
                            (:account_id, :token, :type, :metadata, :expires_at);""")
                .single(call().bind("account_id", accountId)
                        .bind("token", token)
                        .bind("type", tokenType)
                        .bind("metadata", metadata)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Deletes a token by its token string.
     *
     * @param token the token string
     * @return {@code true} if a token was deleted
     */
    public boolean deleteToken(String token) {
        return query("DELETE FROM account_token WHERE token = :token;")
                .single(call().bind("token", token))
                .delete()
                .changed();
    }

    /**
     * Deletes all expired tokens from the database.
     *
     * @return {@code true} if any tokens were deleted
     */
    public boolean deleteExpiredTokens() {
        return query("DELETE FROM account_token WHERE expires_at < now();")
                .single()
                .delete()
                .changed();
    }

    // -- Sessions --

    /**
     * Deletes all tokens of a specific type for an account, typically before creating a replacement.
     *
     * @param accountId the account identifier
     * @param tokenType the type of tokens to delete
     * @return {@code true} if any tokens were deleted
     */
    public boolean deleteTokensByAccountAndType(int accountId, TokenType tokenType) {
        return query("DELETE FROM account_token WHERE account_id = :account_id AND token_type = :type;")
                .single(call().bind("account_id", accountId).bind("type", tokenType))
                .delete()
                .changed();
    }

    /**
     * Finds a session by its bearer token.
     *
     * @param token the session token
     * @return the session, or empty if not found
     */
    public Optional<AccountSession> findSession(String token) {
        return query("""
                SELECT
                            id,
                            account_id,
                            token,
                            expires_at,
                            created_at,
                            user_agent,
                            last_used_at,
                            location
                        FROM
                            account_session
                        WHERE token = :token;""")
                .single(call().bind("token", token))
                .map(AccountSession.map())
                .first();
    }

    /**
     * Retrieves all sessions for an account, ordered by last-used timestamp descending.
     *
     * @param accountId the account identifier
     * @return list of sessions
     */
    public List<AccountSession> findSessionsByAccount(int accountId) {
        return query("""
                SELECT
                    id,
                    account_id,
                    token,
                    expires_at,
                    created_at,
                    user_agent,
                    last_used_at,
                    location
                FROM
                    account_session
                WHERE account_id = :account_id
                ORDER BY last_used_at DESC;""")
                .single(call().bind("account_id", accountId))
                .map(AccountSession.map())
                .all();
    }

    /**
     * Creates a new session for an account.
     *
     * @param accountId the account identifier
     * @param token     the session bearer token
     * @param expiresAt when the session expires
     * @param userAgent the client's user agent string
     * @param location  the client's location (e.g. country code)
     * @return the insertion result
     */
    public InsertionResult createSession(
            int accountId, String token, Instant expiresAt, String userAgent, String location) {
        return query("""
                INSERT
                INTO
                    account_session(account_id, token, expires_at, user_agent, location)
                VALUES
                    (:account_id, :token, :expires_at, :user_agent, :location);""")
                .single(call().bind("account_id", accountId)
                        .bind("token", token)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP)
                        .bind("user_agent", userAgent)
                        .bind("location", location))
                .insert();
    }

    /**
     * Updates the last-used timestamp, user agent, and location of a session.
     *
     * @param token     the session token
     * @param userAgent the current user agent string
     * @param location  the current location, or {@code null} to keep the existing value
     * @return {@code true} if the session was updated
     */
    public boolean touchSession(String token, String userAgent, String location) {
        return query("""
                UPDATE account_session
                SET
                    last_used_at = now(),
                    user_agent   = :user_agent,
                    location     = coalesce(:location, location)
                WHERE token = :token;
                """)
                .single(call().bind("user_agent", userAgent)
                        .bind("location", location)
                        .bind("token", token))
                .update()
                .changed();
    }

    /**
     * Rotates a session token, replacing the old token with a new one and updating the expiration.
     *
     * @param oldToken     the current token to replace
     * @param newToken     the new token
     * @param newExpiresAt the new expiration time
     * @return {@code true} if the session was updated
     */
    public boolean rotateSessionToken(String oldToken, String newToken, Instant newExpiresAt) {
        return query("""
                UPDATE account_session
                        SET
                            token      = :new_token,
                            expires_at = :expires_at
                        WHERE token = :old_token;""")
                .single(call().bind("new_token", newToken)
                        .bind("expires_at", newExpiresAt, INSTANT_TIMESTAMP)
                        .bind("old_token", oldToken))
                .update()
                .changed();
    }

    /**
     * Deletes a session by its token.
     *
     * @param token the session token
     * @return {@code true} if the session was deleted
     */
    public boolean deleteSession(String token) {
        return query("DELETE FROM account_session WHERE token = :token;")
                .single(call().bind("token", token))
                .delete()
                .changed();
    }

    /**
     * Deletes a specific session by ID, scoped to the given account for security.
     *
     * @param id        the session identifier
     * @param accountId the account identifier (ensures the session belongs to this account)
     * @return {@code true} if the session was deleted
     */
    public boolean deleteSessionById(int id, int accountId) {
        return query("DELETE FROM account_session WHERE id = :id AND account_id = :account_id;")
                .single(call().bind("id", id).bind("account_id", accountId))
                .delete()
                .changed();
    }

    /**
     * Deletes all sessions for an account, effectively logging out all devices.
     *
     * @param accountId the account identifier
     * @return {@code true} if any sessions were deleted
     */
    public boolean deleteSessionsByAccount(int accountId) {
        return query("DELETE FROM account_session WHERE account_id = :account_id;")
                .single(call().bind("account_id", accountId))
                .delete()
                .changed();
    }

    // -- GDPR Consent --

    /**
     * Deletes all expired sessions from the database.
     *
     * @return {@code true} if any sessions were deleted
     */
    public boolean deleteExpiredSessions() {
        return query("DELETE FROM account_session WHERE expires_at < now();")
                .single()
                .delete()
                .changed();
    }

    /**
     * Records a GDPR consent entry for an account with version information and client metadata.
     *
     * @param accountId      the account identifier
     * @param consentVersion the consent form version
     * @param privacyVersion the privacy policy version
     * @param tosVersion     the terms of service version
     * @param ipAddress      the client's IP address
     * @param country        the client's country
     * @param userAgent      the client's user agent string
     * @return the insertion result
     */
    public InsertionResult recordConsent(
            int accountId,
            String consentVersion,
            String privacyVersion,
            String tosVersion,
            String ipAddress,
            String country,
            String userAgent) {
        return query("""
                INSERT
                        INTO
                            gdpr_consent(account_id, consent_version, privacy_version, tos_version, ip_address, country, user_agent)
                        VALUES
                            (:account_id, :consent_version, :privacy_version, :tos_version, :ip_address, :country, :user_agent);""")
                .single(call().bind("account_id", accountId)
                        .bind("consent_version", consentVersion)
                        .bind("privacy_version", privacyVersion)
                        .bind("tos_version", tosVersion)
                        .bind("ip_address", ipAddress)
                        .bind("country", country)
                        .bind("user_agent", userAgent))
                .insert();
    }

    /**
     * Finds the most recent GDPR consent record for an account.
     *
     * @param accountId the account identifier
     * @return the latest consent record, or empty if none exists
     */
    public Optional<GdprConsent> findLatestConsent(int accountId) {
        return query("SELECT " + CONSENT_COLUMNS
                        + " FROM gdpr_consent WHERE account_id = :account_id ORDER BY consented_at DESC LIMIT 1;")
                .single(call().bind("account_id", accountId))
                .map(GdprConsent.map())
                .first();
    }

    /**
     * Retrieves all GDPR consent records for an account, ordered by most recent first.
     *
     * @param accountId the account identifier
     * @return list of consent records
     */
    public List<GdprConsent> findAllConsents(int accountId) {
        return query("SELECT " + CONSENT_COLUMNS
                        + " FROM gdpr_consent WHERE account_id = :account_id ORDER BY consented_at DESC;")
                .single(call().bind("account_id", accountId))
                .map(GdprConsent.map())
                .all();
    }
}
