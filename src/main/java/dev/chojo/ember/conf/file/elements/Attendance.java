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
 * Configuration for attendance that is the same for every station on an instance.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("ATTENDANCE")
public class Attendance {

    private static final int MIN_FREEZE_AFTER_DAYS = 1;
    private static final int MAX_FREEZE_AFTER_DAYS = 365;

    /**
     * How many days after its evening an attendance sheet stays open before it freezes.
     *
     * <p>A sheet that anybody may still change months later is not a record of the evening, and a
     * sheet that closes the next morning is useless to a station that writes its evenings up at the
     * weekend. Whoever manages attendance can unlock a frozen sheet for the same span again, so this
     * decides how long the ordinary case lasts, not what is still possible. Read as at least a day
     * and at most a year.
     */
    @Overwrite(env = @Env)
    private int freezeAfterDays = 7;

    public int freezeAfterDays() {
        return Math.clamp(freezeAfterDays, MIN_FREEZE_AFTER_DAYS, MAX_FREEZE_AFTER_DAYS);
    }

    @Override
    public String toString() {
        return "Attendance{freezeAfterDays=" + freezeAfterDays() + '}';
    }
}
