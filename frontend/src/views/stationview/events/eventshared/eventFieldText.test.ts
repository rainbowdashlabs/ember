/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {eventFieldMemberIds, eventFieldText} from './eventFieldText'

const LABELS = {yes: 'Ja', no: 'Nein'}
const NAMES = new Map([
    [42, 'Anna Berger'],
    [43, 'Bert Cordes'],
])

/**
 * A field's value is text whatever its type says, so anything reading one out of its own screen
 * has to resolve it first. These are the two types that do not read as themselves.
 */
describe('eventFieldText', () => {
    it('reads a yes/no field as a word', () => {
        expect(eventFieldText({fieldType: 'BOOLEAN', value: 'true'}, NAMES, LABELS)).toBe('Ja')
        expect(eventFieldText({fieldType: 'BOOLEAN', value: 'false'}, NAMES, LABELS)).toBe('Nein')
    })

    it('names the person a member field holds rather than their number', () => {
        expect(eventFieldText({fieldType: 'MEMBER', value: '42'}, NAMES, LABELS)).toBe('Anna Berger')
        expect(eventFieldText({fieldType: 'MEMBER_LIST', value: '[42,43]'}, NAMES, LABELS)).toBe('Anna Berger, Bert Cordes')
    })

    it('falls back to the number where the station no longer has that member', () => {
        expect(eventFieldText({fieldType: 'MEMBER', value: '99'}, NAMES, LABELS)).toBe('#99')
    })

    it('hands every other type its stored text', () => {
        expect(eventFieldText({fieldType: 'STRING', value: 'Gerätehaus'}, NAMES, LABELS)).toBe('Gerätehaus')
        expect(eventFieldText({fieldType: 'STRING', value: '  '}, NAMES, LABELS)).toBe('')
        expect(eventFieldText({fieldType: undefined, value: undefined}, NAMES, LABELS)).toBe('')
    })
})

describe('eventFieldMemberIds', () => {
    it('reads both shapes a member field is stored in', () => {
        expect(eventFieldMemberIds('42')).toEqual([42])
        expect(eventFieldMemberIds('[42,43]')).toEqual([42, 43])
        expect(eventFieldMemberIds('')).toEqual([])
        expect(eventFieldMemberIds('Anna')).toEqual([])
    })
})
