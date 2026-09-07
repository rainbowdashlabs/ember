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
 * Configuration for the wiki that is the same for every station on an instance.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("KNOWLEDGE_BASE")
public class KnowledgeBase {

    private static final int MIN_TRASH_RETENTION_DAYS = 1;
    private static final int MAX_TRASH_RETENTION_DAYS = 365;

    /**
     * How many days a deleted wiki entry waits in the trash before it is cleared out for good.
     *
     * <p>What waits there still takes up the station's storage, because the files are still on disk,
     * so a longer window buys more room to change one's mind and costs space to do it. Read as at
     * least one day and at most a year: below that nothing is recoverable in practice, and above it
     * a trash stops being a trash.
     */
    @Overwrite(env = @Env)
    private int trashRetentionDays = 30;

    public int trashRetentionDays() {
        return Math.clamp(trashRetentionDays, MIN_TRASH_RETENTION_DAYS, MAX_TRASH_RETENTION_DAYS);
    }

    @Override
    public String toString() {
        return "KnowledgeBase{trashRetentionDays=" + trashRetentionDays() + '}';
    }
}
