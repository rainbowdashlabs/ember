/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {isOfferableLink} from './completionLink'

/** The editor takes the same links after sending as the server does, and nothing else. */
describe('isOfferableLink', () => {
    it('takes no link, a web address and an address on this site', () => {
        expect(isOfferableLink('')).toBe(true)
        expect(isOfferableLink('  ')).toBe(true)
        expect(isOfferableLink('https://example.org')).toBe(true)
        expect(isOfferableLink('HTTP://example.org')).toBe(true)
        expect(isOfferableLink(' /public/fw/events/1 ')).toBe(true)
    })

    it('refuses anything else, including a half-typed address', () => {
        expect(isOfferableLink('htt')).toBe(false)
        expect(isOfferableLink('example.org')).toBe(false)
        expect(isOfferableLink('//example.org')).toBe(false)
        expect(isOfferableLink('javascript:alert(1)')).toBe(false)
    })
})
