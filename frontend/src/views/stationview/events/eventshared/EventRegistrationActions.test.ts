/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventRegistrationActions from './EventRegistrationActions.vue'
import {answerableMembers} from '@/util/eventAnswers'

const SELF = 7
const CHILD = {id: 8, name: 'Kim Berger'}

/** The controls for an appointment asking to be signed up for, for whoever the server named. */
function mountFor(people: {key: number; name: string}[], hasManagedMembers = false) {
    return mountSuspended(EventRegistrationActions, {
        props: {
            people,
            answers: [],
            requiresRegistration: true,
            registrationDeadline: null,
            hasManagedMembers,
            registering: false,
        },
    })
}

/**
 * A sign-up is offered only to those the appointment is open to. Offering it to anybody else only
 * led to the server refusing it once pressed.
 */
describe('EventRegistrationActions', () => {
    it('offers to sign up where the appointment is open to the reader', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountFor(people)

        expect(actions.find('[data-testid="answer-selected"]').exists()).toBe(true)
        expect(actions.text()).not.toContain('Teil der Wache')
    })

    it('offers nothing and says why where the appointment is open to nobody here', async () => {
        const people = answerableMembers(1, {1: []}, SELF, [], 'Ich')
        const actions = await mountFor(people)

        expect(actions.find('[data-testid="answer-selected"]').exists()).toBe(false)
        expect(actions.text()).toContain('Die Anmeldung steht nur einem Teil der Wache offen, dir nicht.')
    })

    it('offers a household only the members the appointment is open to', async () => {
        const people = answerableMembers(1, {1: [CHILD.id]}, SELF, [CHILD], 'Ich')
        const actions = await mountFor(people, true)

        expect(people.map(person => person.key)).toEqual([CHILD.id])
        expect(actions.get('[data-testid="answer-selected"]').text()).toContain('Kim Berger')
    })

    it('names the household where the appointment is open to none of it', async () => {
        const people = answerableMembers(1, {1: []}, SELF, [CHILD], 'Ich')
        const actions = await mountFor(people, true)

        expect(actions.find('[data-testid="answer-selected"]').exists()).toBe(false)
        expect(actions.text()).toContain('weder dir noch den Personen, die du verwaltest')
    })

    /** Whoever has no time on that date says so, which stops the reminders that registration is closing. */
    it('offers to decline beside signing up, for everybody still open', async () => {
        const people = answerableMembers(1, {1: [SELF, CHILD.id]}, SELF, [CHILD], 'Ich')
        const actions = await mountFor(people, true)

        await actions.get('[data-testid="decline-registration"]').trigger('click')

        expect(actions.get('[data-testid="decline-registration"]').text()).toContain('Abmelden')
        expect(actions.emitted('decline')?.[0]?.[0]).toEqual(people)
    })

    /** A place given up stands as an answer and is asked for anew rather than taken back. */
    it('offers to sign up again for a place given up', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountSuspended(EventRegistrationActions, {
            props: {
                people,
                answers: [{key: SELF, name: 'Ich', status: 'WITHDRAWN', undo: 41, placeGivenUp: true}],
                requiresRegistration: true,
                registrationDeadline: null,
                hasManagedMembers: false,
                registering: false,
            },
        })

        const again = actions.get(`[data-testid="undo-answer-${SELF}"]`)
        await again.trigger('click')

        expect(again.text()).toContain('Anmelden')
        expect(actions.find('[data-testid="answer-selected"]').exists()).toBe(false)
        expect(actions.emitted('register')?.[0]?.[0]).toEqual(people)
        expect(actions.emitted('withdraw')).toBeUndefined()
    })

    /** Once registration has closed, the server takes no answer, so nothing offers one. */
    it('offers no decline once registration has closed', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountSuspended(EventRegistrationActions, {
            props: {
                people,
                answers: [],
                requiresRegistration: true,
                registrationDeadline: '2020-01-01T00:00:00Z',
                hasManagedMembers: false,
                registering: false,
            },
        })

        expect(actions.find('[data-testid="decline-registration"]').exists()).toBe(false)
    })

    /** After the deadline a place given up stays given up: signing up again is no longer taken. */
    it('offers no new sign-up for a place given up once registration has closed', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountSuspended(EventRegistrationActions, {
            props: {
                people,
                answers: [{key: SELF, name: 'Ich', status: 'WITHDRAWN', undo: 41, placeGivenUp: true}],
                requiresRegistration: true,
                registrationDeadline: '2020-01-01T00:00:00Z',
                hasManagedMembers: false,
                registering: false,
            },
        })

        expect(actions.find(`[data-testid="undo-answer-${SELF}"]`).exists()).toBe(false)
    })

    /** A guardian reads whose refusal the button records, the same as the sign-up beside it. */
    it('names the one person a decline is for', async () => {
        const people = answerableMembers(1, {1: [CHILD.id]}, SELF, [CHILD], 'Ich')
        const actions = await mountFor(people, true)

        expect(actions.get('[data-testid="decline-registration"]').text()).toContain('Kim Berger')
    })

    /** A partner station takes sign-ups only, so its appointments offer no decline. */
    it('offers no decline where the appointment cannot be declined', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountSuspended(EventRegistrationActions, {
            props: {
                people,
                answers: [],
                requiresRegistration: true,
                registrationDeadline: null,
                hasManagedMembers: false,
                registering: false,
                declinable: false,
            },
        })

        expect(actions.find('[data-testid="answer-selected"]').exists()).toBe(true)
        expect(actions.find('[data-testid="decline-registration"]').exists()).toBe(false)
    })

    /** Where everybody is expected, the refusal is the one answer and there is no sign-up to sit beside. */
    it('offers only the refusal where everybody is expected', async () => {
        const people = answerableMembers(1, {1: [SELF]}, SELF, [], 'Ich')
        const actions = await mountSuspended(EventRegistrationActions, {
            props: {
                people,
                answers: [],
                requiresRegistration: false,
                registrationDeadline: null,
                hasManagedMembers: false,
                registering: false,
            },
        })

        expect(actions.findAll('[data-testid="answer-selected"]')).toHaveLength(1)
        expect(actions.get('[data-testid="answer-selected"]').text()).toContain('Abmelden')
        expect(actions.find('[data-testid="decline-registration"]').exists()).toBe(false)
    })
})
