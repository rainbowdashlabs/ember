/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeAll, describe, expect, it} from 'vitest'
import {deviceLabel} from './pushSupport'
import {setTranslator} from './translatorState'

/** A device is listed under a name its owner recognises, guessed from what the browser says about itself. */
describe('deviceLabel', () => {
    beforeAll(() => {
        setTranslator((key, params) => key === 'pushDevices.deviceOn' ? `${params?.browser} auf ${params?.system}` : key, () => 'de-DE')
    })

    it('names browser and system', () => {
        expect(deviceLabel('Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0')).toBe('Firefox auf Android')
        expect(deviceLabel('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 Edg/131.0'))
            .toBe('Edge auf Windows')
        expect(deviceLabel('Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1'))
            .toBe('Safari auf iOS')
    })

    it('falls back to a plain browser where nothing fits', () => {
        expect(deviceLabel('curl/8.0')).toBe('Browser')
    })
})
