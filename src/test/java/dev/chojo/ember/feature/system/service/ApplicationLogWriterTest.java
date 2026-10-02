/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path from a log call to a row, and the two things that must never happen along it: writing a
 * line about writing a line, and keeping lines nobody asked to keep.
 */
class ApplicationLogWriterTest extends RepositoryTestBase {

    private final ApplicationLogRepository repository = new ApplicationLogRepository();
    private DatabaseLogAppender appender;

    private static Logging config(boolean enabled, String level, int retentionDays) {
        var logging = new Logging();
        set(logging, "databaseEnabled", enabled);
        set(logging, "databaseLevel", level);
        set(logging, "retentionDays", retentionDays);
        return logging;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void log(String loggerName, Level level, String message) {
        var event = new LoggingEvent();
        event.setLoggerName(loggerName);
        event.setLevel(level);
        event.setMessage(message);
        event.setThreadName("test");
        event.setTimeStamp(System.currentTimeMillis());
        appender.doAppend(event);
    }

    @BeforeEach
    void setUp() {
        repository.clear();
        DatabaseLogAppender.discard();
        DatabaseLogAppender.draining(false);
        appender = new DatabaseLogAppender();
        appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
    }

    /**
     * A line written to the appender turns up in the database.
     *
     * <p>What is asserted is that line rather than the size of the table. The appender takes whatever
     * else the process logs while this runs, so counting every row would make the test a report on how
     * chatty the rest of the suite happens to be that day.
     */
    @Test
    void aLineTravelsFromTheAppenderIntoTheDatabase() throws Exception {
        log("dev.chojo.ember.Something", Level.INFO, "it happened");
        var writer = new ApplicationLogWriter(config(true, "DEBUG", 14), repository);

        writer.scheduledTasks().get(0).work().run();

        var stored = repository.search(List.of(), null, null, null, null, 50);
        assertTrue(
                stored.stream().anyMatch(line -> "it happened".equals(line.message())),
                "the line the test wrote went through the appender into the database");
    }

    /**
     * Writing a line goes through the persistence layer, which logs. Capturing those would describe
     * the act of capturing, without end.
     */
    @Test
    void thePersistenceLayerIsNeverCaptured() {
        log("de.chojo.sadu.queries.Something", Level.INFO, "a query ran");
        log("com.zaxxer.hikari.pool.HikariPool", Level.INFO, "a connection was handed out");
        log(ApplicationLogWriter.class.getName(), Level.INFO, "the writer said something");

        assertTrue(DatabaseLogAppender.drain(10).isEmpty(), "none of these may be queued");
    }

    @Test
    void nothingIsKeptWhileTheDatabaseLogIsOff() throws Exception {
        log("dev.chojo.ember.Something", Level.INFO, "not wanted");
        var writer = new ApplicationLogWriter(config(false, "DEBUG", 14), repository);

        writer.flushAll();

        assertTrue(repository.search(List.of(), null, null, null, null, 10).isEmpty());
        assertTrue(DatabaseLogAppender.drain(10).isEmpty(), "and the queue is emptied rather than grown");
    }

    @Test
    void linesBelowTheChosenLevelAreDropped() throws Exception {
        log("dev.chojo.ember.Something", Level.DEBUG, "chatter");
        log("dev.chojo.ember.Something", Level.ERROR, "trouble");
        var writer = new ApplicationLogWriter(config(true, "WARN", 14), repository);

        writer.flushAll();

        var stored = repository.search(List.of(), null, null, null, null, 10);
        assertEquals(1, stored.size());
        assertEquals("trouble", stored.getFirst().message());
    }

    @Test
    void pruningRemovesWhatIsPastRetentionAndOnlyWhileTheLogIsOn() {
        repository.write(List.of(new DatabaseLogAppender.LogLine(
                java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS),
                "INFO",
                "dev.chojo.ember.A",
                "test",
                "ancient",
                null)));

        new ApplicationLogWriter(config(false, "DEBUG", 14), repository).pruneNow();
        assertEquals(1, repository.size(), "nothing is touched while the database log is off");

        new ApplicationLogWriter(config(true, "DEBUG", 14), repository)
                .scheduledTasks()
                .get(1)
                .work()
                .run();
        assertEquals(0, repository.size(), "and removed once it is on");
    }

    /**
     * A database that cannot be written to must not produce a line about not being able to write.
     * The writer swallows its own failures rather than feeding them back into the queue.
     */
    @Test
    void aFailingDatabaseDoesNotBringTheWriterDown() {
        var failing = new ApplicationLogRepository() {
            @Override
            public void write(List<DatabaseLogAppender.LogLine> lines) {
                throw new IllegalStateException("no database");
            }

            @Override
            public int prune(int keepDays) {
                throw new IllegalStateException("no database");
            }
        };
        var writer = new ApplicationLogWriter(config(true, "DEBUG", 14), failing);

        log("dev.chojo.ember.Something", Level.INFO, "will not be written");
        writer.pruneNow();
        writer.flushAll();

        assertFalse(Thread.currentThread().isInterrupted(), "and nothing was thrown out of it");
    }

    /**
     * The start of an instance happens once. Until something is writing lines away, a full queue
     * keeps what it has rather than making room by forgetting how the instance came up.
     */
    @Test
    void theStartOfTheLogSurvivesAFullQueue() {
        for (int i = 0; i < 10_100; i++) {
            log("dev.chojo.ember.Something", Level.INFO, "line " + i);
        }

        var kept = DatabaseLogAppender.drain(20_000);

        assertEquals("line 0", kept.getFirst().message(), "the first line of the run is still there");
        assertTrue(DatabaseLogAppender.dropped() > 0, "and what did not fit was counted");
    }

    /**
     * The shutdown flush takes the whole queue rather than the one batch a periodic run takes, and it
     * goes last so the lines the other flushes log are in the queue by then.
     */
    @Test
    void theShutdownFlushDrainsTheWholeQueueLast() {
        for (int i = 0; i < 1_200; i++) {
            log("dev.chojo.ember.Something", Level.INFO, "line " + i);
        }
        var writer = new ApplicationLogWriter(config(true, "DEBUG", 14), repository);

        writer.flushAll();

        assertTrue(repository.size() >= 1_200, "more than two batches were written");
        assertTrue(DatabaseLogAppender.drain(10).isEmpty(), "and nothing is left in the queue");
        assertEquals(Integer.MAX_VALUE, writer.order());
        assertEquals("application log", writer.name());
    }
}
