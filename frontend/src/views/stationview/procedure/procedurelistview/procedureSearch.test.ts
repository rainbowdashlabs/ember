/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {matchesSearch} from './procedureSearch'

const INTAKE = {name: 'Aufnahme Lena', description: 'Bis zum ersten Übungsabend'}

/** The search finds a procedure by its name or its description, whatever the case. */
describe('matchesSearch', () => {
    it('matches every procedure on an empty search', () => {
        expect(matchesSearch(INTAKE, '  ')).toBe(true)
    })

    it('finds by name and by description, ignoring case and the spaces around', () => {
        expect(matchesSearch(INTAKE, ' lena ')).toBe(true)
        expect(matchesSearch(INTAKE, 'ÜBUNG')).toBe(true)
        expect(matchesSearch(INTAKE, 'Einsatz')).toBe(false)
    })

    it('reads a procedure without a description by its name alone', () => {
        expect(matchesSearch({name: 'Aufnahme', description: null}, 'aufnahme')).toBe(true)
        expect(matchesSearch({name: 'Aufnahme', description: null}, 'abend')).toBe(false)
    })
})
