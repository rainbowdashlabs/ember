/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ColumnTypes, type ColumnOption, type TableColumn} from '@/components/table/tableColumn'
import {MovementState, type Movement} from '@/api/movements'
import {
    hasMoved,
    lastMovedAt,
    memberNameOf,
    purposeOptions,
    stateOptions,
    subjectNameOf,
    turnOptions,
    turnRank,
} from './movementFilter'

/** The columns a list of movements can show, by the key each is remembered under. */
export const MovementColumn = {
    SUBJECT: 'subject',
    PURPOSE: 'purpose',
    MEMBER: 'member',
    STANDING: 'standing',
    CREATED: 'created',
    MODIFIED: 'modified',
} as const

export type MovementColumnKey = (typeof MovementColumn)[keyof typeof MovementColumn]

const TURN = 'turn:'
const STEP = 'step:'

/**
 * Where a movement stands, as every word the step column shows for it: its state, whose turn it is
 * while it runs, and the step it has reached by that step's own name. Any of them can be filtered
 * for, so "running", "waiting for us" and "handed out" are three ways into the same column.
 */
function standingOf(movement: Movement): string[] {
    const standing: string[] = [movement.state]
    if (movement.state === MovementState.OPEN && movement.currentStepActor) standing.push(TURN + movement.currentStepActor)
    if (movement.reachedStepLabel) standing.push(STEP + movement.reachedStepLabel)
    return standing
}

/**
 * The columns of a list of movements, every one of them. A screen takes the ones it shows.
 *
 * <p>What a row is about filters by the inventory it belongs to, which is the question a queue is
 * narrowed by, and still sorts and searches by the piece it names.
 *
 * <p>Where a movement stands sorts by whose turn it is, ours first, rather than by the words on it,
 * because that is the order a queue is worked in. The last move is empty on a movement nobody has
 * touched, since a second date there would read as two facts and is one, and it still sorts by the
 * day it was raised.
 *
 * @param movements the movements shown, which name the steps the step column can be filtered by
 */
export function useMovementColumns(movements: () => readonly Movement[]) {
    const {t} = useI18n()

    const purposes = computed(() => purposeOptions(t))

    const stepNames = computed(() => [...new Set(movements()
        .map(movement => movement.reachedStepLabel)
        .filter((label): label is string => !!label))]
        .toSorted((a, b) => a.localeCompare(b, 'de')))

    const standingOptions = computed<ColumnOption[]>(() => [
        ...stateOptions(t),
        ...turnOptions(t).map(turn => ({value: TURN + turn.value, label: t('movements.waitingFor', {party: turn.label})})),
        ...stepNames.value.map(name => ({value: STEP + name, label: name})),
    ])

    return computed<TableColumn<Movement>[]>(() => [
        {
            key: MovementColumn.SUBJECT, label: t('movements.queue.columns.what'), type: ColumnTypes.TEXT,
            value: movement => movement.inventoryName, display: subjectNameOf, sortValue: subjectNameOf, pinned: true,
        },
        {
            key: MovementColumn.PURPOSE, label: t('movements.queue.columns.purpose'), type: ColumnTypes.ENUM,
            value: movement => movement.purpose, options: purposes.value,
        },
        {
            key: MovementColumn.MEMBER, label: t('movements.queue.columns.member'), type: ColumnTypes.TEXT,
            value: memberNameOf,
            display: movement => memberNameOf(movement) || t('movements.queue.forTheStore'),
        },
        {
            key: MovementColumn.STANDING, label: t('movements.queue.columns.step'), type: ColumnTypes.ENUM,
            value: standingOf, options: standingOptions.value, sortValue: turnRank,
        },
        {
            key: MovementColumn.CREATED, label: t('movements.queue.columns.created'), type: ColumnTypes.DATE,
            value: movement => movement.createdAt,
        },
        {
            key: MovementColumn.MODIFIED, label: t('movements.queue.columns.modified'), type: ColumnTypes.DATE,
            value: movement => hasMoved(movement) ? lastMovedAt(movement) : null,
            sortValue: lastMovedAt,
        },
    ])
}
