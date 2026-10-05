/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import Spinner from '@/components/feedback/Spinner.vue'
import ProtocolSectionNode from './protocoldetailview/ProtocolSectionNode.vue'
import ProtocolSectionModal from './protocoldetailview/ProtocolSectionModal.vue'
import ProtocolDetailView from './ProtocolDetailView.vue'

const getProtocol = vi.fn()
const createSection = vi.fn()
const reorderSections = vi.fn()
const moveSection = vi.fn()

vi.mock('@/api', () => ({
    protocol: {
        getProtocol: (...args: unknown[]) => getProtocol(...args),
        createSection: (...args: unknown[]) => createSection(...args),
        reorderSections: (...args: unknown[]) => reorderSections(...args),
        moveSection: (...args: unknown[]) => moveSection(...args),
    },
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => true, loaded: ref(true)}),
}))

vi.mock('@/api/storage', () => ({
    getItem: () => '00000000-0000-0000-0000-000000000001',
}))

function protocolWith(sectionNames: string[]) {
    return {
        protocol: {
            id: 1,
            name: 'Leistungsabzeichen',
            description: '',
            passThreshold: null,
            stationId: '00000000-0000-0000-0000-000000000001',
            createdAt: '2026-10-01T08:00:00Z',
            updatedAt: '2026-10-01T08:00:00Z',
        },
        sections: sectionNames.map((name, index) => ({
            id: index + 1,
            name,
            description: '',
            maxPoints: null,
            parentId: null,
            passThreshold: null,
            position: index,
        })),
        items: [],
    }
}

/** Adding to a protocol reads it back without taking it off the page in between. */
describe('ProtocolDetailView', () => {
    beforeEach(() => {
        getProtocol.mockReset()
        createSection.mockReset()
        reorderSections.mockReset()
        moveSection.mockReset()
    })

    function topLevel(view: VueWrapper) {
        return view.findAllComponents(ProtocolSectionNode).filter(node => node.props('depth') === 0)
    }

    function sectionNames(view: VueWrapper): string[] {
        return topLevel(view).map(node => node.props('section').name)
    }

    function nestedThreeDeep() {
        const data = protocolWith(['Knoten', 'Leinen'])
        data.sections.push(
            {...data.sections[1]!, id: 3, name: 'Palstek', parentId: 2, position: 0} as never,
            {...data.sections[1]!, id: 4, name: 'Doppelt', parentId: 3, position: 0} as never,
        )
        return data
    }

    it('draws a section three levels down', async () => {
        getProtocol.mockResolvedValueOnce(nestedThreeDeep())
        const view = await mountSuspended(ProtocolDetailView, {route: '/station/protocols/1'})
        await flushPromises()

        const depths = view.findAllComponents(ProtocolSectionNode).map(node => [node.props('section').name, node.props('depth')])
        expect(depths).toEqual([['Knoten', 0], ['Leinen', 0], ['Palstek', 1], ['Doppelt', 2]])
    })

    it('cuts a section and pastes it under another, but never under itself', async () => {
        getProtocol.mockResolvedValue(nestedThreeDeep())
        moveSection.mockResolvedValue(undefined)
        const view = await mountSuspended(ProtocolDetailView, {route: '/station/protocols/1'})
        await flushPromises()

        const leinen = topLevel(view)[1]!
        await leinen.find('[aria-label="Ausschneiden"]').trigger('click')
        await flushPromises()

        const pasteTargets = view.findAll('button[aria-label^="In "]').map(button => button.attributes('aria-label'))
        expect(pasteTargets).toEqual(['In „Knoten“ einfügen'])

        await view.find('button[aria-label="In „Knoten“ einfügen"]').trigger('click')
        await flushPromises()
        expect(moveSection).toHaveBeenCalledWith(2, 1)
    })

    it('moves a section on screen and saves the whole level without reading the protocol back', async () => {
        getProtocol.mockResolvedValueOnce(protocolWith(['Knoten', 'Leinen', 'Funk']))
        reorderSections.mockResolvedValue(undefined)
        const view = await mountSuspended(ProtocolDetailView, {route: '/station/protocols/1'})
        await flushPromises()

        await view.findAll('[data-testid="move-down"]')[0]!.trigger('click')
        await flushPromises()

        expect(sectionNames(view)).toEqual(['Leinen', 'Knoten', 'Funk'])
        expect(reorderSections).toHaveBeenCalledWith(1, [2, 1, 3])
        expect(getProtocol).toHaveBeenCalledTimes(1)
    })

    it('reads the protocol back when a move is refused', async () => {
        getProtocol.mockResolvedValueOnce(protocolWith(['Knoten', 'Leinen'])).mockResolvedValueOnce(protocolWith(['Knoten', 'Leinen']))
        reorderSections.mockRejectedValue(new Error('refused'))
        const view = await mountSuspended(ProtocolDetailView, {route: '/station/protocols/1'})
        await flushPromises()

        await view.findAll('[data-testid="move-down"]')[0]!.trigger('click')
        await flushPromises()

        expect(getProtocol).toHaveBeenCalledTimes(2)
        expect(sectionNames(view)).toEqual(['Knoten', 'Leinen'])
    })

    it('keeps the protocol on screen while a new section is read back', async () => {
        getProtocol.mockResolvedValueOnce(protocolWith(['Knoten']))
        let answer: (value: unknown) => void = () => {}
        getProtocol.mockReturnValueOnce(new Promise(resolve => { answer = resolve }))
        createSection.mockResolvedValue({})
        const view = await mountSuspended(ProtocolDetailView, {route: '/station/protocols/1'})
        await flushPromises()

        const modal = view.findComponent(ProtocolSectionModal)
        modal.vm.$emit('update:name', 'Leinen')
        modal.vm.$emit('submit')
        await flushPromises()

        expect(getProtocol).toHaveBeenCalledTimes(2)
        expect(view.findComponent(Spinner).exists()).toBe(false)
        expect(topLevel(view)).toHaveLength(1)

        answer(protocolWith(['Knoten', 'Leinen']))
        await flushPromises()
        expect(topLevel(view)).toHaveLength(2)
    })
})
