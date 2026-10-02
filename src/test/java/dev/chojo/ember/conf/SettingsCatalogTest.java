/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import dev.chojo.ember.conf.SettingsCatalog.Setting;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class SettingsCatalogTest {

    @OverwritePrefix("NOTE")
    static final class Plain {
        @Overwrite(env = @Env)
        private int retentionDays = 7;

        @Overwrite(env = @Env("NOTE_CUSTOM"))
        private String named = "";

        @Overwrite(env = @Env)
        private ThemeFeel feel = ThemeFeel.ROUNDED;

        @Overwrite(env = @Env)
        private List<String> hosts = List.of("a", "b");

        private Map<String, String> extras = Map.of();
    }

    @OverwritePrefix(value = "FORCED", force = true)
    static final class Forced {
        @Overwrite(env = @Env("NAME"))
        private boolean flag = true;
    }

    static final class Unprefixed {
        @Overwrite(env = @Env)
        private long size = 5L;
    }

    /**
     * A setting added, renamed or given another default without a regenerated catalog fails here, the way a
     * route change without a regenerated description fails the API spec test.
     */
    @Test
    void committedCatalogIsWhatAGenerationWrites() throws IOException {
        String committed = Files.readString(SettingsCatalogCli.CATALOG_PATH);
        if (!committed.equals(SettingsCatalog.render())) {
            fail(SettingsCatalogCli.CATALOG_PATH + " differs from what a generation writes. Run"
                    + " ./toolchain.sh be-settings-catalog and commit the result.");
        }
    }

    @Test
    void aSettingCarriesItsKeyVariableAndDefault() {
        assertEquals(
                List.of(
                        new Setting("retentionDays", "NOTE_RETENTIONDAYS", "7"),
                        new Setting("named", "NOTE_CUSTOM", "-"),
                        new Setting("feel", null, "ROUNDED"),
                        new Setting("hosts", "NOTE_HOSTS", "a,b"),
                        new Setting("extras", null, "{}")),
                SettingsCatalog.settingsOf(new Plain()));
    }

    @Test
    void aForcedPrefixPrecedesANamedVariable() {
        assertEquals(List.of(new Setting("flag", "FORCED_NAME", "true")), SettingsCatalog.settingsOf(new Forced()));
    }

    @Test
    void aClassWithoutPrefixLendsItsName() {
        assertEquals(
                List.of(new Setting("size", "UNPREFIXED_SIZE", "5")), SettingsCatalog.settingsOf(new Unprefixed()));
    }

    @Test
    void sectionsAreWalkedIntoAndKeysJoinedByDots() {
        var settings = SettingsCatalog.settings();

        assertEquals(
                new Setting("database.host", "DB_HOST", "localhost"),
                settings.stream()
                        .filter(setting -> setting.configKey().equals("database.host"))
                        .findFirst()
                        .orElseThrow());
        assertNull(settings.stream()
                .filter(setting -> setting.configKey().equals("auth.hibp.enabled"))
                .findFirst()
                .orElseThrow()
                .env());
    }
}
