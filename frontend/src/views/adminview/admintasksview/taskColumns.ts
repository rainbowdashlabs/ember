/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TaskOutcome} from '@/api/adminTasks'
import type {TaskStatus} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import type {Translate} from '@/util/failure'

/** The words each outcome reads as, in the order they sort in: trouble first. */
export const OUTCOME_LABEL_KEYS: Record<string, string> = {
    [TaskOutcome.FAILED]: 'adminTasks.outcomeFailed',
    [TaskOutcome.RUNNING]: 'adminTasks.outcomeRunning',
    [TaskOutcome.SUCCEEDED]: 'adminTasks.outcomeSucceeded',
    [TaskOutcome.NOT_RUN_YET]: 'adminTasks.outcomeNotRunYet',
}

const UNITS: readonly {seconds: number, key: string}[] = [
    {seconds: 86_400, key: 'adminTasks.everyDays'},
    {seconds: 3_600, key: 'adminTasks.everyHours'},
    {seconds: 60, key: 'adminTasks.everyMinutes'},
    {seconds: 1, key: 'adminTasks.everySeconds'},
]

/**
 * How often a task runs, in the largest unit that divides its period evenly: "alle 6 Std." rather
 * than "alle 21600 Sek.". A task without a period runs once.
 */
export function intervalLabel(periodSeconds: number, t: Translate): string {
    if (periodSeconds <= 0) return t('adminTasks.once')
    const unit = UNITS.find(candidate => periodSeconds % candidate.seconds === 0) ?? UNITS[UNITS.length - 1]!
    return t(unit.key, {n: periodSeconds / unit.seconds})
}

/** The columns of the task list: which task, how often, how its last run went, when and for how long. */
export function taskColumns(t: Translate): TableColumn<TaskStatus>[] {
    return [
        {key: 'name', label: t('adminTasks.name'), type: ColumnTypes.TEXT, value: task => task.name, pinned: true},
        {
            key: 'interval', label: t('adminTasks.interval'), type: ColumnTypes.TEXT,
            value: task => intervalLabel(task.periodSeconds, t), sortValue: task => task.periodSeconds,
        },
        {
            key: 'outcome', label: t('adminTasks.outcome'), type: ColumnTypes.ENUM, value: task => task.outcome,
            options: enumOptions(Object.keys(OUTCOME_LABEL_KEYS), value => t(OUTCOME_LABEL_KEYS[value]!)),
        },
        {key: 'lastStartedAt', label: t('adminTasks.lastStartedAt'), type: ColumnTypes.DATE_TIME, value: task => task.lastStartedAt},
        {key: 'lastDurationMs', label: t('adminTasks.lastDurationMs'), type: ColumnTypes.NUMBER, value: task => task.lastDurationMs, align: 'right'},
        {key: 'runs', label: t('adminTasks.runs'), type: ColumnTypes.NUMBER, value: task => task.runs, align: 'right', defaultVisible: false},
        {key: 'failures', label: t('adminTasks.failures'), type: ColumnTypes.NUMBER, value: task => task.failures, align: 'right'},
        {key: 'lastFailureAt', label: t('adminTasks.lastFailureAt'), type: ColumnTypes.DATE_TIME, value: task => task.lastFailureAt, defaultVisible: false},
        {key: 'lastFailureMessage', label: t('adminTasks.lastFailureMessage'), type: ColumnTypes.TEXT, value: task => task.lastFailureMessage},
    ]
}
