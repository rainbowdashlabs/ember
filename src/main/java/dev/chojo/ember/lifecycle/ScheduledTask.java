/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

/**
 * One piece of periodic background work, run by the {@link TaskScheduler} once the instance has booted.
 *
 * <p>A task is registered through the {@code ScheduledTask} multibinder and never starts a thread of its
 * own. The scheduler runs it on a virtual thread, never lets it overlap itself, and catches and logs
 * whatever it throws, so the schedule survives a failed run.
 */
public interface ScheduledTask {

    /**
     * The name the task runs under: the thread name of each run, the key in the log and on the admin
     * task status page. Lower case words joined by hyphens, unique across the instance.
     *
     * @return the task's name
     */
    String name();

    /**
     * When the task runs.
     *
     * @return the task's schedule
     */
    Schedule schedule();

    /**
     * Performs one run. Whatever it throws is logged with the task's name and the next run happens as
     * planned.
     */
    void run();
}
