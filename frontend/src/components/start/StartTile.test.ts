/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import StartTile from './StartTile.vue'

/** A start tile has one control, which starts it once however it is reached. */
describe('StartTile', () => {
    it('starts once when the button is pressed', async () => {
        const view = await mountSuspended(StartTile, {props: {title: 'Leer', buttonLabel: 'Anlegen', hint: 'Von Hand'}})

        await view.find('button').trigger('click')

        expect(view.emitted('start')).toHaveLength(1)
        expect(view.findAll('button')).toHaveLength(1)
        expect(view.text()).toContain('Von Hand')
    })

    it('starts once on a click beside the button', async () => {
        const view = await mountSuspended(StartTile, {props: {title: 'Leer', buttonLabel: 'Anlegen'}})

        await view.find('span').trigger('click')

        expect(view.emitted('start')).toHaveLength(1)
    })
})
