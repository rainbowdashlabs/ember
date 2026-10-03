/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {choiceOf, PRESETS, pronounsFor, storedPronouns} from './genderPronouns'

describe('gender pronouns', () => {
    it('read the predefined sets back as what they are', () => {
        expect(choiceOf(PRESETS.MALE)).toBe('MALE')
        expect(choiceOf(structuredClone(PRESETS.FEMALE))).toBe('FEMALE')
        expect(choiceOf(undefined)).toBe('NAME')
        expect(choiceOf({de: {subject: ' ', object: null, dative: null, possessive: null}})).toBe('NAME')
    })

    it('read changed words as pronouns of their own', () => {
        const own = {...structuredClone(PRESETS.MALE), de: {subject: 'xier', object: 'xien', dative: 'xiem', possessive: 'xies'}}

        expect(choiceOf(own)).toBe('OWN')
    })

    it('start words of their own from what the answer had', () => {
        expect(pronounsFor('OWN', PRESETS.FEMALE)).toEqual(PRESETS.FEMALE)
        expect(pronounsFor('OWN', undefined)).toEqual({})
        expect(pronounsFor('NAME', PRESETS.FEMALE)).toBeUndefined()
        expect(pronounsFor('MALE', undefined)).not.toBe(PRESETS.MALE)
    })

    it('keep only the answers still offered and none that use the name', () => {
        const stored = storedPronouns(
            {männlich: PRESETS.MALE, weiblich: PRESETS.FEMALE, divers: {}, alt: PRESETS.MALE},
            ['männlich', 'weiblich', 'divers'],
        )

        expect(Object.keys(stored)).toEqual(['männlich', 'weiblich'])
    })
})
