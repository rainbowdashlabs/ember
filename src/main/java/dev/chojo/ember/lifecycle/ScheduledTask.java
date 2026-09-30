/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

/**
 * One piece of periodic background work, contributed by a {@link TaskSource}.
 *
 * <p>The {@link TaskScheduler} runs it on a virtual thread, never lets it overlap itself and logs whatever it
 * throws, so a failed run does not end the schedule.
 *
 * @param name     the thread name of each run and the key in the log and on the task status page: lower case
 *                 words joined by hyphens, unique across the instance
 * @param schedule when it runs
 * @param work     what one run does
 */
public record ScheduledTask(String name, Schedule schedule, Runnable work) {}
