/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RegistrationStatus} from '@/api/generated/schema'
import {placesFromCounts, placesFromResponse, placesLabel, placesOf, signUpQueues} from './eventPlaces'

const say = (key: string, named?: Record<string, unknown>) => `${key} ${JSON.stringify(named ?? {})}`

const accepted = {status: RegistrationStatus.ACCEPTED}
const queued = {status: RegistrationStatus.QUEUED}
const pending = {status: RegistrationStatus.PENDING}

/** Only accepted sign-ups take a place, and a date is full once they fill it or somebody waits. */
describe('placesOf', () => {
    it('limits nothing without a limit', () => {
        expect(placesOf(null, [accepted])).toBeNull()
        expect(placesOf(0, [accepted])).toBeNull()
    })

    it('counts accepted sign-ups as taken and leaves pending ones out', () => {
        expect(placesOf(3, [accepted, pending, accepted])).toEqual({taken: 2, limit: 3, waiting: 0, full: false})
    })

    it('is full once every place is taken', () => {
        expect(placesOf(2, [accepted, accepted])?.full).toBe(true)
    })

    it('is full while anybody waits, even with a place free', () => {
        expect(placesOf(3, [accepted, queued])).toEqual({taken: 1, limit: 3, waiting: 1, full: true})
    })
})

describe('placesFromCounts', () => {
    it('reads the places from the counts per status', () => {
        const counts = [
            {status: RegistrationStatus.ACCEPTED, count: 12},
            {status: RegistrationStatus.QUEUED, count: 4},
            {status: RegistrationStatus.PENDING, count: 2},
        ]
        expect(placesFromCounts(12, counts)).toEqual({taken: 12, limit: 12, waiting: 4, full: true})
    })
})

/** The server counts the places of a date, partners' members included. */
describe('placesFromResponse', () => {
    it('reads the places the server counted', () => {
        expect(placesFromResponse({limit: 12, accepted: 8, queued: 0})).toEqual({taken: 8, limit: 12, waiting: 0, full: false})
        expect(placesFromResponse({limit: 12, accepted: 11, queued: 1})?.full).toBe(true)
    })

    it('limits nothing without a limit', () => {
        expect(placesFromResponse({limit: null, accepted: 3, queued: 0})).toBeNull()
    })
})

/** The line shows how many places are taken while some are free, and how many wait once none is. */
describe('placesLabel', () => {
    it('says how many places are taken while some are free', () => {
        expect(placesLabel({taken: 8, limit: 12, waiting: 0, full: false}, say))
            .toBe('registrationQueue.placesTaken {"taken":8,"limit":12,"waiting":0}')
    })

    it('says full and how many wait', () => {
        expect(placesLabel({taken: 12, limit: 12, waiting: 4, full: true}, say))
            .toBe('registrationQueue.fullWaiting {"taken":12,"limit":12,"waiting":4}')
    })

    /** A manager who let somebody in beyond the limit sees the overrun, not merely full. */
    it('says the count as it is beyond the limit', () => {
        expect(placesLabel({taken: 13, limit: 12, waiting: 0, full: true}, say))
            .toBe('registrationQueue.placesTaken {"taken":13,"limit":12,"waiting":0}')
        expect(placesLabel({taken: 13, limit: 12, waiting: 2, full: true}, say))
            .toBe('registrationQueue.placesTakenWaiting {"taken":13,"limit":12,"waiting":2}')
    })

    /** An event group can have a place free while people wait for a full part: both are shown. */
    it('says the taken places and the waiting ones where a place is free and people wait', () => {
        expect(placesLabel({taken: 4, limit: 10, waiting: 2, full: false}, say))
            .toBe('registrationQueue.placesTakenWaiting {"taken":4,"limit":10,"waiting":2}')
    })

    it('says full alone where nobody waits', () => {
        expect(placesLabel({taken: 12, limit: 12, waiting: 0, full: true}, say)).toBe('registrationQueue.full {}')
    })
})

/** Signing up queues only on a full date that takes sign-ups as they come. */
describe('signUpQueues', () => {
    const full = {taken: 2, limit: 2, waiting: 0, full: true}

    it('queues on a full date without confirmation', () => {
        expect(signUpQueues(full, false)).toBe(true)
    })

    it('leaves the decision to the manager where sign-ups are confirmed', () => {
        expect(signUpQueues(full, true)).toBe(false)
    })

    it('gives a place while one is free, and where nothing is limited', () => {
        expect(signUpQueues({...full, taken: 1, full: false}, false)).toBe(false)
        expect(signUpQueues(null, false)).toBe(false)
    })
})
