/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {RegistrationStatus, type EnrichedFederationRegistration, type EventRegistrationField} from '@/api/generated/schema'
import {events} from '@/api'
import FederatedRegistrationsPanel from './FederatedRegistrationsPanel.vue'

vi.mock('@/api', () => ({
    events: {listPartnerAnswers: vi.fn()},
}))

const SIZE = {id: 5, name: 'Größe', fieldType: 'TEXT', config: {required: true}, overview: true} as unknown as EventRegistrationField

function partnerSignUp(id: number): EnrichedFederationRegistration {
    return {
        registration: {
            id, eventId: 40, partnerId: 7, remoteMemberId: 'a', eventDate: '2026-12-01', status: RegistrationStatus.PENDING,
            createdAt: '2026-11-18T10:00:00Z', agreementWithdrawnAt: null, comingOrAsking: true, standing: true,
        },
        memberIdentity: null,
        queuePosition: null,
    }
}

async function mountPanel(fields: EventRegistrationField[], registrations = [partnerSignUp(9), partnerSignUp(10)]) {
    const panel = await mountSuspended(FederatedRegistrationsPanel, {
        props: {registrations, partnerPlaces: [], fields},
    })
    await flushPromises()
    return panel
}

/** Whoever decides about partners' members reads what each of them answered beside their sign-up. */
describe('FederatedRegistrationsPanel', () => {
    beforeEach(() => {
        vi.mocked(events.listPartnerAnswers).mockReset()
        vi.mocked(events.listPartnerAnswers).mockResolvedValue([{registrationId: 9, answers: [{fieldId: 5, value: 'XL'}]}])
    })

    it('shows the answers of each partner sign-up and names a required one still missing', async () => {
        const panel = await mountPanel([SIZE])

        expect(events.listPartnerAnswers).toHaveBeenCalledWith(40)
        expect(panel.get('[data-testid="federated-answers-9"]').text()).toContain('XL')
        expect(panel.get('[data-testid="federated-answers-10"]').text()).toContain('Größe')
    })

    it('asks for no answers where the appointment asks nothing', async () => {
        await mountPanel([])

        expect(events.listPartnerAnswers).not.toHaveBeenCalled()
    })

    it('says so where the answers cannot be read', async () => {
        vi.mocked(events.listPartnerAnswers).mockRejectedValue(new Error('down'))

        const panel = await mountPanel([SIZE])

        expect(panel.find('[role="alert"]').exists()).toBe(true)
    })

    it('forgets the answers once no partner sign-up is left', async () => {
        const panel = await mountPanel([SIZE])

        await panel.setProps({registrations: []})
        await flushPromises()
        vi.mocked(events.listPartnerAnswers).mockReturnValue(new Promise(() => {}))
        await panel.setProps({registrations: [partnerSignUp(9)]})
        await flushPromises()

        expect(panel.get('[data-testid="federated-answers-9"]').text()).not.toContain('XL')
    })

    it('drops answers that arrive after a newer read started', async () => {
        let answerLate: (value: Awaited<ReturnType<typeof events.listPartnerAnswers>>) => void = () => {}
        vi.mocked(events.listPartnerAnswers)
            .mockReturnValueOnce(new Promise(resolve => {
                answerLate = resolve
            }))
            .mockResolvedValueOnce([{registrationId: 9, answers: [{fieldId: 5, value: 'S'}]}])
        const panel = await mountSuspended(FederatedRegistrationsPanel, {
            props: {registrations: [partnerSignUp(9)], partnerPlaces: [], fields: [SIZE]},
        })

        await panel.setProps({registrations: [partnerSignUp(9), partnerSignUp(10)]})
        await flushPromises()
        answerLate([{registrationId: 9, answers: [{fieldId: 5, value: 'XL'}]}])
        await flushPromises()

        expect(panel.get('[data-testid="federated-answers-9"]').text()).toContain('S')
        expect(panel.get('[data-testid="federated-answers-9"]').text()).not.toContain('XL')
    })
})
