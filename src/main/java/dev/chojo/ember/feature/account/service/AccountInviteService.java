/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The account behind an address somebody was just named at, created when Ember has never seen it.
 *
 * <p>Taking somebody on is two acts and they belong to different owners: an account, which is the
 * instance's, and a membership, which belongs to whoever is taking them on. A station's invite path did
 * both in one method, so an association wanting only the first half had no way to reach it and refused
 * every address that was not already an account. This is that first half, and both callers use it.
 *
 * <p>The address decides the branding of the setup mail through the station it is stamped with. For a
 * station that is the station taking somebody on; for an association it is the station the association
 * owns, which is where its identity already lives.
 */
@Singleton
public class AccountInviteService {
    private static final Logger log = LoggerFactory.getLogger(AccountInviteService.class);

    /**
     * What an address ends in when it was made up for somebody who is not meant to sign in.
     *
     * <p>Nothing makes one any more: somebody without an address of their own is given none at all,
     * because an address that looks real and cannot be written to only has to be explained. Accounts
     * carrying one from before are still read, and finding one already taken is a collision rather
     * than somebody who already has an account.
     */
    public static final String SYNTHETIC_EMAIL_SUFFIX = ".local";

    private final AccountRepository accountRepository;
    private final AuthService authService;

    @Inject
    public AccountInviteService(AccountRepository accountRepository, AuthService authService) {
        this.accountRepository = accountRepository;
        this.authService = authService;
    }

    /**
     * The account for this address, made if there is none, with the password-setup mail sent when it is
     * still owed one and whoever entered them asked for it to go now.
     *
     * @param stationId  the station the account is stamped with, which brands the mail
     * @param email      the address, already trimmed by the caller or not
     * @param firstName  their first name, used only when the account is made
     * @param lastName   their last name, used only when the account is made
     * @param setupMail  whether that mail leaves now or waits to be sent by hand
     * @return the account and whether this call made it
     * @throws EmailInUseException when a made-up address already belongs to somebody
     */
    public Invited resolveOrCreate(
            int stationId, String email, String firstName, String lastName, SetupMail setupMail) {
        String address = email.trim();
        boolean synthetic = address.endsWith(SYNTHETIC_EMAIL_SUFFIX);

        Account existing = accountRepository.findByEmail(address).orElse(null);
        if (existing != null && synthetic) {
            log.warn("Made-up address {} is already taken, station {} cannot use it", address, stationId);
            throw new EmailInUseException(address);
        }

        boolean created = existing == null;
        Account account = created ? accountRepository.create(address, firstName, lastName, true, stationId) : existing;
        if (created) log.info("Account {} created by invitation from station {}", account.id(), stationId);

        if (setupMail.sendsNow() && !synthetic && needsSetup(account)) {
            authService.sendPasswordSetup(account.id());
        }
        return new Invited(account, created);
    }

    /**
     * The account for somebody entered without an address of their own.
     *
     * <p>No address at all, rather than one made up to look like one. A made-up address is shown in
     * the member list as though somebody could write to it, is offered a setup mail that can only
     * fail, and has to be explained to whoever reads it. An account without one says the same thing
     * and says it plainly: this person has no address, and whatever concerns them goes to whoever
     * looks after them.
     *
     * @param stationId the station entering them
     * @param firstName their first name
     * @param lastName  their surname
     * @return the account, always newly made
     */
    public Invited createWithoutAddress(int stationId, String firstName, String lastName) {
        var account = accountRepository.create(null, firstName, lastName, stationId);
        log.info("Account {} created without an address by invitation from station {}", account.id(), stationId);
        return new Invited(account, true);
    }

    /** Nobody is sent a setup mail for an account they have already set up. */
    private boolean needsSetup(Account account) {
        return account.setupCompletedAt() == null && !accountRepository.hasChosenPassword(account.id());
    }

    /**
     * @param created whether this call made the account, rather than finding one
     */
    public record Invited(Account account, boolean created) {}

    /** A made-up address that already belongs to somebody, which is a collision and not a reunion. */
    public static class EmailInUseException extends RuntimeException {
        public EmailInUseException(String email) {
            super("Email already registered: " + email);
        }
    }
}
