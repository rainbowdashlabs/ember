/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

/**
 * Configuration for messages between people.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("MESSAGES")
public class Messages {

    /**
     * The hour of the day, 0 to 23, at which everybody is sent the daily mail about the messages they
     * have not read yet. It is read in the server's time zone, which the shipped image leaves at UTC
     * unless {@code TZ} is set. One hour for the whole instance, since messages belong to accounts and
     * not to a station with mail times of its own.
     */
    @Overwrite(env = @Env)
    private int digestHour = 18;

    /**
     * The hour the daily mail goes out, held to a valid hour of the day.
     *
     * @return the hour, 0 to 23
     */
    public int digestHour() {
        return Math.clamp(digestHour, 0, 23);
    }

    @Override
    public String toString() {
        return "Messages{digestHour=" + digestHour + '}';
    }
}
