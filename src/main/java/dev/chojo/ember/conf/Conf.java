/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import dev.chojo.ember.conf.file.File;
import dev.chojo.ocular.Configurations;
import dev.chojo.ocular.dataformats.YamlDataFormat;
import dev.chojo.ocular.key.Key;

import java.nio.file.Path;
import java.util.List;

/**
 * Application configuration manager that loads and saves the YAML configuration file
 * from the {@code config/} directory using the Ocular library.
 */
public class Conf extends Configurations<File> {
    public static final Key<File> CONFIG =
            Key.builder(Path.of("config.yaml"), File::new).build();

    public Conf() {
        this(Path.of("config"));
    }

    /**
     * Reads the configuration from a directory of the caller's choosing.
     *
     * <p>The application always uses {@code config/} beside itself. A test that builds the container
     * names its own directory instead, so what it wires up is the configuration it wrote rather than
     * whatever the machine running it happens to hold: with the directory fixed, the same test asks a
     * different question on a developer's machine than on a build server, and passes in one place
     * while failing in the other.
     */
    public Conf(Path directory) {
        super(directory, CONFIG, List.of(new YamlDataFormat()), Conf.class.getClassLoader(), null);
    }
}
