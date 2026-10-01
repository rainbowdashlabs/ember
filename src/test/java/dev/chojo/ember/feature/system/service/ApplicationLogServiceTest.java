/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.feature.system.entity.LogFacet;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LogFilter;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LoggingConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationLogServiceTest {
    @TempDir
    Path directory;

    private ApplicationLogRepository logs;
    private ApplicationLogService service;

    @BeforeEach
    void setup() {
        logs = mock(ApplicationLogRepository.class);
        var conf = new Conf(directory);
        service = new ApplicationLogService(logs, conf, new ConfigChanges(conf));
    }

    @Test
    void thePageAsksForTheKnownLevelsOnlyWithTheCursorAndAHeldLimit() {
        var filter = new LogFilter(" warn, nonsense,ERROR,", "boom", "dev.chojo", "worker");

        var page = service.page(filter, " 42 ", 9000);

        verify(logs).search(List.of("WARN", "ERROR"), "boom", "dev.chojo", "worker", 42L, 500);
        verify(logs).loggerFacets(List.of("WARN", "ERROR"), "boom", "worker", null, 20);
        verify(logs).threadFacets(List.of("WARN", "ERROR"), "boom", "dev.chojo", null, 20);
        assertFalse(page.databaseEnabled());
        assertEquals("DEBUG", page.databaseLevel());
    }

    @Test
    void anUnreadableCursorStartsAtTheTop() {
        service.page(new LogFilter(null, null, null, null), "yesterday", 0);
        service.page(new LogFilter(null, null, null, null), " ", 10);

        verify(logs).search(List.of(), null, null, null, null, 1);
        verify(logs).search(List.of(), null, null, null, null, 10);
    }

    @Test
    void facetsAreLoggersUnlessThreadsAreAskedFor() {
        var facet = List.of(new LogFacet("worker-#", 3));
        when(logs.threadFacets(any(), any(), any(), any(), anyInt())).thenReturn(facet);
        var filter = new LogFilter("INFO", "s", "l", "t");

        assertEquals(facet, service.facets(filter, true, "work", 500));
        service.facets(filter, false, null, 0);

        verify(logs).threadFacets(List.of("INFO"), "s", "l", "work", 200);
        verify(logs).loggerFacets(List.of("INFO"), "s", "t", null, 1);
        assertEquals(20, service.defaultFacetLimit());
    }

    @Test
    void clearingEmptiesTheStoredLog() {
        service.clear();

        verify(logs).clear();
    }

    @Test
    void theLoggingSettingsAreWrittenToTheFileWithTheLevelInCapitals() {
        when(logs.size()).thenReturn(7);

        var answer = service.updateConfig(new LoggingConfigRequest(true, "warn", 30));

        var logging = new Conf(directory).main().logging();
        assertTrue(logging.databaseEnabled());
        assertEquals("WARN", logging.databaseLevel());
        assertEquals(30, logging.retentionDays());
        assertEquals(7, answer.storedLines());
    }

    @Test
    void noLevelMeansDebugAndAnUnknownOneIsRefused() {
        assertEquals(
                "DEBUG",
                service.updateConfig(new LoggingConfigRequest(true, null, 30)).databaseLevel());

        var refused = assertThrows(
                RefusalResponse.class, () -> service.updateConfig(new LoggingConfigRequest(true, "LOUD", 30)));
        assertEquals(Refusal.LOG_LEVEL_UNKNOWN, refused.refusal());
        var outOfRange = assertThrows(
                RefusalResponse.class, () -> service.updateConfig(new LoggingConfigRequest(true, "INFO", 0)));
        assertEquals(Refusal.SETTING_OUT_OF_RANGE, outOfRange.refusal());
    }

    @Test
    void aChangeTheFileCannotTakeIsTakenBack() {
        var unwritable = UnwritableConf.create();
        var failing = new ApplicationLogService(logs, unwritable, new ConfigChanges(unwritable));

        var refused = assertThrows(
                RefusalResponse.class, () -> failing.updateConfig(new LoggingConfigRequest(true, "ERROR", 30)));

        assertEquals(Refusal.SETTINGS_NOT_SAVED, refused.refusal());
        assertFalse(failing.config().databaseEnabled());
        assertEquals(14, failing.config().retentionDays());
    }
}
