/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RegistrationStatus, type EventGroupPartState, type EventGroupSet} from '@/api/generated/schema'
import {
    asksForAll,
    blockedByMaximum,
    choiceMeetsRules,
    choiceWaits,
    eventGroupPlaces,
    fullPeoplePlaces,
    isPartFull,
    keepsWaitingList,
    isSingleChoice,
    partPlaces,
    evaluateChoice,
    holdsOrWaitsForPlace,
    isMet,
    missingOf,
    partsOfSet,
    ruleOf,
    setRuleText,
} from './eventGroupChoice'

const PRACTICE: EventGroupSet = {id: 1, eventGroupId: 9, name: 'Übung', minimum: 2, maximum: null, position: 0}
const TEST: EventGroupSet = {id: 2, eventGroupId: 9, name: 'Prüfung', minimum: 1, maximum: null, position: 1}

function part(eventId: number, setId: number, calledOff = false, required = false): EventGroupPartState {
    return {eventId, setId, name: `Tag ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z', calledOff, tookPlace: false,
        required, accepted: 0, places: null}
}

const PARTS = [part(11, 1), part(12, 1), part(13, 1), part(21, 2)]

describe('evaluateChoice', () => {
    it('meets the rules with two practice days and the test', () => {
        expect(choiceMeetsRules([PRACTICE, TEST], PARTS, new Set([11, 13, 21]))).toBe(true)
    })

    it('names the set that is short and by how much', () => {
        const results = evaluateChoice([PRACTICE, TEST], PARTS, new Set([11, 21]))
        expect(isMet(results[0]!)).toBe(false)
        expect(missingOf(results[0]!)).toBe(1)
        expect(isMet(results[1]!)).toBe(true)
    })

    it('stops asking for a called-off part nobody chose, and keeps counting one that was chosen', () => {
        const calledOff = [part(11, 1), part(12, 1, true), part(13, 1, true), part(21, 2)]
        expect(choiceMeetsRules([PRACTICE, TEST], calledOff, new Set([11, 21]))).toBe(true)
        expect(choiceMeetsRules([PRACTICE, TEST], calledOff, new Set([11, 12, 21]))).toBe(true)
    })

    it('refuses an empty choice', () => {
        expect(choiceMeetsRules([{...PRACTICE, minimum: 0}], PARTS, new Set())).toBe(false)
    })

    it('reads a set asking for all its parts as mandatory', () => {
        expect(asksForAll(TEST, PARTS)).toBe(true)
        expect(asksForAll(PRACTICE, PARTS)).toBe(false)
    })
})

describe('required parts', () => {
    const parts = [part(11, 1, false, true), part(12, 1), part(13, 1), part(21, 2)]

    it('count toward the minimum and have to be chosen', () => {
        expect(choiceMeetsRules([PRACTICE, TEST], parts, new Set([11, 12, 21]))).toBe(true)
        const results = evaluateChoice([PRACTICE, TEST], parts, new Set([12, 13, 21]))
        expect(results[0]).toMatchObject({required: 2, chosen: 2, missingRequired: [11]})
        expect(isMet(results[0]!)).toBe(false)
        expect(missingOf(results[0]!)).toBe(1)
    })

    it('are required no more once called off', () => {
        const calledOff = [part(11, 1, true, true), part(12, 1), part(13, 1), part(21, 2)]
        expect(choiceMeetsRules([PRACTICE, TEST], calledOff, new Set([12, 13, 21]))).toBe(true)
    })

    it('are named in the rule of their set', () => {
        const t = (key: string, values?: Record<string, unknown>) => `${key}:${JSON.stringify(values ?? {})}`
        expect(setRuleText(PRACTICE, parts, t)).toContain('eventGroups.setRuleRequired')
        expect(setRuleText(PRACTICE, parts, t)).toContain('Tag 11')
        expect(setRuleText(TEST, parts, t)).toBe('eventGroups.setAll:{}')
    })
})

describe('partsOfSet', () => {
    it('keeps the parts of the set in their order and nothing of another set', () => {
        expect(partsOfSet(1, PARTS).map(entry => entry.eventId)).toEqual([11, 12, 13])
        expect(partsOfSet(3, PARTS)).toEqual([])
    })
})

describe('ruleOf', () => {
    const t = (key: string, values?: Record<string, unknown>) => `${key}:${JSON.stringify(values ?? {})}`

    it('says what the set of the id asks for, as setRuleText does', () => {
        expect(ruleOf(1, [PRACTICE, TEST], PARTS, t)).toBe(setRuleText(PRACTICE, PARTS, t))
    })

    it('says nothing for a set the event group does not have', () => {
        expect(ruleOf(5, [PRACTICE, TEST], PARTS, t)).toBe('')
    })
})

describe('holdsOrWaitsForPlace', () => {
    it('counts an accepted sign-up, one still waiting for a decision and one on the waiting list', () => {
        expect(holdsOrWaitsForPlace(RegistrationStatus.ACCEPTED)).toBe(true)
        expect(holdsOrWaitsForPlace(RegistrationStatus.PENDING)).toBe(true)
        expect(holdsOrWaitsForPlace(RegistrationStatus.QUEUED)).toBe(true)
    })

    it('counts nothing declined, withdrawn or never signed up', () => {
        const standing: RegistrationStatus[] = [RegistrationStatus.ACCEPTED, RegistrationStatus.PENDING, RegistrationStatus.QUEUED]
        const others = Object.values(RegistrationStatus).filter(status => !standing.includes(status))
        expect(others.map(holdsOrWaitsForPlace)).toEqual(others.map(() => false))
        expect(holdsOrWaitsForPlace(null)).toBe(false)
        expect(holdsOrWaitsForPlace(undefined)).toBe(false)
    })
})

/** A block's maximum holds a choice back, and "exactly one" makes its parts alternatives. */
describe('block maximum', () => {
    const ALTERNATIVES: EventGroupSet = {...TEST, minimum: 1, maximum: 1}
    const parts = [part(21, 2), part(22, 2), part(23, 2, true)]

    it('counts too many chosen parts taking place and is not met', () => {
        const [result] = evaluateChoice([ALTERNATIVES], parts, new Set([21, 22]))
        expect(result).toMatchObject({maximum: 1, tooMany: 1})
        expect(isMet(result!)).toBe(false)
    })

    it('does not count a chosen part called off toward the maximum', () => {
        const [result] = evaluateChoice([ALTERNATIVES], parts, new Set([21, 23]))
        expect(result!.tooMany).toBe(0)
    })

    it('blocks the other parts of a block at its maximum and keeps the chosen one choosable', () => {
        const chosen = new Set([21])
        expect(blockedByMaximum(parts[1]!, [ALTERNATIVES], parts, chosen)).toBe(true)
        expect(blockedByMaximum(parts[0]!, [ALTERNATIVES], parts, chosen)).toBe(false)
        expect(blockedByMaximum(parts[1]!, [TEST], parts, chosen)).toBe(false)
    })

    it('reads "at least 1, at most 1" as a single choice', () => {
        expect(isSingleChoice(ALTERNATIVES)).toBe(true)
        expect(isSingleChoice({minimum: 0, maximum: 1})).toBe(true)
        expect(isSingleChoice({minimum: 1, maximum: 2})).toBe(false)
        expect(isSingleChoice({minimum: 1, maximum: null})).toBe(false)
    })

    it('says the rule as exactly one, or as a range', () => {
        const t = (key: string, values?: Record<string, unknown>) => `${key}:${JSON.stringify(values ?? {})}`
        const two = [part(21, 2), part(22, 2), part(24, 2)]
        expect(setRuleText(ALTERNATIVES, two, t)).toBe('eventGroups.setRuleOne:{"count":3}')
        expect(setRuleText({...ALTERNATIVES, maximum: 2}, two, t)).toBe('eventGroups.setRuleRange:{"minimum":1,"maximum":2,"count":3}')
    })
})

/** A sign-up waits as a whole where every place for people or a chosen part still to come is full. */
describe('full places', () => {
    const summary = {details: {registrationLimit: 10, requiresConfirmation: false}, holding: 4, queued: 0}
    const full = {...part(11, 1), accepted: 12, places: 12}
    const free = {...part(12, 1), accepted: 3, places: 12}

    it('reads a part full at its limit and never without one', () => {
        expect(isPartFull(full)).toBe(true)
        expect(isPartFull(free)).toBe(false)
        expect(isPartFull(part(13, 1))).toBe(false)
        expect(partPlaces(free)).toEqual({taken: 3, limit: 12, waiting: 0, full: false})
        expect(partPlaces(part(13, 1))).toBeNull()
    })

    it('waits where a chosen part is full', () => {
        expect(choiceWaits(summary, [full, free], new Set([11, 12]))).toBe(true)
        expect(choiceWaits(summary, [full, free], new Set([12]))).toBe(false)
    })

    /** The server leaves a chosen part called off or past out, so it holds nobody back. */
    it('does not wait for a full part called off or past', () => {
        expect(choiceWaits(summary, [{...full, calledOff: true}], new Set([11]))).toBe(false)
        expect(choiceWaits(summary, [{...full, tookPlace: true}], new Set([11]))).toBe(false)
    })

    it('waits where every place for people is taken', () => {
        expect(choiceWaits({...summary, holding: 10}, [free], new Set([12]))).toBe(true)
        expect(eventGroupPlaces({...summary, holding: 10})).toEqual({taken: 10, limit: 10, waiting: 0, full: true})
    })

    /**
     * Somebody waiting does not make the people full: they may wait for a full part and are passed
     * over while it stays full. The count is kept for the line only.
     */
    it('does not read the people full while somebody waits and places are free', () => {
        expect(choiceWaits({...summary, queued: 2}, [free], new Set([12]))).toBe(false)
        expect(eventGroupPlaces({...summary, queued: 2})).toEqual({taken: 4, limit: 10, waiting: 2, full: false})
        expect(fullPeoplePlaces({...summary, queued: 2})).toBeNull()
        expect(fullPeoplePlaces({...summary, holding: 10})?.full).toBe(true)
    })

    it('never waits where sign-ups are confirmed: they wait for the decision instead', () => {
        const confirming = {...summary, holding: 10, details: {registrationLimit: 10, requiresConfirmation: true}}
        expect(choiceWaits(confirming, [full], new Set([11]))).toBe(false)
    })
})

/** A waiting list needs something that limits places: people, places per appointment, or a part's own. */
describe('keepsWaitingList', () => {
    it('keeps one for a people limit, places per appointment or a part with places', () => {
        const none = {registrationLimit: null, partRegistrationLimit: null}
        expect(keepsWaitingList(none, [{places: null}])).toBe(false)
        expect(keepsWaitingList({...none, registrationLimit: 10}, [])).toBe(true)
        expect(keepsWaitingList({...none, partRegistrationLimit: 8}, [])).toBe(true)
        expect(keepsWaitingList(none, [{places: null}, {places: 20}])).toBe(true)
    })
})
