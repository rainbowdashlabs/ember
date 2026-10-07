/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {recentlyUsedFirst} from './recentlyUsedFirst'

const names = (templates: {name: string}[]) => templates.map(template => template.name)

describe('recentlyUsedFirst', () => {
    it('puts the template used last first and the ones never used after all used ones', () => {
        const ordered = recentlyUsedFirst([
            {name: 'Nie benutzt', lastUsedAt: null},
            {name: 'Gestern', lastUsedAt: '2026-10-02T09:00:00Z'},
            {name: 'Ohne Angabe'},
            {name: 'Heute', lastUsedAt: '2026-10-03T08:00:00Z'},
            {name: 'Letzten Monat', lastUsedAt: '2026-09-01T12:00:00Z'},
        ])

        expect(names(ordered)).toEqual(['Heute', 'Gestern', 'Letzten Monat', 'Nie benutzt', 'Ohne Angabe'])
    })

    it('orders templates used at the same moment, and those never used, by name', () => {
        const ordered = recentlyUsedFirst([
            {name: 'Zeugnis', lastUsedAt: null},
            {name: 'Übungsnachweis', lastUsedAt: '2026-10-03T08:00:00Z'},
            {name: 'Ausweis', lastUsedAt: null},
            {name: 'Bescheinigung', lastUsedAt: '2026-10-03T08:00:00Z'},
        ])

        expect(names(ordered)).toEqual(['Bescheinigung', 'Übungsnachweis', 'Ausweis', 'Zeugnis'])
    })

    it('leaves the list it was given as it was', () => {
        const given = [{name: 'B', lastUsedAt: null}, {name: 'A', lastUsedAt: '2026-10-03T08:00:00Z'}]

        recentlyUsedFirst(given)

        expect(names(given)).toEqual(['B', 'A'])
    })
})
