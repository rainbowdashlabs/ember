/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import NumberMatchPicker from './NumberMatchPicker.vue'

/**
 * The step that makes it safe for the code to ride in the QR. What matters is that every choice the
 * server offered is on the screen, in the order it offered them, and that the screen never says
 * which one is right: somebody who was sent a picture of a QR has to be unable to answer.
 */
function mountPicker(choices: number[], busy = false) {
    return mount(NumberMatchPicker, {
        props: {choices, busy},
        global: {mocks: {$t: (key: string) => key}},
    })
}

describe('NumberMatchPicker', () => {
    it('offers every number the server sent, in the order it sent them', () => {
        const picker = mountPicker([47, 12, 88, 31, 65, 23])

        const labels = picker.findAll('button').map(button => button.text())

        expect(labels).toEqual(['47', '12', '88', '31', '65', '23'])
    })

    it('says which number was picked and nothing else', async () => {
        const picker = mountPicker([47, 12, 88, 31, 65, 23])

        await picker.get('[data-testid="number-choice-88"]').trigger('click')

        expect(picker.emitted('pick')).toEqual([[88]])
    })

    /**
     * Nothing in the markup may betray the answer. A class, an order or an attribute that singled the
     * right number out would hand the whole defence to anybody reading the page source.
     */
    it('marks no choice out from the others', () => {
        const picker = mountPicker([47, 12, 88, 31, 65, 23])

        const buttons = picker.findAll('button')
        const classes = new Set(buttons.map(button => button.attributes('class')))

        expect(classes.size, 'every choice is dressed the same').toBe(1)
    })

    it('takes no answer while an answer is already on its way', async () => {
        const picker = mountPicker([47, 12, 88, 31, 65, 23], true)

        await picker.get('[data-testid="number-choice-47"]').trigger('click')

        expect(picker.emitted('pick')).toBeUndefined()
    })
})
