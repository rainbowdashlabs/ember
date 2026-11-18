/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    RegistrationStatus,
    type EventGroupPartState,
    type EventGroupSet,
    type EventGroupDetails,
    type EventGroupSummary,
    type SetResult,
} from '@/api/generated/schema'
import {places, type Places} from '@/util/eventPlaces'

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
 * taking place together hold. Only chosen parts still taking place count toward a set's maximum.
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
        const pickedTakingPlace = inSet.filter(part => chosen.has(part.eventId) && !part.calledOff).length
        const available = inSet.filter(part => chosen.has(part.eventId) || !part.calledOff).length
        const required = Math.min(Math.max(set.minimum, 0), available)
        const missingRequired = inSet
            .filter(part => part.required && !part.calledOff && !chosen.has(part.eventId))
            .map(part => part.eventId)
        const maximum = set.maximum
        const tooMany = maximum == null ? 0 : Math.max(0, pickedTakingPlace - maximum)
        return {setId: set.id, name: set.name, required, chosen: picked, missingRequired, maximum, tooMany}
    })
}

/** Whether a set's result is met: enough parts, every required one, and no more than its maximum. */
export function isMet(result: SetResult): boolean {
    return result.chosen >= result.required && result.missingRequired.length === 0 && result.tooMany === 0
}

/**
 * Whether a set takes exactly one of its parts, which reads as a choice between alternatives.
 *
 * @param set the set
 */
export function isSingleChoice(set: Pick<EventGroupSet, 'minimum' | 'maximum'>): boolean {
    return set.maximum === 1 && set.minimum <= 1
}

/**
 * Whether a part can no longer be added to a choice because its set already holds its maximum. A part
 * already chosen stays choosable, so it can be taken out again.
 *
 * @param part   the part
 * @param sets   the event group's sets
 * @param parts  the event group's parts
 * @param chosen the chosen parts
 */
export function blockedByMaximum(
    part: Pick<EventGroupPartState, 'eventId' | 'setId'>,
    sets: readonly EventGroupSet[],
    parts: readonly EventGroupPartState[],
    chosen: ReadonlySet<number>,
): boolean {
    if (chosen.has(part.eventId)) return false
    const maximum = sets.find(set => set.id === part.setId)?.maximum
    if (maximum == null) return false
    const held = partsOfSet(part.setId, parts).filter(entry => chosen.has(entry.eventId) && !entry.calledOff).length
    return held >= maximum
}

/** Whether every place of a part is taken, by its effective limit. A part without a limit is never full. */
export function isPartFull(part: Pick<EventGroupPartState, 'accepted' | 'places'>): boolean {
    return part.places != null && part.accepted >= part.places
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
    const count = partsOfSet(set.id, parts).length
    const rule = ruleCount(set, count, t)
    const required = requiredPartsOf(set.id, parts)
    if (required.length === 0) return rule
    return t('eventGroups.setRuleRequired', {rule, parts: required.map(part => part.name).join(', ')})
}

/** How many parts a set asks for: exactly one, a range, or at least a number. */
function ruleCount(
    set: EventGroupSet,
    count: number,
    t: (key: string, values?: Record<string, unknown>) => string,
): string {
    if (isSingleChoice(set)) return t('eventGroups.setRuleOne', {count})
    if (set.maximum != null && set.maximum < count) {
        return t('eventGroups.setRuleRange', {minimum: set.minimum, maximum: set.maximum, count})
    }
    return t('eventGroups.setRule', {minimum: set.minimum, count})
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
 * Whether a sign-up holds a place or waits for one: accepted, not decided yet, or on the waiting
 * list. A declined or withdrawn sign-up holds nothing, and neither does a member who never signed up.
 * Such a sign-up stands: it can be changed and given up, and nobody signs up a second time beside it.
 *
 * @param status the status of the sign-up, missing where there is none
 */
export function holdsOrWaitsForPlace(status: RegistrationStatus | null | undefined): boolean {
    return status === RegistrationStatus.PENDING
        || status === RegistrationStatus.ACCEPTED
        || status === RegistrationStatus.QUEUED
}

/** What the places of an event group are read from: its limit, whether it confirms, and its counts. */
type EventGroupCounts = Pick<EventGroupSummary, 'holding' | 'queued'> & {
    details: Pick<EventGroupDetails, 'registrationLimit' | 'requiresConfirmation'>
}

/**
 * How the places for people of an event group stand: accepted sign-ups against its people limit, and
 * how many wait. Null where it limits no people.
 *
 * <p>Unlike an appointment, an event group is full for people only once every place is taken. Somebody
 * waiting does not make it full: they may wait for a full part, and are passed over while it stays
 * full. The waiting count is there for the line saying how the places stand.
 */
export function eventGroupPlaces(summary: EventGroupCounts): Places | null {
    const limit = summary.details.registrationLimit
    if (!limit) return null
    return {...places(summary.holding, limit, summary.queued), full: summary.holding >= limit}
}

/**
 * The places for people, where they are what is full, for the question asked before accepting beyond
 * the limit. Where a chosen part is what is full instead, the people count would name the wrong
 * reason, so the question goes without a count.
 */
export function fullPeoplePlaces(summary: EventGroupCounts): Places | null {
    const people = eventGroupPlaces(summary)
    return people?.full ? people : null
}

/**
 * How the places of one part stand, by its effective limit. A part keeps no waiting list of its own,
 * the event group does, so nobody waits here. Null where the part has no limit.
 */
export function partPlaces(part: Pick<EventGroupPartState, 'accepted' | 'places'>): Places | null {
    return part.places ? places(part.accepted, part.places, 0) : null
}

/**
 * Whether a new sign-up with this choice would wait on the waiting list rather than get its places at
 * once, as the server decides it: the event group takes sign-ups as they come, and every place for
 * people is taken or a chosen part still to come is full. An event group that confirms its sign-ups
 * leaves every one waiting for the decision instead.
 *
 * @param summary the event group
 * @param parts   its parts
 * @param chosen  the chosen parts
 */
export function choiceWaits(
    summary: EventGroupCounts,
    parts: readonly EventGroupPartState[],
    chosen: ReadonlySet<number>,
): boolean {
    if (summary.details.requiresConfirmation) return false
    if (eventGroupPlaces(summary)?.full) return true
    return parts.some(part => chosen.has(part.eventId) && !part.calledOff && !part.tookPlace && isPartFull(part))
}

/**
 * Whether an event group keeps a waiting list at all, which needs something to limit places: its
 * people, its places per appointment, or a part with places of its own.
 *
 * @param details the event group's settings
 * @param parts   its parts, each with its effective places
 */
export function keepsWaitingList(
    details: Pick<EventGroupDetails, 'registrationLimit' | 'partRegistrationLimit'>,
    parts: readonly Pick<EventGroupPartState, 'places'>[],
): boolean {
    return !!details.registrationLimit || !!details.partRegistrationLimit || parts.some(part => !!part.places)
}
