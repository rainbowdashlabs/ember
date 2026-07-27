/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountExternalAuth;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Repository for account-related database operations including accounts, credentials,
 * external auth providers, tokens, sessions, and GDPR consent records.
 */
@Singleton
public class AccountRepository {

    private static final String ACCOUNT_COLUMNS =
            "id, uid, email, first_name, last_name, email_verified, instance_user_type, full_name, creating_station_id, setup_completed_at";
    private static final String CONSENT_COLUMNS =
            "id, account_id, consent_version, privacy_version, tos_version, ip_address, country, user_agent, consented_at";
    private static final String EXTERNAL_AUTH_COLUMNS = "id, account_id, provider, external_id";
    private static final String TOKEN_COLUMNS =
            "id, account_id, token_hash, token_type, metadata, expires_at, created_at, confirmed_at";
    private static final String SESSION_COLUMNS =
            "id, account_id, token_hash, expires_at, created_at, user_agent, last_used_at, location, two_factor_verified_at";

    private final TokenHasher tokenHasher;

    @Inject
    public AccountRepository(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    /**
     * Finds an account by its unique identifier.
     *
     * @param id the account identifier
     * @return the account, or empty if not found
     */
    public Optional<Account> findById(int id) {
        return SqlSupport.findById("account", ACCOUNT_COLUMNS, id, Account.map());
    }

    /**
     * Finds an account by its public UUID.
     *
     * @param uid the account's public UUID
     * @return the account, or empty if not found
     */
    public Optional<Account> findByUid(UUID uid) {
        return query("SELECT %s FROM account WHERE uid = :uid::UUID;", ACCOUNT_COLUMNS)
                .single(call().bind("uid", uid, UUID_STRING))
                .map(Account.map())
                .first();
    }

    /**
     * Resolves the public UUID for an account by its internal id.
     *
     * @param id the account identifier
     * @return the UUID, or {@code null} if no such account exists
     */
    public UUID resolveUid(int id) {
        return query("SELECT uid FROM account WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> row.get("uid", UUID_STRING))
                .first()
                .orElse(null);
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
     * Searches accounts for an admin picker. With an empty query, returns the most recently
     * created accounts. With a non-empty query, performs case-insensitive partial matches across
     * {@code first_name}, {@code last_name}, the joined display name, and {@code email}.
     *
     * @param search the search term, or {@code null}/blank for the default state
     * @param limit  the maximum number of rows to return
     * @return matching picker rows
     */
    public List<PickerAccount> searchForPicker(String search, int limit) {
        boolean hasSearch = search != null && !search.isBlank();
        String predicate = hasSearch
                ? "WHERE LOWER(COALESCE(full_name, first_name || ' ' || last_name, '')) LIKE :q"
                        + " OR LOWER(first_name) LIKE :q"
                        + " OR LOWER(last_name) LIKE :q"
                        + " OR LOWER(email) LIKE :q"
                : "";
        String order = hasSearch ? "display_name" : "id DESC";
        var c = call().bind("limit", limit);
        if (hasSearch) {
            c = c.bind("q", "%" + search.trim().toLowerCase() + "%");
        }
        return query("""
                SELECT id,
                       uid,
                       coalesce(nullif(full_name, ''), trim(BOTH ' ' FROM first_name || ' ' || last_name)) AS display_name,
                       first_name,
                       last_name,
                       email
                FROM account
                %s
                ORDER BY %s
                LIMIT :limit;""", predicate, order).single(c).map(PickerAccount.map()).all();
    }

    /**
     * Single-lookup variant of {@link #searchForPicker} used to resolve a stored account UUID
     * back to its picker shape.
     *
     * @param uid the account's public UUID
     * @return the picker row, or empty if no such account exists
     */
    public Optional<PickerAccount> findPickerByUid(UUID uid) {
        return query("""
                SELECT id,
                       uid,
                       coalesce(nullif(full_name, ''), trim(BOTH ' ' FROM first_name || ' ' || last_name)) AS display_name,
                       first_name,
                       last_name,
                       email
                FROM account
                WHERE uid = :uid::UUID;""")
                .single(call().bind("uid", uid, UUID_STRING))
                .map(PickerAccount.map())
                .first();
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
        return create(email, firstName, lastName, null);
    }

    /**
     * Creates a new account, optionally remembering the station it was created from.
     *
     * @param email              the email address
     * @param firstName          the first name
     * @param lastName           the last name
     * @param creatingStationId  the station that initiated the creation, or {@code null}
     * @return the created account
     */
    public Account create(String email, String firstName, String lastName, Integer creatingStationId) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    account(email, first_name, last_name, creating_station_id)
                VALUES
                    (:email, :first_name, :last_name, :creating_station_id)
                RETURNING %s;""",
                call().bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("creating_station_id", creatingStationId),
                Account.map(),
                ACCOUNT_COLUMNS);
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
        return create(email, firstName, lastName, emailVerified, null);
    }

    /**
     * Creates a new account with an explicit email verification status and originating station.
     *
     * @param email              the email address
     * @param firstName          the first name
     * @param lastName           the last name
     * @param emailVerified      whether the email should be marked as already verified
     * @param creatingStationId  the station that initiated the creation, or {@code null}
     * @return the created account
     */
    public Account create(
            String email, String firstName, String lastName, boolean emailVerified, Integer creatingStationId) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    account(email, first_name, last_name, email_verified, creating_station_id)
                VALUES
                    (:email, :first_name, :last_name, :verified, :creating_station_id)
                RETURNING %s;""",
                call().bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("verified", emailVerified)
                        .bind("creating_station_id", creatingStationId),
                Account.map(),
                ACCOUNT_COLUMNS);
    }

    /**
     * Stamps the account as setup-completed if it is not stamped yet. No-op once the timestamp is set.
     * Called on the first successful login.
     */
    public void markSetupCompleted(int accountId) {
        query("UPDATE account SET setup_completed_at = now() WHERE id = :id AND setup_completed_at IS NULL;")
                .single(call().bind("id", accountId))
                .update();
    }

    /**
     * Resolves the language code to use for system mails sent to {@code accountId}. Picks the
     * language of the station the account was created from (falling back to {@code "en"} for
     * self-signup, admin bootstrap, or any account without a known origin). The returned value
     * is the short ISO 639 code (e.g. {@code "de"}, {@code "en"}), suitable for mail template
     * lookup.
     */
    public String findMailLocale(int accountId) {
        return query("""
                SELECT s.locale
                FROM account a
                JOIN station s ON s.id = a.creating_station_id
                WHERE a.id = :id;""")
                .single(call().bind("id", accountId))
                .map(row -> row.getString("locale"))
                .first()
                .map(Locale::forLanguageTag)
                .map(Locale::getLanguage)
                .filter(s -> !s.isBlank())
                .orElse("en");
    }

    /**
     * Replaces the {@code uid} column for the account. Used by the demo seeder to pin
     * deterministic UUIDs so demo data does not accumulate on disk across restarts.
     */
    public void setUid(int id, UUID uid) {
        query("UPDATE account SET uid = :uid::uuid WHERE id = :id;")
                .single(call().bind("uid", uid, UUID_STRING).bind("id", id))
                .update();
    }

    /**
     * Checks whether an account is an instance administrator.
     */
    public boolean isAdministrator(int accountId) {
        return SqlSupport.exists(
                "SELECT 1 FROM account WHERE id = :id AND instance_user_type = 'ADMINISTRATOR';",
                call().bind("id", accountId));
    }

    // -- Instance User Type --

    /**
     * Checks whether any account in the system is an administrator.
     */
    public boolean anyAdministratorExists() {
        return SqlSupport.exists("SELECT 1 FROM account WHERE instance_user_type = 'ADMINISTRATOR' LIMIT 1;", call());
    }

    /**
     * Sets the instance user type for an account.
     */
    public void setInstanceUserType(int accountId, InstanceUserType userType) {
        query("UPDATE account SET instance_user_type = :user_type WHERE id = :id;")
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
     */
    public void updateEmail(int id, String email) {
        query("UPDATE account SET email = :email WHERE id = :id;")
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

    /**
     * Deletes an account by its identifier.
     *
     * @param id the account identifier
     * @return {@code true} if the account was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("account", id);
    }

    // -- Credentials --

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
                    force_password_change,
                    last_breach_check_at
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
     */
    public void createCredential(int accountId, String passwordHash) {
        query("""
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
                    force_password_change = FALSE,
                    last_breach_check_at  = NULL
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

    /**
     * Records the outcome of an HIBP breach check for an account's credential.
     * Always updates {@code last_breach_check_at} so the next check is gated by
     * the staleness window; sets {@code force_password_change} only when the
     * lookup reported a pwned password.
     *
     * @param accountId the account identifier
     * @param when      the check timestamp
     * @param pwned     whether the password was found in a breach corpus
     * @return {@code true} if the credential row was updated
     */
    public boolean updateLastBreachCheck(int accountId, Instant when, boolean pwned) {
        String sql = pwned ? """
                UPDATE account_credential
                SET
                    last_breach_check_at  = :when,
                    force_password_change = TRUE
                WHERE account_id = :id;""" : """
                UPDATE account_credential
                SET last_breach_check_at = :when
                WHERE account_id = :id;""";
        return query(sql)
                .single(call().bind("when", when, INSTANT_TIMESTAMP).bind("id", accountId))
                .update()
                .changed();
    }

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

    // -- External Auth --

    /**
     * Retrieves all external authentication links for an account.
     *
     * @param accountId the account identifier
     * @return list of external auth records
     */
    public List<AccountExternalAuth> findExternalAuths(int accountId) {
        return query("SELECT %s FROM account_external_auth WHERE account_id = :id;", EXTERNAL_AUTH_COLUMNS)
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
                SELECT %s
                FROM account_external_auth
                WHERE provider = :provider
                  AND external_id = :external_id;""", EXTERNAL_AUTH_COLUMNS)
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
     */
    public void createExternalAuth(int accountId, String provider, String externalId) {
        query("""
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

    /**
     * Deletes an external authentication record by its identifier.
     *
     * @param id the external auth record identifier
     * @return {@code true} if the record was deleted
     */
    public boolean deleteExternalAuth(int id) {
        return SqlSupport.deleteById("account_external_auth", id);
    }

    // -- Tokens --

    /**
     * Finds a token by its token string.
     *
     * @param token the token string
     * @return the account token, or empty if not found
     */
    public Optional<AccountToken> findToken(String token) {
        return query("""
                SELECT %s
                FROM account_token
                WHERE token_hash = :token_hash;""", TOKEN_COLUMNS)
                .single(call().bind("token_hash", tokenHasher.hash(token)))
                .map(AccountToken.map())
                .first();
    }

    /**
     * Marks a token as pre-confirmed (one half of a two-sided confirmation flow).
     * The token is not deleted; the partner-side click is what commits the action.
     */
    public void markTokenConfirmed(int tokenId) {
        query("UPDATE account_token SET confirmed_at = now() WHERE id = :id;")
                .single(call().bind("id", tokenId))
                .update()
                .changed();
    }

    /**
     * Finds the partner half of a two-sided email-change confirmation. Both halves
     * share a {@code requestId} prefix in their {@code metadata} and live on the
     * same account; this method returns the row that is not the supplied
     * {@code selfId}, regardless of which type it carries.
     */
    public Optional<AccountToken> findEmailChangePartner(int accountId, String requestId, int selfId) {
        return query("""
                SELECT %s
                FROM account_token
                WHERE account_id = :account_id
                    AND id <> :self_id
                    AND token_type IN ('EMAIL_CHANGE_RELEASE', 'EMAIL_CHANGE_CLAIM')
                    AND metadata LIKE :prefix
                LIMIT 1;""", TOKEN_COLUMNS)
                .single(call().bind("account_id", accountId)
                        .bind("self_id", selfId)
                        .bind("prefix", requestId + "|%"))
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
     */
    public void createToken(int accountId, String token, TokenType tokenType, Instant expiresAt) {
        createToken(accountId, token, tokenType, null, expiresAt);
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
                            account_token(account_id, token_hash, token_type, metadata, expires_at)
                        VALUES
                            (:account_id, :token_hash, :type, :metadata, :expires_at);""")
                .single(call().bind("account_id", accountId)
                        .bind("token_hash", tokenHasher.hash(token))
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
        return query("DELETE FROM account_token WHERE token_hash = :token_hash;")
                .single(call().bind("token_hash", tokenHasher.hash(token)))
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

    // -- Sessions --

    /**
     * Deletes every recovery / verification token for an account. Used on a successful
     * password rotation so that any other pending password-reset, email-verification,
     * email-change or station-delete tokens for the same account stop working — they
     * would otherwise let an attacker who already obtained the credential keep
     * performing destructive actions through alternate token-consuming endpoints.
     *
     * @param accountId the account identifier
     */
    public void deleteAllTokens(int accountId) {
        query("DELETE FROM account_token WHERE account_id = :account_id;")
                .single(call().bind("account_id", accountId))
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
                SELECT %s
                FROM account_session
                WHERE token_hash = :token_hash;""", SESSION_COLUMNS)
                .single(call().bind("token_hash", tokenHasher.hash(token)))
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
                SELECT %s
                FROM account_session
                WHERE account_id = :account_id
                ORDER BY last_used_at DESC;""", SESSION_COLUMNS)
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
     */
    public void createSession(int accountId, String token, Instant expiresAt, String userAgent, String location) {
        createSession(accountId, token, expiresAt, userAgent, location, null, null);
    }

    /**
     * Creates a session and additionally records the 2FA-verification timestamp and trusted-device
     * link. Used by the 2FA verify and trusted-device login paths to mint a session that already
     * counts as "freshly verified" for step-up freshness checks.
     */
    public InsertionResult createSession(
            int accountId,
            String token,
            Instant expiresAt,
            String userAgent,
            String location,
            Instant twoFactorVerifiedAt,
            Integer deviceTrustId) {
        return query("""
                INSERT
                INTO
                    account_session(account_id, token_hash, expires_at, user_agent, location,
                                    two_factor_verified_at, device_trust_id)
                VALUES
                    (:account_id, :token_hash, :expires_at, :user_agent, :location,
                     :two_factor_verified_at, :device_trust_id);""")
                .single(call().bind("account_id", accountId)
                        .bind("token_hash", tokenHasher.hash(token))
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP)
                        .bind("user_agent", userAgent)
                        .bind("location", location)
                        .bind("two_factor_verified_at", twoFactorVerifiedAt, INSTANT_TIMESTAMP)
                        .bind("device_trust_id", deviceTrustId))
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
                WHERE token_hash = :token_hash;
                """)
                .single(call().bind("user_agent", userAgent)
                        .bind("location", location)
                        .bind("token_hash", tokenHasher.hash(token)))
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
                            token_hash = :new_hash,
                            expires_at = :expires_at
                        WHERE token_hash = :old_hash;""")
                .single(call().bind("new_hash", tokenHasher.hash(newToken))
                        .bind("expires_at", newExpiresAt, INSTANT_TIMESTAMP)
                        .bind("old_hash", tokenHasher.hash(oldToken)))
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
        return query("DELETE FROM account_session WHERE token_hash = :token_hash;")
                .single(call().bind("token_hash", tokenHasher.hash(token)))
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

    /**
     * Deletes every session for an account except the one belonging to {@code keepToken}.
     * Used by {@code changePassword} so the user is not forcibly logged out of the
     * session they used to change their own credential while every other live session
     * (potentially an attacker's) is killed.
     *
     * @param accountId the account identifier
     * @param keepToken the raw bearer of the session to retain (hashed internally)
     * @return {@code true} if any sessions were deleted
     */
    public boolean deleteSessionsExceptToken(int accountId, String keepToken) {
        return query("DELETE FROM account_session WHERE account_id = :account_id AND token_hash <> :keep_hash;")
                .single(call().bind("account_id", accountId).bind("keep_hash", tokenHasher.hash(keepToken)))
                .delete()
                .changed();
    }

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

    // -- GDPR Consent --

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
     */
    public void recordConsent(
            int accountId,
            String consentVersion,
            String privacyVersion,
            String tosVersion,
            String ipAddress,
            String country,
            String userAgent) {
        query("""
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
        return query("""
                SELECT %s
                FROM gdpr_consent
                WHERE account_id = :account_id
                ORDER BY consented_at DESC
                LIMIT 1;""", CONSENT_COLUMNS)
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
        return query("""
                SELECT %s
                FROM gdpr_consent
                WHERE account_id = :account_id
                ORDER BY consented_at DESC;""", CONSENT_COLUMNS)
                .single(call().bind("account_id", accountId))
                .map(GdprConsent.map())
                .all();
    }

    /**
     * Lightweight result row for the admin account-search picker. Carries both the internal id
     * (needed for admin-scoped 2FA reset / audit filter calls) and the public UUID.
     */
    public record PickerAccount(int id, UUID uid, String displayName, String firstName, String lastName, String email) {
        public static RowMapping<PickerAccount> map() {
            return row -> new PickerAccount(
                    row.getInt("id"),
                    row.get("uid", UUID_STRING),
                    row.getString("display_name"),
                    row.getString("first_name"),
                    row.getString("last_name"),
                    row.getString("email"));
        }
    }
}
