/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {findIconDefinition, library, type IconDefinition} from '@fortawesome/fontawesome-svg-core'
import * as solid from '@fortawesome/free-solid-svg-icons'
import * as phosphor from '@phosphor-icons/vue'
import de from '@/i18n/de-DE'
import {GEAR_ICONS, gearIcon} from './gearIcons'
import {iconSetOf, phosphorComponentOf} from './iconName'

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
    it('offers only FontAwesome pictures the icon library knows', () => {
        const definitions = Object.values(solid).filter(
            (entry): entry is IconDefinition => typeof entry === 'object' && entry !== null && 'iconName' in entry,
        )
        library.add(...definitions)

        const missing = GEAR_ICONS.filter(icon => iconSetOf(icon.name).set === 'fas').filter(
            icon => !findIconDefinition({prefix: 'fas', iconName: icon.name as never}),
        )

        expect(missing.map(icon => icon.name)).toEqual([])
    })

    /**
     * The same guard for the second set, and with it the round trip that only bites there. The
     * naming is the app's own, so a picture whose capitals do not survive the journey (PhTShirt
     * through t-shirt and back) is caught here rather than drawn as nothing.
     */
    it('offers only Phosphor pictures the package exports, under names that survive the round trip', () => {
        const missing = GEAR_ICONS.filter(icon => iconSetOf(icon.name).set === 'ph').filter(
            icon => !(phosphorComponentOf(iconSetOf(icon.name).name) in phosphor),
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
