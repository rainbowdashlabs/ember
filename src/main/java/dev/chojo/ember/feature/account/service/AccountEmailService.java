/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Setting the address an account is reached at on somebody else's behalf.
 *
 * <p>An account holder changing their own address confirms it from both ends, which is what stops a
 * stolen session from quietly walking off with the account. Somebody acting for the holder cannot go
 * that way: a guardian's child has no postbox to confirm from, and an address an administrator is
 * asked to put right is usually wrong or dead, which is exactly the address the old half of that
 * confirmation would be sent to. So this writes the address at once, and pays for it in the two
 * things that make the change visible: every session of that account ends, and both addresses are
 * told, as far as mail can reach them.
 */
@Singleton
public class AccountEmailService {
    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AccountRepository accountRepository;
    private final MailLocaleService mailLocaleService;
    private final EmailService emailService;

    @Inject
    public AccountEmailService(
            AccountRepository accountRepository, MailLocaleService mailLocaleService, EmailService emailService) {
        this.accountRepository = accountRepository;
        this.mailLocaleService = mailLocaleService;
        this.emailService = emailService;
    }

    /**
     * What stands in the way of an address, so a caller that has something of its own to say about
     * each answer can ask before it writes.
     */
    public enum AddressProblem {
        NONE,
        /** Not shaped like an address at all. */
        MALFORMED,
        /** Shaped like one, but made up: nothing can be delivered to it. */
        UNREACHABLE,
        /** Somebody else's. */
        TAKEN
    }

    /** The address as it is stored: trimmed, and in one case. */
    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this address may be written onto this account.
     *
     * @param accountId the account it is meant for, which may already carry it
     * @param email     the address, as it was typed
     */
    public AddressProblem problemWith(int accountId, String email) {
        String normalised = normalise(email);
        if (!EMAIL.matcher(normalised).matches()) return AddressProblem.MALFORMED;
        if (!Account.isRealEmail(normalised)) return AddressProblem.UNREACHABLE;
        var existing = accountRepository.findByEmail(normalised);
        return existing.isPresent() && existing.get().id() != accountId ? AddressProblem.TAKEN : AddressProblem.NONE;
    }

    /**
     * Writes an address onto somebody else's account, and refuses to write one onto the caller's own.
     *
     * <p>The refusal is the whole point of the method. Writing an address without confirming it is a
     * takeover in one step: whoever holds a session long enough to reach this would move the account
     * to an address of their own and reset the password to it. What makes it safe for somebody else's
     * account is that the person doing it is not the person who would gain by it, and that stops
     * being true the moment the two are the same. Putting one's own address right therefore goes the
     * long way round, through a confirmation, however senior the account is.
     *
     * @param callerAccountId the account asking for the change
     * @param accountId       the account whose address is being written
     * @throws ForbiddenResponse when the two are the same account
     */
    public boolean setEmailFor(int callerAccountId, int accountId, String email) {
        if (callerAccountId == accountId) {
            throw new ForbiddenResponse("An account cannot write its own address without confirming it");
        }
        return setEmail(accountId, email);
    }

    /**
     * Writes a new address onto an account and ends every session it had.
     *
     * @param accountId the account
     * @param email     the new address
     * @return {@code true} when the address changed, {@code false} when it was already this one
     * @throws NotFoundResponse   if the account does not exist
     * @throws BadRequestResponse if the address is not one, or already belongs to another account
     */
    public boolean setEmail(int accountId, String email) {
        Account account = accountRepository.findById(accountId).orElseThrow(NotFoundResponse::new);
        String normalised = normalise(email);
        switch (problemWith(accountId, email)) {
            case MALFORMED, UNREACHABLE -> throw new BadRequestResponse("A valid email address is required");
            case TAKEN -> throw new BadRequestResponse("This email address already belongs to another account");
            case NONE -> {}
        }
        if (normalised.equalsIgnoreCase(account.email())) return false;

        String previous = account.email();
        accountRepository.updateEmail(accountId, normalised);
        accountRepository.deleteSessionsByAccount(accountId);
        if (Account.isRealEmail(previous)) {
            String mailLocale = mailLocaleService.forAccount(accountId);
            emailService.sendEmailChangedNotice(previous, account.firstName(), previous, normalised, mailLocale);
            emailService.sendEmailChangedNotice(normalised, account.firstName(), previous, normalised, mailLocale);
        }
        log.info("Address of account {} set to a new one on its holder's behalf", accountId);
        return true;
    }
}
