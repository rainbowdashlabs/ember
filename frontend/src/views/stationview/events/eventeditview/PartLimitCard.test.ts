/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import PartLimitCard from './PartLimitCard.vue'

function card(inherited: number | null, modelValue?: number) {
    return mount(PartLimitCard, {props: {inherited, modelValue}})
}

/** Left empty, a part takes its event group's places, which the box shows and the hint names. */
describe('PartLimitCard', () => {
    it('shows the event group\'s places as placeholder and in the hint', () => {
        const shown = card(10)

        expect(shown.get('input').attributes('placeholder')).toBe('10')
        expect(shown.text()).toContain('Plätze der Terminreihe: 10')
    })

    it('says the places are unlimited where the event group limits none', () => {
        const shown = card(null)

        expect(shown.get('input').attributes('placeholder')).toBe('Leer = unbegrenzt')
        expect(shown.text()).toContain('Plätze der Terminreihe: unbegrenzt')
    })

    it('hands back a number of its own, and nothing for an empty box', async () => {
        const shown = card(10, 20)

        await shown.get('input').setValue('25')
        expect(shown.emitted('update:modelValue')!.at(-1)).toEqual([25])
        await shown.get('input').setValue('')
        expect(shown.emitted('update:modelValue')!.at(-1)).toEqual([undefined])
    })
})
