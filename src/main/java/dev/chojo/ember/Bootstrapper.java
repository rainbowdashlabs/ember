/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.Guice;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.SecretsInitializer;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.beacon.service.BeaconMetricsService;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.board.service.DueDateReminderChecker;
import dev.chojo.ember.feature.events.service.RegistrationDeadlineChecker;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.mailimport.service.MailImportPoller;
import dev.chojo.ember.feature.media.service.MediaPrefixMigrationService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.TransferTimeoutWatchdog;
import dev.chojo.ember.feature.system.service.ApplicationLogWriter;
import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import dev.chojo.ember.util.service.CloudflareRangesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Application entry point that initializes the Guice injector, runs database migrations,
 * seeds demo data or creates a default admin account, and starts the API server.
 */
public class Bootstrapper {
    private static final Logger log = LoggerFactory.getLogger(Bootstrapper.class);
    private static final String ADMIN_LOGIN_NAME = "admin";
    private static final String ADMIN_FIRST_NAME = "Admin";
    private static final String ADMIN_LAST_NAME = "Admin";

    /**
     * Creates the account that administers a brand new instance and a default station, unless
     * somebody already administers it. Logs what to sign in with: on an ordinary instance a
     * random password with a forced change at first sign-in, on a passwordless one no credential
     * row at all and a one-time enrolment link instead. Whoever can read the console is the
     * person installing the instance, and a link that can do nothing but create one passkey is
     * a smaller thing to leave lying in a log file than a working password.
     *
     * <p>The account is given a login name and no address at all. It used to be given a made-up
     * one ending in {@code .local}, which read as an address without being one: no password reset
     * reached it, no security notice did, and naming it as somebody's address elsewhere in the
     * application was refused as a collision, because a made-up address that is already taken is
     * two different people rather than one. A name is what an account signs in with when it has no
     * address of its own, which is exactly this account's position, and it is a truthful one: it
     * says the instance does not yet know where to write.
     *
     * <p>Where to write is then asked for at the first sign-in, beside the password, and there is
     * no session until it has been answered.
     */
    private static void createDefaultAdmin(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            PasskeyModeService passkeyModeService,
            PasskeyEnrollmentService enrollmentService,
            Api api) {
        if (accountRepository.anyAdministratorExists()) {
            return;
        }
        boolean passwordless = passkeyModeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
        String loginName = freeLoginName(accountRepository);

        var account = accountRepository.create(null, ADMIN_FIRST_NAME, ADMIN_LAST_NAME, false);
        int accountId = account.id();
        accountRepository.updateUsername(accountId, loginName);
        String password = null;
        if (!passwordless) {
            password = generatePassword();
            accountRepository.createCredential(accountId, passwordHasher.hash(password));
            accountRepository.setForcePasswordChange(accountId, true);
        }
        accountRepository.setInstanceUserType(accountId, InstanceUserType.ADMINISTRATOR);

        var station = stationRepository.create("default");
        stationMemberRepository.create(station.id(), accountId);

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
        log.info("  Default station '{}' created (id={})", station.name(), station.id());
        log.info("==========================================================");
    }

    /**
     * The rescue for the one lockout nobody can staff their way out of: when the flag is set,
     * every start prints a fresh one-time enrolment link for an administrator account and kills
     * the one before it. A link with no expiry printed into a shipped log would be a standing
     * key to the instance; this one lives an hour and dies on use.
     */
    private static void printAdminEnrollmentLinkIfAsked(
            Conf conf, AccountRepository accountRepository, PasskeyEnrollmentService enrollmentService, Api api) {
        if (!conf.main().auth().passkeys().printAdminEnrollmentLink()) return;
        var admin = accountRepository.findAnyAdministrator();
        if (admin.isEmpty()) {
            log.warn("printAdminEnrollmentLink is set, but no administrator account exists");
            return;
        }
        String code = enrollmentService.issueCode(admin.get().id(), PasskeyEnrollmentService.LINK_TTL);
        log.warn("==========================================================");
        log.warn(
                "  One-time passkey enrolment link for administrator account {} (lives one hour):",
                admin.get().id());
        log.warn("  {}/enroll?code={}", api.baseUrl(), code);
        log.warn("  Remove auth.passkeys.printAdminEnrollmentLink again after using it.");
        log.warn("==========================================================");
    }

