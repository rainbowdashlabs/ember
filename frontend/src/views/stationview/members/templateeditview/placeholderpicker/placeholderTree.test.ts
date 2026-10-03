/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {PlaceholderCategory} from '@/api/generated/schema'
import {CATALOGUE} from './fixtures'
import {branchesAlong, offeredPlaceholders, placeholderTree, searchPlaceholders, type PlaceholderNode} from './placeholderTree'

const EVERYTHING = {legal: false, appointments: true}

function names(nodes: PlaceholderNode[]) {
    return nodes.map(node => `${node.kind}:${node.name}`)
}

function keys(placeholders: {key: string}[]) {
    return placeholders.map(placeholder => placeholder.key)
}

describe('the placeholders a place offers', () => {
    it('leaves the called name out of a legal template', () => {
        expect(keys(offeredPlaceholders(CATALOGUE, {...EVERYTHING, legal: true}))).not.toContain('member.calledName')
        expect(keys(offeredPlaceholders(CATALOGUE, EVERYTHING))).toContain('member.calledName')
    })

    it('leaves the appointment out where documents are for members alone', () => {
        expect(keys(offeredPlaceholders(CATALOGUE, {...EVERYTHING, appointments: false}))).not.toContain('event.name')
    })
})

describe('the path the picker walks', () => {
    const tree = placeholderTree(CATALOGUE)

    it('has one category per category of the catalogue, in its order', () => {
        expect(tree.map(category => category.category)).toEqual([
            PlaceholderCategory.MEMBER,
            PlaceholderCategory.PRONOUNS,
            PlaceholderCategory.GUARDIAN1,
            PlaceholderCategory.APPOINTMENT,
        ])
        expect(tree[0]?.name).toBe('Mitglied')
    })

    it('splits the member into the details and the profile, and the profile by its headings', () => {
        expect(names(tree[0]?.children ?? [])).toEqual(['branch:Stammdaten', 'branch:Profil'])
        const profile = branchesAlong(tree, ['Mitglied', 'Profil']).at(-1)
        expect(names(profile?.children ?? [])).toEqual(['leaf:Schule', 'branch:Medizinisches'])
        const medical = branchesAlong(tree, ['Mitglied', 'Profil', 'Medizinisches']).at(-1)
        expect(medical?.children).toEqual([{kind: 'leaf', name: 'Allergien', placeholder: CATALOGUE[3]}])
    })

    it('leads a possessive through its role and its place in the sentence', () => {
        const steps = ['Pronomen', 'Wessen (sein / ihr)', 'Am Satzanfang']
        const place = branchesAlong(tree, steps)

        expect(place.map(branch => branch.name)).toEqual(steps)
        expect(names(place[2]?.children ?? [])).toEqual(['leaf:Seine / Ihre / Vornamens'])
    })

    it('follows a trail only as far as it is still there', () => {
        expect(branchesAlong(tree, ['Mitglied', 'Weg', 'Profil']).map(branch => branch.name)).toEqual(['Mitglied'])
        expect(branchesAlong(tree, ['Nirgends'])).toEqual([])
        expect(branchesAlong(tree, [])).toEqual([])
    })
})

describe('the search of the picker', () => {
    it('finds by label, path and key, whatever the case, every word anywhere', () => {
        expect(keys(searchPlaceholders(CATALOGUE, 'allergien'))).toEqual(['profile.2', 'guardian1.profile.2'])
        expect(keys(searchPlaceholders(CATALOGUE, 'erziehungsberechtigte allergien'))).toEqual(['guardian1.profile.2'])
        expect(keys(searchPlaceholders(CATALOGUE, 'pronoun.subject'))).toEqual(['pronoun.subject'])
        expect(keys(searchPlaceholders(CATALOGUE, 'Satzanfang'))).toEqual(['pronoun.possessive.start.e'])
    })

    it('finds nothing for an empty search', () => {
        expect(searchPlaceholders(CATALOGUE, '   ')).toEqual([])
    })
})
