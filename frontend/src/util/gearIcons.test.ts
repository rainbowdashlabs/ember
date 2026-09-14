/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {findIconDefinition, library, type IconDefinition} from '@fortawesome/fontawesome-svg-core'
import * as solid from '@fortawesome/free-solid-svg-icons'
import de from '@/i18n/de-DE'
import {GEAR_ICONS, gearIcon} from './gearIcons'

type Translations = Record<string, unknown>

function translation(key: string): unknown {
    return key.split('.').reduce<unknown>((node, part) => {
        if (node === null || typeof node !== 'object') return undefined
        return (node as Translations)[part]
    }, de as unknown)
}

describe('the catalogue of gear pictures', () => {
    /**
     * The one test that earns its keep: a picture nobody registered renders as an empty square at a
     * station, and nothing in a build notices. This does.
     */
    it('offers only pictures the icon library knows', () => {
        const definitions = Object.values(solid).filter(
            (entry): entry is IconDefinition => typeof entry === 'object' && entry !== null && 'iconName' in entry,
        )
        library.add(...definitions)

        const missing = GEAR_ICONS.filter(
            icon => !findIconDefinition({prefix: 'fas', iconName: icon.name as never}),
        )

        expect(missing.map(icon => icon.name)).toEqual([])
    })

    it('names every picture in the translations rather than in the code', () => {
        const unnamed = GEAR_ICONS.filter(icon => typeof translation(icon.labelKey) !== 'string')

        expect(unnamed.map(icon => icon.labelKey)).toEqual([])
    })

    it('offers each picture once', () => {
        const names = GEAR_ICONS.map(icon => icon.name)

        expect(new Set(names).size).toBe(names.length)
    })

    it('finds a picture by the name that is stored, and tolerates one it no longer offers', () => {
        expect(gearIcon('helmet-safety')?.group).toBe('protection')
        expect(gearIcon('a-name-retired-years-ago')).toBeNull()
        expect(gearIcon(null)).toBeNull()
        expect(gearIcon(undefined)).toBeNull()
    })
})
