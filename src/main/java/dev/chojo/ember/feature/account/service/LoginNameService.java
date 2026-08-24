/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.regex.Pattern;

/**
 * The name somebody signs in with when it is not their address, and what makes one acceptable.
 *
 * <p>A name never contains an at sign. That one rule is what keeps the two ways in apart: what
 * somebody types at the login screen is an address when it holds an at sign and a name when it does
 * not, so neither can be mistaken for the other and a name can never collide with an address. What is
 * left to check is that no two accounts carry the same name, which is asked without regard to case.
 *
 * <p>Three separate screens set a name: somebody editing their own account, a member manager editing
 * somebody else's, and a guardian setting one for a member in their care. All three judge it here.
 */
@Singleton
public class LoginNameService {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 32;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final AccountRepository accountRepository;

    @Inject
    public LoginNameService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * The name as it will be stored, or null where none was given.
     *
     * @param username  the name as it was typed, which may be null or blank for none
     * @param accountId the account it is meant for, which may already carry it
     * @return the trimmed name, or null to leave the address as the only way in
     * @throws BadRequestResponse when the name is malformed or already somebody else's
     */
    public String validated(String username, Integer accountId) {
        if (username == null || username.isBlank()) return null;
        String name = username.trim();
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            throw new BadRequestResponse("A username is between 3 and 32 characters long");
        }
        if (!ALLOWED.matcher(name).matches()) {
            throw new BadRequestResponse("A username may only hold letters, digits, dots, dashes and underscores");
        }
        if (accountRepository.usernameTaken(name, accountId)) {
            throw new BadRequestResponse("This username already belongs to another account");
        }
        return name;
    }

    /**
     * The same, for an account that may have nothing else to sign in with.
     *
     * <p>An account with no address of its own is reached by its name and by nothing else, so taking
     * the name away would lock it out without saying so. Give it an address first.
     *
     * @throws BadRequestResponse when the name is malformed, taken, or the only way into the account
     */
    public String validatedFor(Account account, String username) {
        String name = validated(username, account.id());
        if (name == null && account.username() != null && !account.hasRealEmail()) {
            throw new BadRequestResponse("This account signs in with its username; set an email address first");
        }
        return name;
    }

    /**
     * Whether what somebody typed at the login screen is an address rather than a name.
     */
    public static boolean looksLikeEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }
}
