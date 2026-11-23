/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import ProcurementEntryDetails from './ProcurementEntryDetails.vue'
import type {ProcurementResponse} from '@/api/generated/schema'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}, en: {}},
})

function order(movementId: number | null): ProcurementResponse {
    return {
        id: 1, inventoryId: 2, inventoryName: 'Jacken', memberId: null, memberName: '', memberIdentity: null,
        sizeId: null, sizeLabel: null, notes: '', requestedAt: '2026-05-01T08:00:00Z', fulfilledAt: null,
        stock: [], movementId,
    }
}

/** What an order says about itself on the procurement page. */
describe('ProcurementEntryDetails', () => {
    it('leads to the swap an order is waiting for, and nowhere for an order of its own', () => {
        const forSwap = mount(ProcurementEntryDetails, {global: {plugins: [i18n]}, props: {entry: order(31)}})
        const ownOrder = mount(ProcurementEntryDetails, {global: {plugins: [i18n]}, props: {entry: order(null)}})

        expect(forSwap.find('[data-testid="procurement-for-swap"]').exists()).toBe(true)
        expect(ownOrder.find('[data-testid="procurement-for-swap"]').exists()).toBe(false)
    })
})
