/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {TaskStatus} from './generated/schema'

/**
 * Every scheduled background task with its last run, by name. Nothing of this is stored, so after a
 * restart every task starts again as not run yet.
 */
export async function listTasks(): Promise<TaskStatus[]> {
    const res = await client.get<TaskStatus[]>('/admin/tasks')
    return res.data
}
