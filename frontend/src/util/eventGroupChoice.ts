/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventGroupPartState, EventGroupSet, SetResult} from '@/api/generated/schema'

/**
 * How a choice of parts stands against every set of an event group, the same way the server decides it.
 *
 * <p>A chosen part counts even when it was called off later. A part called off that was not chosen
 * can no longer be asked for, so a set never asks for more than the chosen parts and those still
 * taking place together hold.
 *
 * @param sets   the event group's sets in order
 * @param parts  the event group's parts
 * @param chosen the chosen parts
 * @return one result per set
 */
export function evaluateChoice(
    sets: readonly EventGroupSet[],
    parts: readonly EventGroupPartState[],
    chosen: ReadonlySet<number>,
): SetResult[] {
    return sets.map(set => {
        const inSet = parts.filter(part => part.setId === set.id)
        const picked = inSet.filter(part => chosen.has(part.eventId)).length
        const available = inSet.filter(part => chosen.has(part.eventId) || !part.calledOff).length
        const required = Math.min(Math.max(set.minimum, 0), available)
        return {setId: set.id, name: set.name, required, chosen: picked}
    })
}

/** Whether a set's result is met. */
export function isMet(result: SetResult): boolean {
    return result.chosen >= result.required
}

/** How many parts a set still lacks, zero where it is met. */
export function missingOf(result: SetResult): number {
    return Math.max(0, result.required - result.chosen)
}

/** Whether a choice meets every set, and chooses at least one part. */
export function choiceMeetsRules(
    sets: readonly EventGroupSet[],
    parts: readonly EventGroupPartState[],
    chosen: ReadonlySet<number>,
): boolean {
    return chosen.size > 0 && evaluateChoice(sets, parts, chosen).every(isMet)
}

/** Whether a set asks for every one of its parts, which reads as "all mandatory". */
export function asksForAll(set: EventGroupSet, parts: readonly EventGroupPartState[]): boolean {
    const count = parts.filter(part => part.setId === set.id).length
    return count > 0 && set.minimum >= count
}
