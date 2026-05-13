/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.Guice;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.repository.StationRepository;
import dev.chojo.ember.service.DemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

public class Bootstrapper {
    private static final Logger log = LoggerFactory.getLogger(Bootstrapper.class);
    private static final String ADMIN_EMAIL = "admin@ember.local";
    private static final String ADMIN_FIRST_NAME = "Admin";
    private static final String ADMIN_LAST_NAME = "Admin";

    private static void createDefaultAdmin(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository) {
        if (accountRepository.anyAccountHasRole("ADMIN")) {
            return;
        }

        String password = generatePassword();
        String hash = passwordHasher.hash(password);

        var account = accountRepository.create(ADMIN_EMAIL, ADMIN_FIRST_NAME, ADMIN_LAST_NAME, true);
        int accountId = account.id();
        accountRepository.createCredential(accountId, hash);
        accountRepository.setForcePasswordChange(accountId, true);
        accountRepository.addAccountRole(accountId, "ADMIN");

        var station = stationRepository.create("default");
        stationMemberRepository.create(station.id(), accountId);

        log.info("==========================================================");
        log.info("  Default admin account created");
        log.info("  Email:    {}", ADMIN_EMAIL);
        log.info("  Password: {}", password);
        log.info("  You will be required to change this password on first login.");
        log.info("  Default station '{}' created (id={})", station.name(), station.id());
        log.info("==========================================================");
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    void main() {
        var conf = new Conf();
        var injector = Guice.createInjector(new EmberModule(conf));
        // Eagerly initialize the query configuration so Query.query(...) works globally
        injector.getInstance(QueryConfiguration.class);

        // Demo mode: wipe and seed before starting
        var demoService = injector.getInstance(DemoService.class);
        if (demoService.isEnabled()) {
            demoService.initialize();
        } else {
            createDefaultAdmin(
                    injector.getInstance(AccountRepository.class),
                    injector.getInstance(PasswordHasher.class),
                    injector.getInstance(StationRepository.class),
                    injector.getInstance(StationMemberRepository.class));
        }

        var apiServer = injector.getInstance(ApiServer.class);
        apiServer.start();
    }
}
