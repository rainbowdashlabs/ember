/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import SwapOrder from './SwapOrder.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'

const held = {procurement: true, module: true}
const api = vi.hoisted(() => ({procureForSwap: vi.fn(), deleteProcurement: vi.fn()}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => held.procurement, isModuleEnabled: () => held.module}),
}))

vi.mock('@/api', () => ({procurement: api}))

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}, en: {}},
})

function swapOrder(props: Record<string, unknown>) {
    return mount(SwapOrder, {
        global: {plugins: [i18n], stubs: {'font-awesome-icon': true}},
        props: {movementId: 5, orderId: null, nothingFits: true, busy: false, ...props},
    })
}

/** Ordering the replacement of a swap from the step that would otherwise pick it. */
describe('SwapOrder', () => {
    beforeEach(() => {
        held.procurement = true
        held.module = true
        api.procureForSwap.mockReset().mockResolvedValue({})
        api.deleteProcurement.mockReset().mockResolvedValue(undefined)
    })

    it('orders for the swap and lets the page read it again', async () => {
        const changed = vi.fn()
        const wrapper = swapOrder({onChanged: changed})

        await wrapper.get('[data-testid="swap-order-place"]').trigger('click')
        await flushPromises()

        expect(api.procureForSwap).toHaveBeenCalledWith(5)
        expect(changed).toHaveBeenCalled()
    })

    it('offers nothing to somebody who may not order, or where the station orders nothing', () => {
        held.procurement = false
        expect(swapOrder({}).find('[data-testid="swap-order-place"]').exists()).toBe(false)

        held.procurement = true
        held.module = false
        expect(swapOrder({}).find('[data-testid="swap-order-place"]').exists()).toBe(false)
    })

    it('deletes the order once asked, and lets the page read the swap again', async () => {
        const changed = vi.fn()
        const wrapper = swapOrder({orderId: 9, onChanged: changed})

        await wrapper.get('[data-testid="swap-order-delete"]').trigger('click')
        wrapper.findComponent(ConfirmDeleteModal).vm.$emit('confirm')
        await flushPromises()

        expect(api.deleteProcurement).toHaveBeenCalledWith(9)
        expect(changed).toHaveBeenCalled()
    })

    it('shows the waiting swap with a way to the order', () => {
        const wrapper = swapOrder({orderId: 9})
        expect(wrapper.find('[data-testid="swap-order-waiting"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="swap-order-place"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="swap-order-delete"]').exists()).toBe(true)
    })
})
