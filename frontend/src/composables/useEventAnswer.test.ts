/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {defineComponent, ref, type Ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {StationEvent} from '@/api/events'
import type {Failure} from '@/util/failure'
import {useEventAnswer} from './useEventAnswer'

const registerForEvent = vi.fn()
const declineEvent = vi.fn()
const withdrawRegistration = vi.fn()
const listRegistrationFields = vi.fn()

vi.mock('@/api', () => ({
    events: {
        registerForEvent: (...args: unknown[]) => registerForEvent(...args),
        declineEvent: (...args: unknown[]) => declineEvent(...args),
        withdrawRegistration: (...args: unknown[]) => withdrawRegistration(...args),
        listRegistrationFields: (...args: unknown[]) => listRegistrationFields(...args),
    },
}))

vi.mock('@/composables/useSidebarCounts', () => ({
    useSidebarCounts: () => ({refresh: () => undefined}),
}))

const date = '2026-09-04'
const appointment = {id: 4, stationId: '1', name: 'Übungsabend'} as StationEvent

const child = {key: 11, name: 'Mira'}
const sibling = {key: 12, name: 'Jonas'}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function answerWith(failure: Ref<Failure | null>, afterChange: () => Promise<void> = async () => undefined) {
    let api: ReturnType<typeof useEventAnswer> | null = null
    mount(defineComponent({
        setup() {
            api = useEventAnswer(ref(11), afterChange, failure)
            return () => null
        },
    }))
    return api as unknown as ReturnType<typeof useEventAnswer>
}

describe('useEventAnswer', () => {
    beforeEach(() => {
        registerForEvent.mockReset()
        declineEvent.mockReset()
        withdrawRegistration.mockReset()
        listRegistrationFields.mockReset()
        listRegistrationFields.mockResolvedValue([])
        registerForEvent.mockResolvedValue(undefined)
        declineEvent.mockResolvedValue(undefined)
    })

    /**
     * A guardian answers for the whole household at once, and the answers go out one at a time. The
     * message about the one that was refused used to be wiped by the one that went through, which
     * left them looking at a closed dialog and two children they believed were signed up.
     */
    it('keeps the refusal of the first child on screen after the second has gone through', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.registerFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(registerForEvent, 'both were asked for').toHaveBeenCalledTimes(2)
        expect(failure.value, 'and the one that was refused is still said').not.toBeNull()
    })

    it('says nothing went wrong when the whole household got through', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)

        await answer.registerFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(failure.value).toBeNull()
    })

    it('carries the same refusal out of a household that was turned down', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        declineEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.declineFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(declineEvent).toHaveBeenCalledTimes(2)
        expect(failure.value).not.toBeNull()
    })

    /** The message belongs to the answer being given now, not to the one given before it. */
    it('clears what went wrong last time when the next answer is given', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))
        await answer.registerFor(appointment, date, [child])

        expect(failure.value).not.toBeNull()

        await answer.registerFor(appointment, date, [child])

        expect(failure.value).toBeNull()
    })

    it('says so when taking an answer back is refused', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        withdrawRegistration.mockRejectedValue(new Error('nope'))

        await answer.withdrawRegistration(3)

        expect(failure.value).not.toBeNull()
    })

    /**
     * A sign-up the server took, followed by a list that would not come back, is not a sign-up that
     * failed. Saying it was sends a member who already holds a place to take a second one.
     */
    it('tells a list that would not refresh apart from an answer that was refused', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure, async () => { throw new Error('nope') })

        await answer.registerFor(appointment, date, [child])

        expect(registerForEvent, 'the place was taken').toHaveBeenCalledTimes(1)
        expect(failure.value?.message)
            .toBe('Das hat geklappt, aber die Ansicht konnte danach nicht aktualisiert werden.')
    })

    /**
     * A closed list is the appointment working as intended, so the member is pointed at whoever can
     * still help rather than invited to file a bug about it.
     */
    it('names the lead for a closed list, and offers no report', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValue({response: {status: 400, data: {}}})

        await answer.registerFor(appointment, date, [child])

        expect(failure.value?.message)
            .toBe('Die Anmeldung ist geschlossen. Wer den Termin leitet, kann dich noch auf die Liste setzen.')
        expect(failure.value?.reportable).toBe(false)
    })

    /** One person with nothing to ask is a single press, and the dialog never opens. */
    it('answers for a single person without asking anything', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)

        await answer.registerFor(appointment, date, [child])

        expect(answer.answerPrompt.value).toBeNull()
        expect(registerForEvent).toHaveBeenCalledTimes(1)
    })
})
