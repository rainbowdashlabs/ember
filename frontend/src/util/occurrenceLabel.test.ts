/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {occurrenceLabel} from './occurrenceLabel'

describe('occurrenceLabel', () => {
    it('writes the day with both clocks', () => {
        expect(occurrenceLabel('2026-10-13', '18:00', '20:00', 'bis')).toBe('Dienstag, 13.10.2026, 18:00 bis 20:00')
    })

    it('writes the day with the start alone where there is no end', () => {
        expect(occurrenceLabel('2026-10-13', '18:00', null, 'bis')).toBe('Dienstag, 13.10.2026, 18:00')
    })

    it('writes only the day where there is no clock at all', () => {
        expect(occurrenceLabel('2026-10-13', null, null, 'bis')).toBe('Dienstag, 13.10.2026')
    })
})
