/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import java.time.Instant;

/**
 * A one-time password as it is shown, once, to the administrator who issued it. Nothing here is
 * stored in this form: the password is kept as a hash like any other.
 *
 * @param accountId the account it was issued for
 * @param name      the account's name, for the sheet that is handed over
 * @param loginName what the person types as their name at the login screen
 * @param password  the one-time password in plain text
 * @param expiresAt when it stops working
 */
public record IssuedOneTimePassword(int accountId, String name, String loginName, String password, Instant expiresAt) {}
