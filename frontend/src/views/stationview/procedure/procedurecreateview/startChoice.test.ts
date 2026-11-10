/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {startsWithChoice} from './startChoice'

/** Only a fresh create page asks how the procedure starts. */
describe('startsWithChoice', () => {
    it('asks on a fresh create page', () => {
        expect(startsWithChoice(false, null, {})).toBe(true)
    })

    it('opens the form for a blank start', () => {
        expect(startsWithChoice(false, null, {blank: '1'})).toBe(false)
    })

    it('opens the form for a template named in the address', () => {
        expect(startsWithChoice(false, 4, {template: '4'})).toBe(false)
    })

    it('opens the form when editing', () => {
        expect(startsWithChoice(true, null, {})).toBe(false)
    })
})
