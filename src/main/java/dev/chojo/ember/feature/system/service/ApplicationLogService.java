/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.feature.system.entity.LogFacet;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository.LogEntry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The copy of the application log kept in the database: reading and searching it, emptying it,
 * and how much of it is kept.
 *
 * <p>Only what the operator chose to keep in the database is here. The console and the file always
 * hold everything, which is what makes this safe to switch off.
 */
@Singleton
public class ApplicationLogService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLogService.class);

    /** The severities a client may ask for, so an unknown one is refused rather than ignored. */
    private static final Set<String> LOG_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    /** How many loggers and threads are offered to pick from. Beyond this the list stops helping. */
    private static final int FACET_LIMIT = 20;

    private final ApplicationLogRepository logs;
    private final Conf conf;
    private final ConfigChanges changes;

    @Inject
    public ApplicationLogService(ApplicationLogRepository logs, Conf conf, ConfigChanges changes) {
        this.logs = logs;
        this.conf = conf;
        this.changes = changes;
    }

    /**
     * A page of the log, newest first, narrowed by the filter.
     *
     * @param filter what the reader narrowed the log to
     * @param before the paging cursor as the client sent it; one that does not read as a number
     *               starts at the top, since it only serves reading further back
     * @param limit  how many lines at most, held between 1 and 500
     */
    public ApplicationLogPage page(LogFilter filter, @Nullable String before, int limit) {
        List<String> levels = filter.levelList();
        var logging = conf.main().logging();
        return new ApplicationLogPage(
                logs.search(
                        levels,
                        filter.search(),
                        filter.logger(),
                        filter.thread(),
                        cursor(before),
                        Math.clamp(limit, 1, 500)),
                logs.loggerFacets(levels, filter.search(), filter.thread(), null, FACET_LIMIT),
                logs.threadFacets(levels, filter.search(), filter.logger(), null, FACET_LIMIT),
                logging.databaseEnabled(),
                logging.databaseLevel(),
                logging.retentionDays(),
                DatabaseLogAppender.dropped());
    }

    /**
     * The loggers or threads the filter matches, searched by name.
     *
     * <p>Separate from the log itself so that typing in the list of loggers does not fetch the log
     * again, and so that one below the top of the list can still be reached.
     *
     * @param threads whether threads rather than loggers are wanted
     * @param name    a fragment of the name, or null for all
     * @param limit   how many at most, held between 1 and 200
     */
    public List<LogFacet> facets(LogFilter filter, boolean threads, @Nullable String name, int limit) {
        int held = Math.clamp(limit, 1, 200);
        return threads
                ? logs.threadFacets(filter.levelList(), filter.search(), filter.logger(), name, held)
                : logs.loggerFacets(filter.levelList(), filter.search(), filter.thread(), name, held);
    }

    /** How many loggers and threads a reader is offered when they ask for no other number. */
    public int defaultFacetLimit() {
        return FACET_LIMIT;
    }

    /**
     * Empties the stored log, for when it holds something that should not be kept.
     */
    public void clear() {
        logs.clear();
        log.info("The stored application log was cleared by an administrator");
    }

    public LoggingConfig config() {
        var logging = conf.main().logging();
        return new LoggingConfig(
                logging.databaseEnabled(), logging.databaseLevel(), logging.retentionDays(), logs.size());
    }

    public LoggingConfig updateConfig(LoggingConfigRequest request) {
        String level = request.databaseLevel() == null
                ? "DEBUG"
                : request.databaseLevel().toUpperCase(Locale.ROOT);
        if (!LOG_LEVELS.contains(level)) {
            throw Refusal.LOG_LEVEL_UNKNOWN.raise(request.databaseLevel());
        }
        SecuritySettingsService.requireRange(request.retentionDays(), 1, 3650, "retentionDays");
        var logging = conf.main().logging();
        var after = new LoggingConfigRequest(request.databaseEnabled(), level, request.retentionDays());
        var before = LoggingConfigRequest.of(logging);
        changes.apply(() -> after.applyTo(logging), () -> before.applyTo(logging));
        return config();
    }

    private static @Nullable Long cursor(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * What a reader narrowed the log to.
     *
     * @param levels the severities as the client sent them, separated by commas; unknown ones are
     *               dropped
     * @param search a fragment of the message, or null for all
     * @param logger one logger, or null for all
     * @param thread one thread, numbered off, or null for all
     */
    public record LogFilter(
            @Nullable String levels,
            @Nullable String search,
            @Nullable String logger,
            @Nullable String thread) {
        List<String> levelList() {
            String raw = levels == null ? "" : levels;
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .filter(LOG_LEVELS::contains)
                    .toList();
        }
    }

    /**
     * A page of the log, with what the reader needs to make sense of a short one.
     *
     * @param entries         the lines, newest first
     * @param loggers         the loggers the current filter matches, so a reader can narrow to one
     * @param threads         the same for threads, numbered off so a pool is one entry
     * @param databaseEnabled whether anything is being stored at all
     * @param databaseLevel   the lowest severity being stored
     * @param retentionDays   how long lines are kept
     * @param dropped         how many lines were dropped since start because the queue was full,
     *                        which is what says the log is incomplete rather than quiet
     */
    public record ApplicationLogPage(
            List<LogEntry> entries,
            List<LogFacet> loggers,
            List<LogFacet> threads,
            boolean databaseEnabled,
            String databaseLevel,
            int retentionDays,
            long dropped) {}

    /**
     * @param storedLines how many lines are stored, so an operator can see what a retention change
     *                    would act on
     */
    public record LoggingConfig(boolean databaseEnabled, String databaseLevel, int retentionDays, int storedLines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoggingConfigRequest(boolean databaseEnabled, String databaseLevel, int retentionDays) {
        static LoggingConfigRequest of(Logging logging) {
            return new LoggingConfigRequest(
                    logging.databaseEnabled(), logging.databaseLevel(), logging.retentionDays());
        }

        void applyTo(Logging logging) {
            logging.databaseEnabled(databaseEnabled);
            logging.databaseLevel(databaseLevel);
            logging.retentionDays(retentionDays);
        }
    }
}
