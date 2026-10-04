/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {PRONOUN_OFFER} from './fixtures'
import {choiceOf, pronounsFor} from './genderPronouns'

const {presets} = PRONOUN_OFFER

describe('gender pronouns', () => {
    it('key the predefined sets by language code', () => {
        expect(presets.MALE).toEqual({
            de: {subject: 'er', object: 'ihn', dative: 'ihm', possessive: 'sein'},
            en: {subject: 'he', object: 'him', dative: null, possessive: 'his'},
        })
    })

    it('read the predefined sets back as what they are', () => {
        expect(choiceOf(presets.MALE, presets)).toBe('MALE')
        expect(choiceOf(structuredClone(presets.FEMALE), presets)).toBe('FEMALE')
        expect(choiceOf(undefined, presets)).toBe('NAME')
        expect(choiceOf({de: {subject: ' ', object: null, dative: null, possessive: null}}, presets)).toBe('NAME')
    })

    it('read changed words as pronouns of their own', () => {
        const own = {...structuredClone(presets.MALE), de: {subject: 'xier', object: 'xien', dative: 'xiem', possessive: 'xies'}}

        expect(choiceOf(own, presets)).toBe('OWN')
    })

    it('start words of their own from what the answer had', () => {
        expect(pronounsFor('OWN', presets.FEMALE, presets)).toEqual(presets.FEMALE)
        expect(pronounsFor('OWN', undefined, presets)).toEqual({})
        expect(pronounsFor('NAME', presets.FEMALE, presets)).toBeUndefined()
        expect(pronounsFor('MALE', undefined, presets)).not.toBe(presets.MALE)
    })
})
