/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {appointmentLabel, occurrenceLabel} from './occurrenceLabel'

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

describe('appointmentLabel', () => {
    it('writes the day with the start and the end', () => {
        expect(appointmentLabel('2026-10-13T16:00:00Z', '2026-10-13T18:00:00Z', 'bis', 'Europe/Berlin'))
            .toBe('Dienstag, 13.10.2026, 18:00 bis 20:00')
    })

    it('writes the start alone where there is no end', () => {
        expect(appointmentLabel('2026-10-13T16:00:00Z', null, 'bis', 'Europe/Berlin')).toBe('Dienstag, 13.10.2026, 18:00')
    })

    it('names both days where it ends on a later one', () => {
        expect(appointmentLabel('2026-10-13T16:00:00Z', '2026-10-15T14:00:00Z', 'bis', 'Europe/Berlin'))
            .toBe('Dienstag, 13.10.2026 18:00 – Donnerstag, 15.10.2026 16:00')
    })
})
