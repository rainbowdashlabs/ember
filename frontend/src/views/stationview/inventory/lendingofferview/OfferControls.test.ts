/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount, shallowMount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import ErrorButton from '@/components/button/ErrorButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import LendingShareModal from '@/components/lending/LendingShareModal.vue'
import BulkActions from './BulkActions.vue'
import DescriptionDrawer from './DescriptionDrawer.vue'
import ShareControl from './ShareControl.vue'
import type {CatalogRow} from './catalogRows'

const setShare = vi.fn()
const removeShare = vi.fn()
vi.mock('@/api/lending', () => ({
    setShare: (...args: unknown[]) => setShare(...args),
    removeShare: (...args: unknown[]) => removeShare(...args),
}))
const describeInventory = vi.fn()
const describeArt = vi.fn()
const describeItem = vi.fn()
vi.mock('@/api', () => ({
    inventory: {describeInventory: (...a: unknown[]) => describeInventory(...a), describeItem: (...a: unknown[]) => describeItem(...a)},
    inventoryArts: {describeArt: (...a: unknown[]) => describeArt(...a)},
    inventoryPictures: {pictureUrl: (key: string) => `/p/${key}`},
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})
const global = {plugins: [i18n], stubs: {LendingShareModal: true}}

function row(target: CatalogRow['target'], id: number): CatalogRow {
    return {key: `${target}:${id}`, target, id, inventoryId: 1, artId: null, name: 'Funk', detail: '', description: 'alt',
        pictureKey: null, share: null, visible: null, depth: target === 'inventory' ? 0 : 1}
}

/** The catalog's controls write the share rules and texts the way each level needs. */
describe('offer catalog controls', () => {
    beforeEach(() => {
        setShare.mockReset().mockResolvedValue({})
        removeShare.mockReset().mockResolvedValue(undefined)
        describeInventory.mockReset().mockResolvedValue({})
        describeArt.mockReset().mockResolvedValue({})
        describeItem.mockReset().mockResolvedValue({})
    })

    it('offers to all, holds a kind back, takes a rule away, and picks partners in the dialog', async () => {
        const control = mount(ShareControl, {props: {target: 'art', targetId: 5, targetName: 'FuG', share: null}, global})
        const select = control.get('select')

        await select.setValue('all')
        await flushPromises()
        expect(setShare).toHaveBeenLastCalledWith('art', 5, {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []})
        await select.setValue('withheld')
        await flushPromises()
        expect(setShare).toHaveBeenLastCalledWith('art', 5, {grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []})
        await select.setValue('inherit')
        await flushPromises()
        expect(removeShare).toHaveBeenCalledWith('art', 5)
        await select.setValue('some')
        expect(control.findComponent(LendingShareModal).props('modelValue')).toBe(true)
        expect((select.element as HTMLSelectElement).value).toBe('inherit')
        expect(control.emitted('changed')).toHaveLength(3)
    })

    it('shows the choice as text where it is only shown, and reopens the partners of a chosen-partners rule', async () => {
        const some = {grant: 'GRANT' as const, scope: 'SPECIFIC' as const, partnerIds: [1, 2]}
        const shown = mount(ShareControl, {props: {target: 'inventory', targetId: 1, targetName: 'Funk', share: some, readonly: true}, global})
        expect(shown.find('select').exists()).toBe(false)

        const control = mount(ShareControl, {props: {target: 'inventory', targetId: 1, targetName: 'Funk', share: some}, global})
        await control.findComponent(LinkButton).trigger('click')
        expect(control.findComponent(LendingShareModal).props('modelValue')).toBe(true)
    })

    it('withdraws a product by removing its rule and a piece by holding it back', async () => {
        const granted = {...row('art', 4), share: {grant: 'GRANT' as const, scope: 'ALL_PARTNERS' as const, partnerIds: []}}
        const bulk = shallowMount(BulkActions, {
            props: {rows: [row('inventory', 1), row('item', 9)], partners: [], childrenOf: () => [granted, row('art', 5)]},
            global: {...global, renderStubDefaultSlot: true},
        })

        await bulk.findComponent(ErrorButton).vm.$emit('click')
        await flushPromises()
        expect(removeShare).toHaveBeenCalledWith('inventory', 1)
        expect(removeShare).toHaveBeenCalledWith('art', 4)
        expect(removeShare).not.toHaveBeenCalledWith('art', 5)
        expect(setShare).toHaveBeenCalledWith('item', 9, {grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []})
        expect(bulk.emitted('done')).toHaveLength(1)

        await bulk.findComponent(PrimaryButton).vm.$emit('click')
        await flushPromises()
        expect(setShare).toHaveBeenCalledWith('inventory', 1, {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []})
    })

    it('saves a text through the call of its level', async () => {
        for (const [target, call] of [['inventory', describeInventory], ['art', describeArt], ['item', describeItem]] as const) {
            const drawer = shallowMount(DescriptionDrawer, {props: {modelValue: true, row: row(target, 7)}, global: {...global, renderStubDefaultSlot: true}})
            await drawer.findAllComponents(PrimaryButton).at(-1)!.vm.$emit('click')
            await flushPromises()
            expect(call).toHaveBeenCalled()
            expect(drawer.emitted('changed')).toHaveLength(1)
        }
        expect(describeArt).toHaveBeenCalledWith(1, 7, 'alt')
    })
})
