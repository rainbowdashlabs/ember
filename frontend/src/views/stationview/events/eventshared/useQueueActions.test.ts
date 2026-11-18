/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {RegistrationStatus, WhenFull} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'
import type {QueueEntry} from '@/util/registrationQueue'
import {useQueueActions} from './useQueueActions'

const api = vi.hoisted(() => ({
    events: {
        updateRegistrationStatus: vi.fn(),
        updateFederationRegistrationStatus: vi.fn(),
        moveRegistrationsToQueue: vi.fn(),
        moveFederationRegistrationsToQueue: vi.fn(),
        moveRegistrationInQueue: vi.fn(),
        moveFederationRegistrationInQueue: vi.fn(),
    },
}))
vi.mock('@/api', () => api)

const FULL = {response: {status: 409, data: {code: 'E-226', error: 'full'}}}
const places = {taken: 12, limit: 12, waiting: 2, full: true}

function entry(kind: QueueEntry['kind'], id: number): QueueEntry {
    return {kind, id, position: 1, identity: null, name: 'Jonas', partnerId: kind === 'partner' ? 4 : null}
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function actionsWith(reload = vi.fn(async () => undefined)) {
    const failure = ref<Failure | null>(null)
    let actions: ReturnType<typeof useQueueActions> | null = null
    mount(defineComponent({
        setup() {
            actions = useQueueActions(() => places, reload, failure)
            return () => null
        },
    }), {global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}})]}})
    return {actions: actions as unknown as ReturnType<typeof useQueueActions>, failure, reload}
}

/** Whoever decides the sign-ups keeps the waiting list through the routes of each kind of sign-up. */
describe('useQueueActions', () => {
    beforeEach(() => {
        Object.values(api.events).forEach(mock => mock.mockReset())
    })

    /** Accepting on a full date is asked about first, and sent again beyond the limit once agreed. */
    it('asks before accepting beyond the limit and sends it again once agreed', async () => {
        api.events.updateRegistrationStatus.mockRejectedValueOnce(FULL).mockResolvedValueOnce({message: 'ok'})
        const {actions, reload} = actionsWith()

        const accepting = actions.accept(5)
        await vi.waitFor(() => expect(actions.fullDateQuestion.value).not.toBeNull())
        expect(actions.fullDateQuestion.value).toEqual({kind: 'accept', places})

        actions.answerFullDate(WhenFull.OVER_LIMIT)
        await accepting

        expect(api.events.updateRegistrationStatus.mock.calls).toEqual([
            [5, RegistrationStatus.ACCEPTED, false],
            [5, RegistrationStatus.ACCEPTED, true],
        ])
        expect(reload).toHaveBeenCalledOnce()
    })

    it('leaves the sign-up as it is where the question is dismissed', async () => {
        api.events.updateRegistrationStatus.mockRejectedValueOnce(FULL)
        const {actions, failure} = actionsWith()

        const accepting = actions.accept(5)
        await vi.waitFor(() => expect(actions.fullDateQuestion.value).not.toBeNull())
        actions.answerFullDate(null)
        await accepting

        expect(api.events.updateRegistrationStatus).toHaveBeenCalledOnce()
        expect(failure.value).toBeNull()
    })

    /** Moving somebody up is accepting them, a partner's member through the partner route. */
    it("moves a partner's member up through the partner route", async () => {
        api.events.updateFederationRegistrationStatus.mockResolvedValue(undefined)
        const {actions} = actionsWith()

        await actions.moveUp(entry('partner', 9))

        expect(api.events.updateFederationRegistrationStatus).toHaveBeenCalledWith(9, RegistrationStatus.ACCEPTED, false)
        expect(api.events.updateRegistrationStatus).not.toHaveBeenCalled()
    })

    it('reorders each kind through its own route', async () => {
        const {actions} = actionsWith()

        await actions.reorder(entry('member', 3), 1)
        await actions.reorder(entry('partner', 9), 2)

        expect(api.events.moveRegistrationInQueue).toHaveBeenCalledWith(3, 1)
        expect(api.events.moveFederationRegistrationInQueue).toHaveBeenCalledWith(9, 2)
    })

    it('puts pending sign-ups on the waiting list together and nothing where none is given', async () => {
        const {actions} = actionsWith()

        await actions.queue([4, 2])
        await actions.queue([])

        expect(api.events.moveRegistrationsToQueue.mock.calls).toEqual([[[4, 2]]])
    })

    /** Any other refusal is said as it is, and nothing is read back. */
    it('says a refusal and reads nothing back', async () => {
        api.events.moveRegistrationInQueue.mockRejectedValue({response: {status: 409, data: {code: 'E-228', error: 'not queued'}}})
        const {actions, failure, reload} = actionsWith()

        await actions.reorder(entry('member', 3), 2)

        expect(failure.value).not.toBeNull()
        expect(reload).not.toHaveBeenCalled()
    })
})
