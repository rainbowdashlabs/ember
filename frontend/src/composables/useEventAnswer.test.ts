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

const evening = '2026-09-04'
const appointment = {id: 4, stationId: '1', name: 'Übungsabend'} as StationEvent

const child = {key: 11, name: 'Mira'}
const sibling = {key: 12, name: 'Jonas'}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function answerWith(error: Ref<string>) {
    let api: ReturnType<typeof useEventAnswer> | null = null
    mount(defineComponent({
        setup() {
            api = useEventAnswer(ref(11), async () => undefined, error)
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
        const error = ref('')
        const answer = answerWith(error)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.registerFor(appointment, evening, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(registerForEvent, 'both were asked for').toHaveBeenCalledTimes(2)
        expect(error.value, 'and the one that was refused is still said').not.toBe('')
    })

    it('says nothing went wrong when the whole household got through', async () => {
        const error = ref('')
        const answer = answerWith(error)

        await answer.registerFor(appointment, evening, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(error.value).toBe('')
    })

    it('carries the same refusal out of a household that was turned down', async () => {
        const error = ref('')
        const answer = answerWith(error)
        declineEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.declineFor(appointment, evening, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(declineEvent).toHaveBeenCalledTimes(2)
        expect(error.value).not.toBe('')
    })

    /** The message belongs to the answer being given now, not to the one given before it. */
    it('clears what went wrong last time when the next answer is given', async () => {
        const error = ref('')
        const answer = answerWith(error)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))
        await answer.registerFor(appointment, evening, [child])

        expect(error.value).not.toBe('')

        await answer.registerFor(appointment, evening, [child])

        expect(error.value).toBe('')
    })

    it('says so when taking an answer back is refused', async () => {
        const error = ref('')
        const answer = answerWith(error)
        withdrawRegistration.mockRejectedValue(new Error('nope'))

        await answer.withdrawRegistration(3)

        expect(error.value).not.toBe('')
    })

    /** One person with nothing to ask is a single press, and the dialog never opens. */
    it('answers for a single person without asking anything', async () => {
        const error = ref('')
        const answer = answerWith(error)

        await answer.registerFor(appointment, evening, [child])

        expect(answer.answerPrompt.value).toBeNull()
        expect(registerForEvent).toHaveBeenCalledTimes(1)
    })
})
