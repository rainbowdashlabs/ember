/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

/**
 * A {@link ScheduledTask} whose name, schedule and body are handed in when it is built.
 *
 * <p>The shape for a class that owns a second schedule, or that does more than its sweep: it keeps the
 * work method and contributes a small nested task that calls it, for example
 * {@code super("email-cleanup", Schedule.fixedRate(...), emailService::runCleanup)}.
 */
public abstract class DelegatingTask implements ScheduledTask {
    private final String name;
    private final Schedule schedule;
    private final Runnable body;

    /**
     * Creates the task.
     *
     * @param name     the task's name
     * @param schedule when it runs
     * @param body     what one run does
     */
    protected DelegatingTask(String name, Schedule schedule, Runnable body) {
        this.name = name;
        this.schedule = schedule;
        this.body = body;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final Schedule schedule() {
        return schedule;
    }

    @Override
    public final void run() {
        body.run();
    }
}
