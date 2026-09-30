/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {TaskOutcome, type TaskStatus} from '@/api/adminTasks'
import {intervalLabel, taskColumns} from './taskColumns'

const t = (key: string, named?: Record<string, unknown>) => named ? `${key}:${String(named.n)}` : key

/** How often a task runs reads in the largest unit that fits, and a one-shot task says so. */
describe('intervalLabel', () => {
    it('picks the largest unit that divides the period evenly', () => {
        expect(intervalLabel(21_600, t)).toBe('adminTasks.everyHours:6')
        expect(intervalLabel(86_400, t)).toBe('adminTasks.everyDays:1')
        expect(intervalLabel(900, t)).toBe('adminTasks.everyMinutes:15')
        expect(intervalLabel(5, t)).toBe('adminTasks.everySeconds:5')
    })

    it('names a task without a period as running once', () => {
        expect(intervalLabel(0, t)).toBe('adminTasks.once')
    })
})

/** The columns read the task as the backend sends it and sort the interval by its length. */
describe('taskColumns', () => {
    const task: TaskStatus = {
        name: 'email-queue',
        mode: 'FIXED_DELAY',
        periodSeconds: 10,
        outcome: TaskOutcome.FAILED,
        lastStartedAt: '2026-05-01T08:00:00Z',
        lastDurationMs: 12,
        runs: 4,
        failures: 1,
        lastFailureAt: '2026-05-01T07:59:50Z',
        lastFailureMessage: 'IllegalStateException: database gone',
    }

    it('shows the interval in words and sorts it by seconds', () => {
        const interval = taskColumns(t).find(column => column.key === 'interval')!

        expect(interval.value(task)).toBe('adminTasks.everySeconds:10')
        expect(interval.sortValue!(task)).toBe(10)
    })

    it('offers the outcomes with trouble first', () => {
        const outcome = taskColumns(t).find(column => column.key === 'outcome')!

        expect(outcome.options!.map(option => option.value)).toEqual([
            TaskOutcome.FAILED, TaskOutcome.RUNNING, TaskOutcome.SUCCEEDED, TaskOutcome.NOT_RUN_YET,
        ])
        expect(outcome.value(task)).toBe(TaskOutcome.FAILED)
    })
})
