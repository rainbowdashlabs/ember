/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus, type EventGroupPartState, type EventGroupSet, type SetResult} from '@/api/generated/schema'

/**
 * The parts of one set, in the order the event group lists them.
 *
 * @param setId the set
 * @param parts the event group's parts
 */
export function partsOfSet<P extends Pick<EventGroupPartState, 'setId'>>(setId: number, parts: readonly P[]): P[] {
    return parts.filter(part => part.setId === setId)
}

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
        const inSet = partsOfSet(set.id, parts)
        const picked = inSet.filter(part => chosen.has(part.eventId)).length
        const available = inSet.filter(part => chosen.has(part.eventId) || !part.calledOff).length
        const required = Math.min(Math.max(set.minimum, 0), available)
        const missingRequired = inSet
            .filter(part => part.required && !part.calledOff && !chosen.has(part.eventId))
            .map(part => part.eventId)
        return {setId: set.id, name: set.name, required, chosen: picked, missingRequired}
    })
}

/** Whether a set's result is met. */
export function isMet(result: SetResult): boolean {
    return result.chosen >= result.required && result.missingRequired.length === 0
}

/** How many parts a set still lacks, zero where it is met. */
export function missingOf(result: SetResult): number {
    return Math.max(0, result.required - result.chosen, result.missingRequired.length)
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
    const count = partsOfSet(set.id, parts).length
    return count > 0 && set.minimum >= count
}

/**
 * The parts of a set every sign-up has to hold, leaving out the ones called off, which are required no
 * more.
 *
 * @param setId the set
 * @param parts the event group's parts
 */
export function requiredPartsOf(setId: number, parts: readonly EventGroupPartState[]): EventGroupPartState[] {
    return parts.filter(part => part.setId === setId && part.required && !part.calledOff)
}

/**
 * What a set asks for, as the group page and the sign-up dialog say it: all of its parts, or a number
 * of them, naming the parts it requires.
 *
 * @param set   the set
 * @param parts the event group's parts
 * @param t     the translator
 */
export function setRuleText(
    set: EventGroupSet,
    parts: readonly EventGroupPartState[],
    t: (key: string, values?: Record<string, unknown>) => string,
): string {
    if (asksForAll(set, parts)) return t('eventGroups.setAll')
    const rule = t('eventGroups.setRule', {minimum: set.minimum, count: partsOfSet(set.id, parts).length})
    const required = requiredPartsOf(set.id, parts)
    if (required.length === 0) return rule
    return t('eventGroups.setRuleRequired', {rule, parts: required.map(part => part.name).join(', ')})
}

/**
 * What the set of the given id asks for, in the words of {@link setRuleText}, or nothing where the
 * event group has no such set.
 *
 * @param setId the set
 * @param sets  the event group's sets
 * @param parts the event group's parts
 * @param t     the translator
 */
export function ruleOf(
    setId: number,
    sets: readonly EventGroupSet[],
    parts: readonly EventGroupPartState[],
    t: (key: string, values?: Record<string, unknown>) => string,
): string {
    const set = sets.find(candidate => candidate.id === setId)
    return set ? setRuleText(set, parts, t) : ''
}

/**
 * Whether a sign-up holds a place or waits for one: accepted, or not decided yet. A declined or
 * withdrawn sign-up holds nothing, and neither does a member who never signed up.
 *
 * @param status the status of the sign-up, missing where there is none
 */
export function holdsOrWaitsForPlace(status: RegistrationStatus | null | undefined): boolean {
    return status === RegistrationStatus.PENDING || status === RegistrationStatus.ACCEPTED
}
