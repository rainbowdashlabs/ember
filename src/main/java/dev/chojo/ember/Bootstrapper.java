/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.auth.SecretsInitializer;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.FirstAdministratorService;
import dev.chojo.ember.feature.beacon.service.BeaconReportService;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.StationKeyStore;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.quiz.service.AiCredentialService;
import dev.chojo.ember.feature.station.service.TransferTimeoutWatchdog;
import dev.chojo.ember.feature.system.service.ChangelogAnnouncer;
import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.system.service.PagePictureRedrawService;
import dev.chojo.ember.feature.system.service.SearchIndexRebuildService;
import dev.chojo.ember.feature.system.service.UpdateCheckService;
import dev.chojo.ember.lifecycle.Lifecycle;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.service.CloudflareRangesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Application entry point that initializes the Guice injector, runs database migrations,
 * seeds demo data or creates a default admin account, and starts the API server.
 *
 * <p>The query configuration and the domain event bus are fetched eagerly because creating them is
 * what makes {@code query(...)} work globally and registers every event handler. Whether this start
 * is an update is known only from what the previous start stored, so the changelog announcement runs
 * once the schema is certain and before the API is up.
 *
 * <p>Background work starts last: the scheduled tasks once the schema is certain, the demo data is in
 * place and the HTTP port is open, then the one-shot jobs (search index rebuild, Cloudflare ranges).
 * The shutdown hook is installed before any of it, so everything that starts is also stopped in order.
 */
public class Bootstrapper {
    private static final Logger log = LoggerFactory.getLogger(Bootstrapper.class);
    private static final Key<Set<TaskSource>> TASK_SOURCES = Key.get(new TypeLiteral<>() {});

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

    void main() {
        OutboundHttp.allowHostHeader();
        var conf = new Conf();
        SecretsInitializer.ensure(conf);
        var injector = Guice.createInjector(new EmberModule(conf));
        injector.getInstance(QueryConfiguration.class);
        injector.getInstance(StationKeyStore.class).sealLegacyKeys();
        injector.getInstance(AiCredentialService.class).sealLegacyStationKeys();
        injector.getInstance(DomainEventBus.class);
        injector.getInstance(TransferTimeoutWatchdog.class);

        injector.getInstance(DataInitializer.class).initialize();

        var demoService = injector.getInstance(DemoService.class);
        if (demoService.isEnabled()) {
            demoService.initialize();
        } else {
            injector.getInstance(FirstAdministratorService.class).createIfMissing();
            printAdminEnrollmentLinkIfAsked(
                    conf,
                    injector.getInstance(AccountRepository.class),
                    injector.getInstance(PasskeyEnrollmentService.class),
                    injector.getInstance(Api.class));
        }

        injector.getInstance(ConsentService.class).initialize();

        var updateCheck = injector.getInstance(UpdateCheckService.class);
        injector.getInstance(BeaconReportService.class).startForwarding(updateCheck.currentVersion());

        injector.getInstance(ChangelogAnnouncer.class).announce();

        var tasks = injector.getInstance(TASK_SOURCES).stream()
                .flatMap(source -> source.scheduledTasks().stream())
                .toList();
        var apiServer = injector.getInstance(ApiServer.class);
        apiServer.start();

        injector.getInstance(Lifecycle.class).installShutdownHook();
        var scheduler = injector.getInstance(TaskScheduler.class);
        scheduler.start(tasks);
        scheduler.background(
                "search-index-rebuild", injector.getInstance(SearchIndexRebuildService.class)::rebuildInBackground);
        scheduler.background(
                "page-picture-redraw", injector.getInstance(PagePictureRedrawService.class)::redrawInBackground);
        injector.getInstance(CloudflareRangesService.class).refreshAsync();
    }
}
