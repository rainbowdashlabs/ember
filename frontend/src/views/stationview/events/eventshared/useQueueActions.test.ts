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
import {appointmentStatuses, useQueueActions} from './useQueueActions'
import {followModifierKeys} from '@/util/modifierKeys'
import type {EnrichedFederationRegistration, RegistrationResponse} from '@/api/generated/schema'

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

const toast = vi.hoisted(() => ({showToast: vi.fn()}))
vi.mock('@/util/toast', () => toast)

const FULL = {response: {status: 409, data: {code: 'E-226', error: 'full'}}}
const places = {taken: 12, limit: 12, waiting: 2, full: true}

function entry(kind: QueueEntry['kind'], id: number): QueueEntry {
    return {kind, id, position: 1, identity: null, name: 'Jonas', partnerId: kind === 'partner' ? 4 : null}
}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function actionsWith(
    reload = vi.fn(async () => undefined),
    statusOf: (id: number, partner: boolean) => RegistrationStatus | undefined = () => undefined,
) {
    const failure = ref<Failure | null>(null)
    let actions: ReturnType<typeof useQueueActions> | null = null
    mount(defineComponent({
        setup() {
            actions = useQueueActions(() => places, reload, failure, undefined, statusOf)
            return () => null
        },
    }), {global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}})]}})
    return {actions: actions as unknown as ReturnType<typeof useQueueActions>, failure, reload}
}

/** Whoever decides the sign-ups keeps the waiting list through the routes of each kind of sign-up. */
describe('useQueueActions', () => {
    followModifierKeys()

    beforeEach(() => {
        Object.values(api.events).forEach(mock => mock.mockReset())
        toast.showToast.mockReset()
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
        expect(toast.showToast).not.toHaveBeenCalled()
    })

    it('says an acceptance went through', async () => {
        api.events.updateRegistrationStatus.mockResolvedValue({message: 'ok'})
        const {actions} = actionsWith()

        expect(await actions.accept(5)).toBe(true)

        expect(toast.showToast).toHaveBeenCalledWith('registrationQueue.accepted', 'success')
    })

    /** A sign-up still waiting loses nothing by being denied, so nothing is asked. */
    it('denies a waiting sign-up at once and says so', async () => {
        api.events.updateRegistrationStatus.mockResolvedValue({message: 'ok'})
        const {actions} = actionsWith(undefined, () => RegistrationStatus.PENDING)

        await actions.deny(5)

        expect(actions.denyQuestion.value).toBe(false)
        expect(api.events.updateRegistrationStatus).toHaveBeenCalledWith(5, RegistrationStatus.DENIED, undefined)
        expect(toast.showToast).toHaveBeenCalledWith('registrationQueue.denied', 'success')
    })

    /** Denying somebody who holds a place takes it away, so it is asked first. */
    it('asks before denying an accepted sign-up and sends it once agreed', async () => {
        api.events.updateFederationRegistrationStatus.mockResolvedValue(undefined)
        const statusOf = vi.fn(() => RegistrationStatus.ACCEPTED)
        const {actions} = actionsWith(undefined, statusOf)

        const denying = actions.deny(9, true)
        await vi.waitFor(() => expect(actions.denyQuestion.value).toBe(true))
        expect(api.events.updateFederationRegistrationStatus).not.toHaveBeenCalled()
        actions.answerDeny(true)
        await denying

        expect(statusOf).toHaveBeenCalledWith(9, true)
        expect(api.events.updateFederationRegistrationStatus).toHaveBeenCalledWith(9, RegistrationStatus.DENIED, undefined)
    })

    /** Shift skips this question as it does every plain confirmation. */
    it('denies an accepted sign-up at once while shift is held', async () => {
        api.events.updateRegistrationStatus.mockResolvedValue({message: 'ok'})
        const {actions} = actionsWith(undefined, () => RegistrationStatus.ACCEPTED)

        window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Shift'}))
        try {
            expect(await actions.deny(5)).toBe(true)
        } finally {
            window.dispatchEvent(new KeyboardEvent('keyup', {key: 'Shift'}))
        }

        expect(actions.denyQuestion.value).toBe(false)
        expect(api.events.updateRegistrationStatus).toHaveBeenCalledWith(5, RegistrationStatus.DENIED, undefined)
    })

    it('leaves an accepted sign-up where the question is turned down', async () => {
        const {actions} = actionsWith(undefined, () => RegistrationStatus.ACCEPTED)

        const denying = actions.deny(5)
        await vi.waitFor(() => expect(actions.denyQuestion.value).toBe(true))
        actions.answerDeny(false)

        expect(await denying).toBe(false)
        expect(api.events.updateRegistrationStatus).not.toHaveBeenCalled()
        expect(toast.showToast).not.toHaveBeenCalled()
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

/** An appointment date's own sign-ups are found by their id, partners' by the id of their registration. */
describe('appointmentStatuses', () => {
    const own = [{id: 5, status: RegistrationStatus.ACCEPTED}] as RegistrationResponse[]
    const partners = [{registration: {id: 5, status: RegistrationStatus.PENDING}}] as EnrichedFederationRegistration[]
    const statusOf = appointmentStatuses(() => own, () => partners)

    it('tells the two kinds apart where their ids are the same', () => {
        expect(statusOf(5, false)).toBe(RegistrationStatus.ACCEPTED)
        expect(statusOf(5, true)).toBe(RegistrationStatus.PENDING)
        expect(statusOf(6, false)).toBeUndefined()
    })
})
