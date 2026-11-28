/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {createI18n} from 'vue-i18n'
import type {FlowResponse, MovementPurpose} from '@/api/generated/schema'
import MovementFlowPanel from './MovementFlowPanel.vue'
import FlowCard from '../flowview/FlowCard.vue'

const ownerAbove = vi.fn()
vi.mock('@/api', () => ({
    inventory: {ownerAbove: () => ownerAbove()},
    movements: {
        listFlows: async () => [
            {id: 1, name: 'Ausgabe', purpose: 'ISSUE'},
            {id: 2, name: 'Anfrage', purpose: 'REQUEST'},
        ] as FlowResponse[],
        listBindings: async () => [],
    },
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

async function panel() {
    const wrapper = shallowMount(MovementFlowPanel, {global: {plugins: [i18n]}})
    await flushPromises()
    return wrapper
}

function shownChains(wrapper: Awaited<ReturnType<typeof panel>>): MovementPurpose[] {
    return wrapper.findAllComponents(FlowCard).map(card => (card.props('flow') as FlowResponse).purpose)
}

/** A request goes to an association in Ember, so its chains are only kept where there is one. */
describe('MovementFlowPanel', () => {
    beforeEach(() => ownerAbove.mockReset())

    it('keeps the request chains where an association keeps its gear in Ember', async () => {
        ownerAbove.mockResolvedValue('Kreisfeuerwehrverband')

        expect(shownChains(await panel())).toEqual(['ISSUE', 'REQUEST'])
    })

    it('leaves the request chains out where nobody can be asked', async () => {
        ownerAbove.mockResolvedValue(null)

        expect(shownChains(await panel())).toEqual(['ISSUE'])
    })
})
