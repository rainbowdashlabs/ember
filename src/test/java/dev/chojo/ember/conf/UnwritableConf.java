/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import dev.chojo.ember.conf.file.File;
import dev.chojo.ocular.exceptions.ConfigurationException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A configuration holding the defaults in memory whose file can never be written, for the path
 * where a settings change has to be taken back.
 */
public final class UnwritableConf {
    private UnwritableConf() {}

    /**
     * A configuration whose every save fails.
     *
     * @return the configuration
     */
    public static Conf create() {
        var conf = mock(Conf.class);
        var file = new File();
        when(conf.main()).thenReturn(file);
        doThrow(new ConfigurationException("the file is read-only", new RuntimeException()))
                .when(conf)
                .save();
        return conf;
    }
}
