/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {parseFieldConfig} from './profileFields'

/**
 * Every dynamic field reads its configuration through this, and it is handed whatever the server
 * stored - including nothing at all. Answering with an empty configuration rather than throwing is
 * the behaviour the field renderers rely on.
 */
describe('parseFieldConfig', () => {
    it('parses a JSON string', () => {
        expect(parseFieldConfig('{"required":true}')).toEqual({required: true})
    })

    it('passes an object through untouched', () => {
        const config = {required: true, options: ['S', 'M']}
        expect(parseFieldConfig(config)).toBe(config)
    })

    it('answers empty for nothing', () => {
        expect(parseFieldConfig(null)).toEqual({})
        expect(parseFieldConfig(undefined)).toEqual({})
        expect(parseFieldConfig('')).toEqual({})
    })

    it('answers empty for something that is not JSON', () => {
        expect(parseFieldConfig('not-json')).toEqual({})
    })
})
