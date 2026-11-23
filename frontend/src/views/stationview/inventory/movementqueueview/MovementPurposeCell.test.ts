/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import MovementPurposeCell from './MovementPurposeCell.vue'
import {movements} from '@/views/helpcenter/stationview/inventory/movementhelp/fixtures'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}, en: {}},
})

/** The badges a row of the queue carries for what kind of movement it is. */
describe('MovementPurposeCell', () => {
    it('marks a swap that waits for an order, and no other', () => {
        const ordered = movements.find(movement => movement.orderId != null)!
        const plain = movements.find(movement => movement.orderId == null)!

        const marked = mount(MovementPurposeCell, {global: {plugins: [i18n]}, props: {movement: ordered}})
        const unmarked = mount(MovementPurposeCell, {global: {plugins: [i18n]}, props: {movement: plain}})

        expect(marked.find('[data-testid="movement-ordered"]').exists()).toBe(true)
        expect(unmarked.find('[data-testid="movement-ordered"]').exists()).toBe(false)
    })
})
