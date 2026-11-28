/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {LendingRequestResponse, LendingStatus} from '@/api/generated/schema'
import LendColumn from './LendColumn.vue'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function asked(id: number, allowedSteps: LendingStatus[]): LendingRequestResponse {
    return {
        allowedSteps, isOwner: true, overdue: false, itemSummary: '', owningStationName: 'Hier', requestingStationName: 'Süd',
        request: {id, status: 'REQUESTED', requestedDateFrom: '2030-05-01', requestedDateTo: '2030-05-03', createdAt: '2030-04-01T00:00:00Z',
            createdBy: null, eventDate: null, eventId: null, occasion: '', owningStationUid: 'a', requestingStationUid: 'b', uid: String(id)},
    } as LendingRequestResponse
}

function overdue(id: number): LendingRequestResponse {
    return {
        allowedSteps: ['RETURNED'],
        isOwner: true, overdue: true, itemSummary: '', owningStationName: 'Hier', requestingStationName: 'Süd',
        request: {id, status: 'LENT', requestedDateFrom: '2030-05-01', requestedDateTo: '2030-05-03', createdAt: '2030-04-01T00:00:00Z',
            createdBy: null, eventDate: null, eventId: null, occasion: '', owningStationUid: 'a', requestingStationUid: 'b', uid: String(id)},
    } as LendingRequestResponse
}

/** An overdue loan offers its reminder right in its row. */
describe('LendColumn', () => {
    it('reminds of an overdue loan from its row', async () => {
        const wrapper = mount(LendColumn, {
            props: {blocks: {toAnswer: [], handOutSoon: [], outNow: [], overdue: [overdue(9)]}},
            global: {plugins: [i18n], stubs: {RouterLink: {template: '<a><slot/></a>'}}},
        })

        const button = wrapper.findAll('button').find(candidate => candidate.attributes('aria-label') === 'lendingOverview.remindFor')
        await button!.trigger('click')
        expect(wrapper.emitted('remind')?.[0]).toEqual([9])
    })

    it('offers on a request only the steps the server allows the reader', () => {
        const wrapper = mount(LendColumn, {
            props: {blocks: {toAnswer: [asked(1, ['APPROVED', 'DECLINED']), asked(2, [])], handOutSoon: [], outNow: [], overdue: []}},
            global: {plugins: [i18n], stubs: {RouterLink: {template: '<a><slot/></a>'}}},
        })

        const labels = wrapper.findAll('button').map(button => button.attributes('aria-label'))
        expect(labels.filter(label => label === 'lendingOverview.approveFrom')).toHaveLength(1)
        expect(labels.filter(label => label === 'lendingOverview.declineFrom')).toHaveLength(1)
    })
})
