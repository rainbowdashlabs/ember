/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    MovementPurpose,
    MovementState,
    StepActor,
    type Movement,
    type MovementPurposeName,
    type MovementStateName,
    type StepActorName,
} from '@/api/movements'
import type {SortComparator} from '@/composables/useSortable'
import {enumOptions, type ColumnOption} from '@/components/table/tableColumn'

/** Every purpose a movement can have, in the order the wizard offers them. */
export const filterablePurposes: MovementPurposeName[] = [
    MovementPurpose.ISSUE,
    MovementPurpose.RETURN,
    MovementPurpose.EXCHANGE,
    MovementPurpose.REQUEST,
]

/** Every state a movement can be in, running first. */
export const filterableStates: MovementStateName[] = [
    MovementState.OPEN,
    MovementState.DONE,
    MovementState.DECLINED,
    MovementState.CANCELLED,
]

/** Whose turn it can be, ours first, which is what somebody working the queue narrows by first. */
export const filterableTurns: StepActorName[] = [StepActor.STATION, StepActor.MEMBER, StepActor.OWNER]

type Translate = (key: string) => string

/** The purposes to tick, each by its name. */
export function purposeOptions(t: Translate): ColumnOption[] {
    return enumOptions(filterablePurposes, name => t(`movements.purpose.${name}`))
}

/** The states to tick, each by its name. */
export function stateOptions(t: Translate): ColumnOption[] {
    return enumOptions(filterableStates, name => t(`movements.state.${name}`))
}

/** The parties whose turn it can be, each by its name. */
export function turnOptions(t: Translate): ColumnOption[] {
    return enumOptions(filterableTurns, name => t(`movements.actor.${name}`))
}

/** The member name a row shows, which is one of the two things a search has to match. */
export function memberNameOf(movement: Movement): string {
    return movement.memberIdentity?.name ?? movement.memberName ?? ''
}

/** What the row is about in words: the piece where there is one, the inventory otherwise. */
export function subjectNameOf(movement: Movement): string {
    return movement.itemName ?? movement.incomingItemName ?? movement.inventoryName ?? ''
}

/**
 * How far down the queue a row sits before any column is sorted.
 *
 * <p>Ours first, because a station working the queue wants the steps it can actually press; then the
 * member's, then the body's above, and everything finished out of the way. The date orders within a
 * group, which is what the comparator does after this.
 */
export function turnRank(movement: Movement): number {
    if (movement.state !== MovementState.OPEN) return 4
    if (movement.currentStepActor === StepActor.STATION) return movement.actionable ? 0 : 1
    if (movement.currentStepActor === StepActor.MEMBER) return 2
    return 3
}

/**
 * When the movement last moved, which for one that never has is the day it was raised.
 *
 * <p>The two dates answer different questions. When it was raised is what the queue was let sit for,
 * and when it last moved is what says which rows have gone quiet, so a row raised in January and
 * touched yesterday belongs at opposite ends of the two orders.
 */
export function lastMovedAt(movement: Movement): string {
    return movement.updatedAt ?? movement.createdAt
}

/** Whether anything has happened to the movement since it was raised. */
export function hasMoved(movement: Movement): boolean {
    return lastMovedAt(movement) !== movement.createdAt
}

/**
 * The order of the queue while no column is sorted, and the tie break once one is: whose turn it is,
 * and within an equal turn the oldest first, because a movement nobody has touched for a fortnight
 * is the one to deal with.
 */
export const queueOrder: SortComparator<Movement> = (a, b) =>
    turnRank(a) - turnRank(b) || a.createdAt.localeCompare(b.createdAt)
