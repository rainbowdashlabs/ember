/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it, vi} from 'vitest'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import BorrowAgainPanel from './BorrowAgainPanel.vue'

const borrowAgain = vi.fn()
vi.mock('@/api/lendingCart', () => ({
    borrowAgain: (id: number) => borrowAgain(id),
    getCart: () => Promise.resolve({dateFrom: null, dateTo: null, occasion: '', note: '', lines: []}),
}))
const reload = vi.fn()
vi.mock('@/composables/useLendingCart', () => ({useLendingCart: () => ({reload})}))
vi.mock('vue-router', () => ({useRouter: () => ({resolve: () => ({href: '/station/inventory/lending/cart'})})}))

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {'de-DE': {lendingCart: {againAdded: '{count} Zeilen', againSkipped: 'Nicht: {names}'}}, en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

/** An answered request goes back in the cart, and what is no longer offered is named. */
describe('BorrowAgainPanel', () => {
    it('puts the request back in the cart and names what was left out', async () => {
        borrowAgain.mockResolvedValue({added: 2, skipped: ['Zelt']})
        const wrapper = mount(BorrowAgainPanel, {props: {requestId: 41}, global: {plugins: [i18n], stubs: {NuxtLink: {template: '<a><slot/></a>'}}}})

        await wrapper.findComponent(SecondaryButton).vm.$emit('click')
        await flushPromises()

        expect(borrowAgain).toHaveBeenCalledWith(41)
        expect(reload).toHaveBeenCalled()
        const result = wrapper.get('[data-testid="lending-borrow-again-result"]').text()
        expect(result).toContain('2 Zeilen')
        expect(result).toContain('Nicht: Zelt')
    })
})
