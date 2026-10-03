/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {initialSearchTerm, searchDiscovery, searchIndex} from './discoverySearch'
import type {DiscoveryEntry} from '@/api/generated/schema'
import {createDiscoveryEntry} from '@/test/mocks/discovery'

/**
 * The search on the public discovery page reaches the stations of other instances as it reaches
 * this instance's own.
 *
 * @vitest-environment happy-dom
 */
describe('searchDiscovery', () => {
    function entry(name: string, overrides: Partial<DiscoveryEntry> = {}): DiscoveryEntry {
        return createDiscoveryEntry({stationUid: name, name, publicSlug: null, ...overrides})
    }

    const local = entry('Wache Hier', {city: 'Südstadt'})
    const remote = entry('Wache Dort', {
        city: 'Nordstadt',
        clusterName: 'Kreis Nord',
        instanceHost: 'feuer.example',
        description: 'Jugendfeuerwehr am Hafen',
    })

    const index = searchIndex([local, remote])

    it('keeps every station for a blank term', () => {
        expect(searchDiscovery(index, '  ')).toEqual([local, remote])
    })

    it('finds a remote station by name, place, association, description and instance', () => {
        for (const term of ['dort', 'NORDSTADT', 'kreis nord', 'hafen', 'feuer.example']) {
            expect(searchDiscovery(index, term)).toEqual([remote])
        }
    })

    it('finds a local station the same way', () => {
        expect(searchDiscovery(index, 'süd')).toEqual([local])
    })

    it('puts the text of each station together once, lowercased', () => {
        expect(index.map(({entry}) => entry)).toEqual([local, remote])
        expect(index[1]!.text).toBe('wache dort\njugendfeuerwehr am hafen\nnordstadt\nkreis nord\nfeuer.example')
    })

    it('reads the term a link hands over', () => {
        expect(initialSearchTerm('nord')).toBe('nord')
        expect(initialSearchTerm(['nord', 'süd'])).toBe('nord')
        expect(initialSearchTerm(undefined)).toBe('')
        expect(initialSearchTerm(null)).toBe('')
    })
})
