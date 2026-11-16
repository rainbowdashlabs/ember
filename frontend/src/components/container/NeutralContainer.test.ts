/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent, h} from 'vue'
import {describe, expect, it} from 'vitest'
import NeutralContainer from './NeutralContainer.vue'

const BOX = 'rounded-theme'

const Card = defineComponent({
    setup: (_, {slots}) => () => h(NeutralContainer, {'data-testid': 'card'}, slots),
})

/**
 * A form made of several cards reads as one when it sits in a sheet: the cards directly inside lose
 * their box and become its sections, while a card inside one of them still frames its content.
 */
describe('NeutralContainer as a sheet', () => {
    it('draws a box on its own', () => {
        expect(mount(Card).find('[data-testid="card"]').classes()).toContain(BOX)
    })

    it('turns the cards directly inside into sections without a box', () => {
        const sheet = mount(NeutralContainer, {props: {sheet: true}, slots: {default: () => h(Card)}})

        expect(sheet.classes()).toContain(BOX)
        expect(sheet.find('[data-testid="card"]').classes()).not.toContain(BOX)
    })

    it('keeps the box of a card inside a section', () => {
        const sheet = mount(NeutralContainer, {
            props: {sheet: true},
            slots: {default: () => h(Card, null, {default: () => h(NeutralContainer, {'data-testid': 'inner'})})},
        })

        expect(sheet.find('[data-testid="inner"]').classes()).toContain(BOX)
    })
})
