/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {parseFieldConfig} from './profileFields'

/**
 * Every dynamic field reads its configuration through this. The settings arrive as an object in
 * both directions now, so all that is left to answer for is a field that carries none: answering
 * with an empty configuration rather than throwing is what the field renderers rely on.
 */
describe('parseFieldConfig', () => {
    it('passes an object through untouched', () => {
        const config = {required: true, options: ['S', 'M']}
        expect(parseFieldConfig(config)).toBe(config)
    })

    it('answers empty for nothing', () => {
        expect(parseFieldConfig(null)).toEqual({})
        expect(parseFieldConfig(undefined)).toEqual({})
    })
})
