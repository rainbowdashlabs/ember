/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {createI18n} from 'vue-i18n'
import type {Inventory, ItemOwner} from '@/api/generated/schema'
import ProcurementCreateModal from './ProcurementCreateModal.vue'
import ProcurementFields from './ProcurementFields.vue'

const createProcurement = vi.fn()
vi.mock('@/api', () => ({
    procurement: {createProcurement: (request: unknown) => createProcurement(request)},
    inventory: {listSizes: vi.fn(async () => [])},
}))

const routes = vi.hoisted(() => ({member: 'station-member' as string | undefined}))
vi.mock('@/composables/useInventoryRoutes', () => ({useInventoryRoutes: () => routes}))

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

const MIXED = {id: 5, name: 'Gemischt', inventoryType: 'MIXED', homogeneous: true} as Inventory
const EXTERNAL = {id: 6, name: 'Vom Träger', inventoryType: 'EXTERNAL', homogeneous: true} as Inventory

const BUTTON = {
    props: ['disabled'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot/></button>',
}

const STUBS = {
    Modal: {template: '<div><slot/></div>'},
    ButtonRow: {template: '<div><slot/></div>'},
    PrimaryButton: BUTTON,
    SecondaryButton: BUTTON,
}

/** Mounts the dialog closed and opens it, as the page does, which is when it starts from its preset. */
async function dialog(props: {presetOwner?: ItemOwner, memberOptional?: boolean} = {}) {
    const wrapper = shallowMount(ProcurementCreateModal, {
        props: {modelValue: false, inventories: [MIXED, EXTERNAL], members: [], ...props},
        global: {plugins: [i18n], stubs: STUBS},
    })
    await wrapper.setProps({modelValue: true})
    return wrapper
}

type Dialog = Awaited<ReturnType<typeof dialog>>

function fields(wrapper: Dialog) {
    return wrapper.findComponent(ProcurementFields)
}

async function choose(wrapper: Dialog, values: Record<string, string>) {
    for (const [name, value] of Object.entries(values)) fields(wrapper).vm.$emit(`update:${name}`, value)
    await flushPromises()
}

function submit(wrapper: Dialog) {
    return wrapper.find('[data-testid="procurement-submit"]')
}

/** Whose piece an order brings is asked only where the inventory leaves it open, and only of a station. */
describe('ProcurementCreateModal', () => {
    beforeEach(() => {
        routes.member = 'station-member'
        createProcurement.mockReset().mockResolvedValue({})
    })

    it('holds back an order in a mixed inventory until it is given its owner', async () => {
        const wrapper = await dialog()
        await choose(wrapper, {inventoryId: '5', memberId: '3'})

        expect(submit(wrapper).attributes('disabled')).toBeDefined()

        await choose(wrapper, {owner: 'CLUSTER'})
        expect(submit(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('sends the owner for a mixed inventory and leaves it to the type elsewhere', async () => {
        const wrapper = await dialog({presetOwner: 'CLUSTER'})
        await choose(wrapper, {inventoryId: '5', memberId: '3'})
        await submit(wrapper).trigger('click')
        await flushPromises()
        expect(createProcurement).toHaveBeenLastCalledWith(expect.objectContaining({inventoryId: 5, owner: 'CLUSTER'}))

        const external = await dialog({presetOwner: 'CLUSTER'})
        await choose(external, {inventoryId: '6', memberId: '3'})
        await submit(external).trigger('click')
        await flushPromises()
        expect(createProcurement).toHaveBeenLastCalledWith(expect.objectContaining({inventoryId: 6, owner: undefined}))
    })

    it('starts again from the owner it was opened for', async () => {
        const wrapper = await dialog({presetOwner: 'CLUSTER'})
        await choose(wrapper, {owner: 'STATION'})

        await wrapper.setProps({modelValue: false})
        await wrapper.setProps({modelValue: true})

        expect(fields(wrapper).props('owner')).toBe('CLUSTER')
    })

    it('lets a request noted down for the association go without a member', async () => {
        const wrapper = await dialog({presetOwner: 'CLUSTER', memberOptional: true})
        await choose(wrapper, {inventoryId: '6'})

        expect(submit(wrapper).attributes('disabled')).toBeUndefined()
    })

    it('asks an association nothing, and its mixed order brings its own piece', async () => {
        routes.member = undefined
        const wrapper = await dialog()
        await choose(wrapper, {inventoryId: '5'})

        expect(fields(wrapper).props('asksOwner')).toBe(false)
        await submit(wrapper).trigger('click')
        await flushPromises()
        expect(createProcurement).toHaveBeenLastCalledWith(expect.objectContaining({inventoryId: 5, owner: 'CLUSTER'}))
    })
})
