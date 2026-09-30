/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** How the most recent run of a background task went. */
export const TaskOutcome = {
    NOT_RUN_YET: 'NOT_RUN_YET',
    RUNNING: 'RUNNING',
    SUCCEEDED: 'SUCCEEDED',
    FAILED: 'FAILED',
} as const

export type TaskOutcomeName = (typeof TaskOutcome)[keyof typeof TaskOutcome]

/** How the runs of a background task follow each other. */
export const ScheduleMode = {
    FIXED_DELAY: 'FIXED_DELAY',
    FIXED_RATE: 'FIXED_RATE',
    ONCE: 'ONCE',
} as const

export type ScheduleModeName = (typeof ScheduleMode)[keyof typeof ScheduleMode]

/**
 * One background task as the instance knows it since its last start. Nothing of this is stored, so
 * after a restart every task starts again at {@link TaskOutcome.NOT_RUN_YET}.
 */
export interface TaskStatus {
    name: string
    mode: ScheduleModeName
    /** The distance between two runs, 0 for a task that runs once. */
    periodSeconds: number
    outcome: TaskOutcomeName
    lastStartedAt: string | null
    lastDurationMs: number | null
    runs: number
    failures: number
    lastFailureAt: string | null
    lastFailureMessage: string | null
}

/** Every scheduled background task with its last run, by name. */
export async function listTasks(): Promise<TaskStatus[]> {
    const res = await client.get<TaskStatus[]>('/admin/tasks')
    return res.data
}
