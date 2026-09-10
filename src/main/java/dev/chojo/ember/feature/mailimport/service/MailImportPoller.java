/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The one thread that visits the mailboxes.
 *
 * <p>One thread walking them in turn is enough for what this does, and it is the shape that keeps a
 * misconfigured mailbox from becoming a fleet of threads holding connections open to a provider that is
 * already unhappy. What makes it safe is not the thread count but the timeouts: without a bound on
 * connecting and reading, one host that neither fails nor answers would hold every other station's
 * import for as long as its socket took to give up.
 */
@Singleton
public class MailImportPoller {
    private static final Logger log = LoggerFactory.getLogger(MailImportPoller.class);
    private static final int TICK_MINUTES = 1;

    private final MailMailboxRepository mailboxRepository;
    private final MailImportLogRepository logRepository;
    private final MailImportService importService;
    private final MailImport settings;

    @Inject
    public MailImportPoller(
            MailMailboxRepository mailboxRepository,
            MailImportLogRepository logRepository,
            MailImportService importService,
            MailImport settings) {
        this.mailboxRepository = mailboxRepository;
        this.logRepository = logRepository;
        this.importService = importService;
        this.settings = settings;
    }

    /**
     * Starts the walk, unless the operator has switched the whole thing off.
     *
     * <p>The tick is a minute and the interval is a quarter of an hour, because the tick does not decide
     * anything: it asks each mailbox whether it is due. Ticking often and visiting rarely is what lets a
     * newly added mailbox be read within the minute without anybody being allowed to poll a provider that
     * often.
     */
    public void start() {
        if (!settings.enabled()) {
            log.info("Reading mail for documents is switched off for this instance");
            return;
        }
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "mail-import");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, TICK_MINUTES, TICK_MINUTES, TimeUnit.MINUTES);
        executor.scheduleWithFixedDelay(this::prune, 1, 24 * 60L, TimeUnit.MINUTES);
        log.info(
                "Reading mail for documents every {} minutes at the soonest, {} attachments a cycle",
                settings.minimumIntervalMinutes(),
                settings.maxAttachmentsPerCycle());
    }

    /**
     * One turn of the walk.
     *
     * <p>Whatever goes wrong here goes wrong on a daemon thread nobody is watching, so it is written down
     * rather than thrown: an exception escaping a scheduled task stops the task for good, and an import
     * that quietly stopped a month ago is worse than one that logs a failure every quarter of an hour.
     */
    void tick() {
        try {
            Instant now = Instant.now();
            for (MailMailbox mailbox : mailboxRepository.findPollable()) {
                if (!MailImportSchedule.due(mailbox, settings.minimumIntervalMinutes(), now)) continue;
                importService.run(mailbox, now);
            }
        } catch (Exception e) {
            log.warn("A turn of the mail import could not be finished", e);
        }
    }

    /**
     * Clears the readable half of the log once a day, leaving the duplicate keys.
     *
     * <p>The keys are not touched, so a mail redelivered after its entry was pruned is still recognised
     * rather than filed a second time.
     */
    void prune() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(settings.logRetentionDays()));
            int reduced = logRepository.pruneReadableBefore(cutoff);
            if (reduced > 0) {
                log.info(
                        "Reduced {} import log entries older than {} days to their keys",
                        reduced,
                        settings.logRetentionDays());
            }
        } catch (Exception e) {
            log.warn("The import log could not be pruned", e);
        }
    }
}
