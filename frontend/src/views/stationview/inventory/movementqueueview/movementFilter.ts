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

/** Every purpose the filter offers to tick, in the order the wizard offers them. */
export const filterablePurposes: MovementPurposeName[] = [
    MovementPurpose.ISSUE,
    MovementPurpose.RETURN,
    MovementPurpose.EXCHANGE,
    MovementPurpose.REQUEST,
]

/** Every state the filter offers to tick. */
export const filterableStates: MovementStateName[] = [
    MovementState.OPEN,
    MovementState.DONE,
    MovementState.DECLINED,
    MovementState.CANCELLED,
]

/** Whose turn it can be, which is the filter somebody working the queue reaches for first. */
export const filterableTurns: StepActorName[] = [StepActor.STATION, StepActor.MEMBER, StepActor.OWNER]

export interface MovementFilter {
    /** Part of a member name or a piece name, matched without regard to case. */
    search: string
    /** Ids of the inventories that were ticked, as text. */
    inventoryIds: string[]
    purposes: string[]
    states: string[]
    /** The parties whose turn it is, as ticked. */
    turns: string[]
}

/**
 * What the page starts with: every inventory, every purpose, every name, and only the movements that
 * are still walking. A finished movement is a record rather than a task, and whoever opens the queue
 * is looking at the tasks. The states stand ticked rather than hidden behind a mode, so whoever wants
 * the finished ones back only has to tick them.
 */
export const defaultMovementFilter: MovementFilter = {
    search: '',
    inventoryIds: [],
    purposes: [],
    states: [MovementState.OPEN],
    turns: [],
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

function amongTicked(ticked: string[], value: string | null | undefined): boolean {
    return ticked.length === 0 || (value != null && ticked.includes(value))
}

/**
 * The rows left once every filter has had its say. Within a filter the ticks stand beside one
 * another; between filters they narrow together, so a row survives only where it answers all of them.
 */
export function filterMovements(movements: Movement[], filter: MovementFilter): Movement[] {
    const needle = filter.search.trim().toLowerCase()
    return movements.filter(movement => {
        const matches = needle === ''
            || memberNameOf(movement).toLowerCase().includes(needle)
            || subjectNameOf(movement).toLowerCase().includes(needle)
            || (movement.itemInternalId ?? '').toLowerCase().includes(needle)
        return matches
            && amongTicked(filter.inventoryIds, movement.inventoryId != null ? String(movement.inventoryId) : null)
            && amongTicked(filter.purposes, movement.purpose)
            && amongTicked(filter.states, movement.state)
            && amongTicked(filter.turns, movement.state === MovementState.OPEN ? movement.currentStepActor : null)
    })
}

/** Name of an inventory as the filter offers it. */
export interface InventoryChoice {
    id: number
    name: string
}

/**
 * The inventories the loaded movements actually mention, sorted by name. Offering every inventory of
 * the station would fill the list with entries that match nothing.
 */
export function inventoryChoices(movements: Movement[]): InventoryChoice[] {
    const names = new Map<number, string>()
    for (const movement of movements) {
        if (movement.inventoryId != null) names.set(movement.inventoryId, movement.inventoryName ?? '')
    }
    return [...names]
        .map(([id, name]) => ({id, name}))
        .sort((a, b) => a.name.localeCompare(b.name, 'de'))
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
