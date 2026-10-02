/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import java.util.List;

/**
 * A class that owns periodic background work, registered through the {@code TaskSource} multibinder. It
 * declares its tasks and never starts a thread of its own.
 */
public interface TaskSource {

    /**
     * The tasks this class contributes, read once when the scheduler starts.
     *
     * @return the tasks
     */
    List<ScheduledTask> scheduledTasks();
}
