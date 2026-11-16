/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import FederationCard from './FederationCard.vue'

function card(compact: boolean) {
    return mount(FederationCard, {
        props: {partners: [], canFederate: true, shared: false, scope: 'ALL_PARTNERS', partnerIds: [], places: {}, compact},
        global: {stubs: {'font-awesome-icon': true}},
    })
}

/**
 * At the end of the appointment form the switch names what it does, so sharing needs no heading of its
 * own and keeps its explanation behind an icon.
 */
describe('FederationCard', () => {
    it('shows only the switch and a hint icon in compact form', () => {
        const wrapper = card(true)

        expect(wrapper.find('[data-testid="hint-icon"]').exists()).toBe(true)
        expect(wrapper.find('h3, h2').exists()).toBe(false)
        expect(wrapper.findAll('p').some(paragraph => paragraph.text().includes('Partnerwachen'))).toBe(false)
    })

    it('keeps heading and explanation elsewhere', () => {
        const full = card(false)
        expect(full.find('[data-testid="hint-icon"]').exists()).toBe(false)
        expect(full.find('h3').exists()).toBe(true)
    })
})
