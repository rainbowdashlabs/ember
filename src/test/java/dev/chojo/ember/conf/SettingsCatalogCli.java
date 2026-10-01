/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the settings catalog for {@code ./toolchain.sh be-settings-catalog}, the file the help centre's list of
 * environment variables is rendered from.
 */
public final class SettingsCatalogCli {

    /** Where the catalog is committed. */
    static final Path CATALOG_PATH = Path.of("frontend/src/data/generated/settings.json");

    private SettingsCatalogCli() {}

    static void main(String[] args) throws IOException {
        Path target = args.length > 0 ? Path.of(args[0]) : CATALOG_PATH;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, SettingsCatalog.render());
        System.out.println("Wrote " + target.toAbsolutePath());
    }
}
