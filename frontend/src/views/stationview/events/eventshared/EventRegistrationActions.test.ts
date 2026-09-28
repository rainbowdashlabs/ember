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
})
