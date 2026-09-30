/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ocular.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfigChangesTest {

    @Test
    void aChangeIsAppliedAndSaved() {
        var conf = mock(Conf.class);
        var steps = new ArrayList<String>();

        new ConfigChanges(conf).apply(() -> steps.add("change"), () -> steps.add("restore"));

        assertEquals(List.of("change"), steps);
        verify(conf).save();
    }

    @Test
    void aChangeThatCannotBeSavedIsTakenBackAndRefused() {
        var conf = mock(Conf.class);
        doThrow(new ConfigurationException("disk full", new RuntimeException()))
                .when(conf)
                .save();
        var steps = new ArrayList<String>();

        var refused = assertThrows(RefusalResponse.class, () -> new ConfigChanges(conf)
                .apply(() -> steps.add("change"), () -> steps.add("restore")));

        assertEquals(Refusal.SETTINGS_NOT_SAVED, refused.refusal());
        assertEquals(List.of("change", "restore"), steps);
    }

    @Test
    void aSavedChangeIsWhatTheFileHoldsWhenReadAgain(@TempDir Path directory) {
        var conf = new Conf(directory);
        var theming = conf.main().theming();

        new ConfigChanges(conf).apply(() -> theming.defaultTheme("forest"), () -> theming.defaultTheme("ember"));

        assertEquals("forest", new Conf(directory).main().theming().defaultTheme());
    }
}