    /**
     * The name to sign in with, moved out of the way of whoever already holds it. Nobody normally
     * does on an instance with no administrator, but an instance whose only administrator was
     * deleted comes through here again, with everybody else still on it.
     */
    private static String freeLoginName(AccountRepository accountRepository) {
        if (!accountRepository.usernameTaken(ADMIN_LOGIN_NAME, null)) return ADMIN_LOGIN_NAME;
        var random = new SecureRandom();
        String name;
        do {
            name = ADMIN_LOGIN_NAME + "-" + Integer.toString(random.nextInt(0x10000), 16);
        } while (accountRepository.usernameTaken(name, null));
        return name;
    }

    /**
     * Generates a cryptographically secure random password encoded as a URL-safe Base64 string.
     */
    private static String generatePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    void main() {
        var conf = new Conf();
        SecretsInitializer.ensure(conf);
        var injector = Guice.createInjector(new EmberModule(conf));
        // Eagerly initialize the query configuration so query(...) works globally
        injector.getInstance(QueryConfiguration.class);
        // Initialize domain event bus (registers all handlers)
        injector.getInstance(DomainEventBus.class);
        // Start registration deadline checker (daemon thread)
        injector.getInstance(RegistrationDeadlineChecker.class);
        // Start board due date reminder checker (daemon thread)
        injector.getInstance(DueDateReminderChecker.class);
        injector.getInstance(TransferTimeoutWatchdog.class);

        // Initialize data directory from templates if empty
        injector.getInstance(DataInitializer.class).initialize();

        // Demo mode: wipe and seed before starting
        var demoService = injector.getInstance(DemoService.class);
        if (demoService.isEnabled()) {
            demoService.initialize();
        } else {
            createDefaultAdmin(
                    injector.getInstance(AccountRepository.class),
                    injector.getInstance(PasswordHasher.class),
                    injector.getInstance(StationRepository.class),
                    injector.getInstance(StationMemberRepository.class),
                    injector.getInstance(PasskeyModeService.class),
                    injector.getInstance(PasskeyEnrollmentService.class),
                    injector.getInstance(Api.class));
            printAdminEnrollmentLinkIfAsked(
                    conf,
                    injector.getInstance(AccountRepository.class),
                    injector.getInstance(PasskeyEnrollmentService.class),
                    injector.getInstance(Api.class));
        }

        // Initialize legal document versioning (detect changes, archive old versions)
        injector.getInstance(ConsentService.class).initialize();

        injector.getInstance(CloudflareRangesService.class).refreshAsync();

        var updateCheck = injector.getInstance(UpdateCheckService.class);
        updateCheck.start();

        injector.getInstance(BeaconMetricsService.class).start(updateCheck.currentVersion());
        injector.getInstance(BeaconReportService.class).startForwarding(updateCheck.currentVersion());

        injector.getInstance(MailImportPoller.class).start();

        // Media moved from the page-files prefix onto media/. The move is per station and
        // resumable, so it runs off the boot path and picks up where it left off after a crash.
        var mediaMigration = injector.getInstance(MediaPrefixMigrationService.class);
        Thread.ofPlatform().daemon().name("media-prefix-migration").start(mediaMigration::migrateAll);

        // After the schema is certain: in demo mode the migration runs in DemoService, not above.
        injector.getInstance(ApplicationLogWriter.class).start();

        var apiServer = injector.getInstance(ApiServer.class);
        apiServer.start();
    }
}
