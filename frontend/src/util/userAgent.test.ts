/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {browserOf, systemOf} from './userAgent'

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1'
const ANDROID = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36'
const EDGE = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 Edg/131.0'
const MAC = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15'

/**
 * A browser's description names more than one system and browser at once; the phones and the more particular
 * browsers have to win over the ones they claim to be like.
 *
 * @vitest-environment happy-dom
 */
describe('userAgent', () => {
    it('reads phones before the desktop systems they name', () => {
        expect(systemOf(IPHONE)).toBe('ios')
        expect(systemOf(ANDROID)).toBe('android')
        expect(systemOf(MAC)).toBe('mac')
        expect(systemOf(EDGE)).toBe('windows')
        expect(systemOf(null)).toBe('unknown')
    })

    it('reads the particular browsers before the ones they claim to be', () => {
        expect(browserOf(EDGE)).toBe('edge')
        expect(browserOf(ANDROID)).toBe('chrome')
        expect(browserOf(IPHONE)).toBe('safari')
        expect(browserOf('curl/8.0')).toBe('unknown')
        expect(browserOf('Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0 Mobile/15E148 Safari/604.1'))
            .toBe('chrome')
        expect(browserOf('Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/132.0 Mobile/15E148 Safari/605.1.15'))
            .toBe('firefox')
        expect(browserOf('Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/131.0 Mobile/15E148 Safari/605.1.15'))
            .toBe('edge')
    })
})
