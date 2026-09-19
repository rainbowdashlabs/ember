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
    filterablePurposes,
    filterableStates,
    filterableTurns,
    hasMoved,
    lastMovedAt,
    memberNameOf,
    subjectNameOf,
    turnRank,
} from './movementFilter'

/** The columns a list of movements can show, by the key each is remembered under. */
export const MovementColumn = {
    SUBJECT: 'subject',
    PURPOSE: 'purpose',
    MEMBER: 'member',
    INVENTORY: 'inventory',
    STANDING: 'standing',
    CREATED: 'created',
    MODIFIED: 'modified',
} as const

export type MovementColumnKey = (typeof MovementColumn)[keyof typeof MovementColumn]

/**
 * Where a movement stands, as one value: the party it waits on while it runs, how it ended once it
 * has. Absent for a running movement that waits on nobody yet.
 */
function standingOf(movement: Movement): string | null {
    if (movement.state !== MovementState.OPEN) return movement.state
    return movement.currentStepActor ?? null
}

/**
 * The columns of a list of movements, every one of them. A screen takes the ones it shows.
 *
 * <p>Where a movement stands sorts by whose turn it is, ours first, rather than by the words on it,
 * because that is the order a queue is worked in. The last move is empty on a movement nobody has
 * touched, since a second date there would read as two facts and is one, and it still sorts by the
 * day it was raised.
 */
export function useMovementColumns() {
    const {t} = useI18n()

    const purposeOptions = computed<ColumnOption[]>(() =>
        filterablePurposes.map(name => ({value: name, label: t(`movements.purpose.${name}`)})))

    const standingOptions = computed<ColumnOption[]>(() => [
        ...filterableTurns.map(actor => ({
            value: actor,
            label: t('movements.waitingFor', {party: t(`movements.actor.${actor}`)}),
        })),
        ...filterableStates
            .filter(state => state !== MovementState.OPEN)
            .map(state => ({value: state, label: t(`movements.state.${state}`)})),
    ])

    return computed<TableColumn<Movement>[]>(() => [
        {
            key: MovementColumn.SUBJECT, label: t('movements.queue.columns.what'), type: ColumnTypes.TEXT,
            value: subjectNameOf, pinned: true,
        },
        {
            key: MovementColumn.PURPOSE, label: t('movements.queue.columns.purpose'), type: ColumnTypes.ENUM,
            value: movement => movement.purpose, options: purposeOptions.value,
        },
        {
            key: MovementColumn.MEMBER, label: t('movements.queue.columns.member'), type: ColumnTypes.TEXT,
            value: memberNameOf,
            display: movement => memberNameOf(movement) || t('movements.queue.forTheStore'),
        },
        {
            key: MovementColumn.INVENTORY, label: t('movements.queue.columns.inventory'), type: ColumnTypes.TEXT,
            value: movement => movement.inventoryName, defaultVisible: false,
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
