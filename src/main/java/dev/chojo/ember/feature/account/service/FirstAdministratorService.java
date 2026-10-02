/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The account that administers a brand new instance.
 *
 * <p>Created unless somebody already administers the instance, and nothing else with it: a fresh
 * instance has no station, and the administrator founds the first one after signing in, under a name
 * they choose. Logs what to sign in with: on an ordinary instance a random password with a forced
 * change at first sign-in, on a passwordless one no credential row at all and a one-time enrolment
 * link instead. Whoever can read the console is the person installing the instance, and a link that
 * can do nothing but create one passkey is a smaller thing to leave lying in a log file than a working
 * password.
 *
 * <p>The account is given a login name and no address at all. It used to be given a made-up one
 * ending in {@code .local}, which read as an address without being one: no password reset reached it,
 * no security notice did, and naming it as somebody's address elsewhere in the application was refused
 * as a collision, because a made-up address that is already taken is two different people rather than
 * one. A name is what an account signs in with when it has no address of its own, which is exactly
 * this account's position, and it is a truthful one: it says the instance does not yet know where to
 * write. Where to write is then asked for at the first sign-in, beside the password, and there is no
 * session until it has been answered.
 */
@Singleton
public class FirstAdministratorService {
    private static final Logger log = LoggerFactory.getLogger(FirstAdministratorService.class);
    private static final String ADMIN_LOGIN_NAME = "admin";
    private static final String ADMIN_FIRST_NAME = "Admin";
    private static final String ADMIN_LAST_NAME = "Admin";

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final PasskeyModeService passkeyModeService;
    private final PasskeyEnrollmentService enrollmentService;
    private final Api api;

    @Inject
    public FirstAdministratorService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            PasskeyModeService passkeyModeService,
            PasskeyEnrollmentService enrollmentService,
            Api api) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.passkeyModeService = passkeyModeService;
        this.enrollmentService = enrollmentService;
        this.api = api;
    }

    /**
     * Creates the administrator of a fresh instance, unless the instance already has one.
     */
    public void createIfMissing() {
        if (accountRepository.anyAdministratorExists()) return;
        create();
    }

    /**
     * Creates an administrator account with no station and logs how to sign in with it.
     *
     * @return the new account
     */
    public Account create() {
        boolean passwordless = passkeyModeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
        String loginName = freeLoginName();

        var account = accountRepository.create(null, ADMIN_FIRST_NAME, ADMIN_LAST_NAME, false);
        int accountId = account.id();
        accountRepository.updateUsername(accountId, loginName);
        String password = null;
        if (!passwordless) {
            password = RandomTokens.urlSafe(24);
            accountRepository.createCredential(accountId, passwordHasher.hash(password));
            accountRepository.setForcePasswordChange(accountId, true);
        }
        accountRepository.setInstanceUserType(accountId, InstanceUserType.ADMINISTRATOR);

        log.info("==========================================================");
        log.info("  Default admin account created");
        log.info("  Username: {}", loginName);
        if (passwordless) {
            String code = enrollmentService.issueCode(accountId, PasskeyEnrollmentService.LINK_TTL);
            log.info("  This instance is passwordless. Create the admin's passkey here (link lives one hour):");
            log.info("  {}/enroll?code={}", api.baseUrl(), code);
        } else {
            log.info("  Password: {}", password);
            log.info("  You will be required to change this password and to give an email address");
            log.info("  the instance can write to on first login.");
        }
        log.info("  The instance has no station yet. After signing in you will be asked to found");
        log.info("  the first one.");
        log.info("==========================================================");
        return accountRepository.findById(accountId).orElse(account);
    }

    /**
     * The name to sign in with, moved out of the way of whoever already holds it. Nobody normally
     * does on an instance with no administrator, but an instance whose only administrator was
     * deleted comes through here again, with everybody else still on it.
     */
    private String freeLoginName() {
        if (!accountRepository.usernameTaken(ADMIN_LOGIN_NAME, null)) return ADMIN_LOGIN_NAME;
        String name;
        do {
            name = ADMIN_LOGIN_NAME + "-" + Integer.toString(RandomTokens.number(0x10000), 16);
        } while (accountRepository.usernameTaken(name, null));
        return name;
    }
}
