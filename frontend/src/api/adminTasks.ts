/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {components, TaskStatus} from './generated/schema'

type Schemas = components['schemas']

export type TaskOutcomeName = Schemas['TaskOutcome']

/** How the most recent run of a background task went. */
export const TaskOutcome = {
    NOT_RUN_YET: 'NOT_RUN_YET',
    RUNNING: 'RUNNING',
    SUCCEEDED: 'SUCCEEDED',
    FAILED: 'FAILED',
} as const satisfies Record<TaskOutcomeName, TaskOutcomeName>

export type ScheduleModeName = Schemas['ScheduleMode']

/** How the runs of a background task follow each other. */
export const ScheduleMode = {
    FIXED_DELAY: 'FIXED_DELAY',
    FIXED_RATE: 'FIXED_RATE',
    ONCE: 'ONCE',
} as const satisfies Record<ScheduleModeName, ScheduleModeName>

/**
 * Every scheduled background task with its last run, by name. Nothing of this is stored, so after a
 * restart every task starts again at {@link TaskOutcome.NOT_RUN_YET}.
 */
export async function listTasks(): Promise<TaskStatus[]> {
    const res = await client.get<TaskStatus[]>('/admin/tasks')
    return res.data
}
