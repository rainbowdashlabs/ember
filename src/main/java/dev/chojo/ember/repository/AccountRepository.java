/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.AccountCredential;
import dev.chojo.ember.entity.AccountExternalAuth;
import dev.chojo.ember.entity.AccountSession;
import dev.chojo.ember.entity.AccountToken;
import dev.chojo.ember.entity.TokenType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class AccountRepository {

    private static final String ACCOUNT_COLUMNS = "id, email, first_name, last_name, email_verified";

    public Optional<Account> findById(int id) {
        return query("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Account.map())
                .first();
    }

    public Optional<Account> findByEmail(String email) {
        return query("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE email = :email;")
                .single(Call.of().bind("email", email))
                .map(Account.map())
                .first();
    }

    public List<Account> findAll() {
        return query("SELECT " + ACCOUNT_COLUMNS + " FROM account;")
                .single()
                .map(Account.map())
                .all();
    }

    public Account create(String email, String firstName, String lastName) {
        return query(
                        "INSERT INTO account(email, first_name, last_name) VALUES(:email, :first_name, :last_name) RETURNING "
                                + ACCOUNT_COLUMNS + ";")
                .single(Call.of()
                        .bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName))
                .map(Account.map())
                .first()
                .orElseThrow();
    }

    public Account create(String email, String firstName, String lastName, boolean emailVerified) {
        return query(
                        "INSERT INTO account(email, first_name, last_name, email_verified) VALUES(:email, :first_name, :last_name, :verified) RETURNING "
                                + ACCOUNT_COLUMNS + ";")
                .single(Call.of()
                        .bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("verified", emailVerified))
                .map(Account.map())
                .first()
                .orElseThrow();
    }

    // -- Account Roles --

    public List<String> findAccountRoles(int accountId) {
        return query("SELECT role FROM account_role WHERE account_id = :account_id;")
                .single(Call.of().bind("account_id", accountId))
                .map(row -> row.getString("role"))
                .all();
    }

    public boolean hasAccountRole(int accountId, String role) {
        return query("SELECT 1 FROM account_role WHERE account_id = :account_id AND role = :role;")
                .single(Call.of().bind("account_id", accountId).bind("role", role))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public boolean anyAccountHasRole(String role) {
        return query("SELECT 1 FROM account_role WHERE role = :role LIMIT 1;")
                .single(Call.of().bind("role", role))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public InsertionResult addAccountRole(int accountId, String role) {
        return query("INSERT INTO account_role(account_id, role) VALUES(:account_id, :role);")
                .single(Call.of().bind("account_id", accountId).bind("role", role))
                .insert();
    }

    public boolean removeAccountRole(int accountId, String role) {
        return query("DELETE FROM account_role WHERE account_id = :account_id AND role = :role;")
                .single(Call.of().bind("account_id", accountId).bind("role", role))
                .delete()
                .changed();
    }

    public boolean update(int id, String email, String firstName, String lastName) {
        return query(
                        "UPDATE account SET email = :email, first_name = :first_name, last_name = :last_name WHERE id = :id;")
                .single(Call.of()
                        .bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean setEmailVerified(int id) {
        return query("""
                UPDATE account SET email_verified = TRUE WHERE id = :id;""").single(Call.of().bind("id", id)).update().changed();
    }

    public boolean delete(int id) {
        return query("""
                DELETE FROM account WHERE id = :id;""").single(Call.of().bind("id", id)).delete().changed();
    }

    // -- Credentials --

    public Optional<AccountCredential> findCredential(int accountId) {
        return query("""
                SELECT account_id, password_hash, force_password_change FROM account_credential WHERE account_id = :id;""")
                .single(Call.of().bind("id", accountId))
                .map(AccountCredential.map())
                .first();
    }

    public InsertionResult createCredential(int accountId, String passwordHash) {
        return query("""
                INSERT INTO account_credential(account_id, password_hash) VALUES(:account_id, :hash);""")
                .single(Call.of().bind("account_id", accountId).bind("hash", passwordHash))
                .insert();
    }

    public boolean updateCredential(int accountId, String passwordHash) {
        return query(
                        "UPDATE account_credential SET password_hash = :hash, force_password_change = FALSE WHERE account_id = :id;")
                .single(Call.of().bind("hash", passwordHash).bind("id", accountId))
                .update()
                .changed();
    }

    public boolean setForcePasswordChange(int accountId, boolean force) {
        return query("UPDATE account_credential SET force_password_change = :force WHERE account_id = :id;")
                .single(Call.of().bind("force", force).bind("id", accountId))
                .update()
                .changed();
    }

    public boolean deleteCredential(int accountId) {
        return query("DELETE FROM account_credential WHERE account_id = :id;")
                .single(Call.of().bind("id", accountId))
                .delete()
                .changed();
    }

    // -- External Auth --

    public List<AccountExternalAuth> findExternalAuths(int accountId) {
        return query("SELECT id, account_id, provider, external_id FROM account_external_auth WHERE account_id = :id;")
                .single(Call.of().bind("id", accountId))
                .map(AccountExternalAuth.map())
                .all();
    }

    public Optional<AccountExternalAuth> findExternalAuth(String provider, String externalId) {
        return query(
                        "SELECT id, account_id, provider, external_id FROM account_external_auth WHERE provider = :provider AND external_id = :external_id;")
                .single(Call.of().bind("provider", provider).bind("external_id", externalId))
                .map(AccountExternalAuth.map())
                .first();
    }

    public InsertionResult createExternalAuth(int accountId, String provider, String externalId) {
        return query(
                        "INSERT INTO account_external_auth(account_id, provider, external_id) VALUES(:account_id, :provider, :external_id);")
                .single(Call.of()
                        .bind("account_id", accountId)
                        .bind("provider", provider)
                        .bind("external_id", externalId))
                .insert();
    }

    public boolean deleteExternalAuth(int id) {
        return query("DELETE FROM account_external_auth WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Tokens --

    public Optional<AccountToken> findToken(String token) {
        return query(
                        "SELECT id, account_id, token, token_type, expires_at, created_at FROM account_token WHERE token = :token;")
                .single(Call.of().bind("token", token))
                .map(AccountToken.map())
                .first();
    }

    public InsertionResult createToken(int accountId, String token, TokenType tokenType, Instant expiresAt) {
        return query(
                        "INSERT INTO account_token(account_id, token, token_type, expires_at) VALUES(:account_id, :token, :type, :expires_at);")
                .single(Call.of()
                        .bind("account_id", accountId)
                        .bind("token", token)
                        .bind("type", tokenType)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .insert();
    }

    public boolean deleteToken(String token) {
        return query("DELETE FROM account_token WHERE token = :token;")
                .single(Call.of().bind("token", token))
                .delete()
                .changed();
    }

    public boolean deleteExpiredTokens() {
        return query("DELETE FROM account_token WHERE expires_at < now();")
                .single()
                .delete()
                .changed();
    }

    public boolean deleteTokensByAccountAndType(int accountId, TokenType tokenType) {
        return query("DELETE FROM account_token WHERE account_id = :account_id AND token_type = :type;")
                .single(Call.of().bind("account_id", accountId).bind("type", tokenType))
                .delete()
                .changed();
    }

    // -- Sessions --

    public Optional<AccountSession> findSession(String token) {
        return query(
                        "SELECT id, account_id, token, expires_at, created_at, user_agent, last_used_at FROM account_session WHERE token = :token;")
                .single(Call.of().bind("token", token))
                .map(AccountSession.map())
                .first();
    }

    public List<AccountSession> findSessionsByAccount(int accountId) {
        return query(
                        "SELECT id, account_id, token, expires_at, created_at, user_agent, last_used_at FROM account_session WHERE account_id = :account_id ORDER BY last_used_at DESC;")
                .single(Call.of().bind("account_id", accountId))
                .map(AccountSession.map())
                .all();
    }

    public InsertionResult createSession(int accountId, String token, Instant expiresAt, String userAgent) {
        return query(
                        "INSERT INTO account_session(account_id, token, expires_at, user_agent) VALUES(:account_id, :token, :expires_at, :user_agent);")
                .single(Call.of()
                        .bind("account_id", accountId)
                        .bind("token", token)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP)
                        .bind("user_agent", userAgent))
                .insert();
    }

    public boolean touchSession(String token, String userAgent) {
        return query("UPDATE account_session SET last_used_at = now(), user_agent = :user_agent WHERE token = :token;")
                .single(Call.of().bind("user_agent", userAgent).bind("token", token))
                .update()
                .changed();
    }

    public boolean rotateSessionToken(String oldToken, String newToken, Instant newExpiresAt) {
        return query(
                        "UPDATE account_session SET token = :new_token, expires_at = :expires_at WHERE token = :old_token;")
                .single(Call.of()
                        .bind("new_token", newToken)
                        .bind("expires_at", newExpiresAt, INSTANT_TIMESTAMP)
                        .bind("old_token", oldToken))
                .update()
                .changed();
    }

    public boolean deleteSession(String token) {
        return query("DELETE FROM account_session WHERE token = :token;")
                .single(Call.of().bind("token", token))
                .delete()
                .changed();
    }

    public boolean deleteSessionById(int id, int accountId) {
        return query("DELETE FROM account_session WHERE id = :id AND account_id = :account_id;")
                .single(Call.of().bind("id", id).bind("account_id", accountId))
                .delete()
                .changed();
    }

    public boolean deleteSessionsByAccount(int accountId) {
        return query("DELETE FROM account_session WHERE account_id = :account_id;")
                .single(Call.of().bind("account_id", accountId))
                .delete()
                .changed();
    }

    public boolean deleteExpiredSessions() {
        return query("DELETE FROM account_session WHERE expires_at < now();")
                .single()
                .delete()
                .changed();
    }
}
