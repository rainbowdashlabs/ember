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
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The one task that visits the mailboxes.
 *
 * <p>One run walking them in turn is enough for what this does, and it is the shape that keeps a
 * misconfigured mailbox from becoming a fleet of threads holding connections open to a provider that is
 * already unhappy. What makes it safe is not the thread count but the timeouts: without a bound on
 * connecting and reading, one host that neither fails nor answers would hold every other station's
 * import for as long as its socket took to give up.
 */
@Singleton
public class MailImportPoller implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(MailImportPoller.class);
    private static final Duration TICK = Duration.ofMinutes(1);

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

    /**
     * The walk every minute and the log pruning once a day, both only while the import is switched on. The
     * tick decides nothing but asks each mailbox whether it is due, so a new mailbox is read within the minute
     * while no provider is polled that often. Logs once whether the import is on.
     */
    @Override
    public List<ScheduledTask> scheduledTasks() {
        if (settings.enabled()) {
            log.info(
                    "Reading mail for documents every {} minutes at the soonest, {} attachments a cycle",
                    settings.minimumIntervalMinutes(),
                    settings.maxAttachmentsPerCycle());
        } else {
            log.info("Reading mail for documents is switched off for this instance");
        }
        return List.of(
                new ScheduledTask("mail-import", Schedule.fixedDelay(TICK, TICK), () -> {
                    if (settings.enabled()) tick();
                }),
                new ScheduledTask(
                        "mail-import-prune", Schedule.fixedDelay(Duration.ofMinutes(1), Duration.ofDays(1)), () -> {
                            if (settings.enabled()) prune();
                        }));
    }
}
