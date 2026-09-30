/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ocular.exceptions.ConfigurationException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Changes the running configuration and writes it to the file, as one step.
 *
 * <p>Changes are made one at a time, so two administrators saving at once cannot interleave their
 * values. A change is validated before it gets here and applied through the typed setters of the
 * configuration elements, which cannot fail; what can fail is writing the file. When it does, the
 * change is taken back, so the instance keeps running on what the file says, and the request is
 * refused instead of answering as though it had been saved.
 */
@Singleton
public class ConfigChanges {
    private static final Logger log = LoggerFactory.getLogger(ConfigChanges.class);

    private final Conf conf;

    @Inject
    public ConfigChanges(Conf conf) {
        this.conf = conf;
    }

    /**
     * Applies a change and saves the configuration file.
     *
     * @param change  sets the new values
     * @param restore sets the values back to what they were before the change
     */
    public synchronized void apply(Runnable change, Runnable restore) {
        change.run();
        try {
            conf.save();
        } catch (ConfigurationException e) {
            log.error("The configuration file could not be written, the change is taken back", e);
            restore.run();
            throw Refusal.SETTINGS_NOT_SAVED.raise();
        }
    }
}
