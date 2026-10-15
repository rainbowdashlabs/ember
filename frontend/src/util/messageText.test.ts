/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {ACCOUNT_SCOPE, mentionablesOf, mentions, segmentsOf} from './messageText'
import {conversationTitle} from './conversationTitle'

describe('segmentsOf', () => {
    it('cuts a text into plain text, links and mentions', () => {
        expect(segmentsOf('Hallo @[account/u1:Ben Muster], schau https://example.org/plan.')).toEqual([
            {kind: 'text', text: 'Hallo '},
            {kind: 'mention', uid: 'u1', name: 'Ben Muster'},
            {kind: 'text', text: ', schau '},
            {kind: 'link', text: 'https://example.org/plan', href: 'https://example.org/plan'},
            {kind: 'text', text: '.'},
        ])
    })

    it('leaves addresses that are not http or https as text', () => {
        expect(segmentsOf('javascript:alert(1)')).toEqual([{kind: 'text', text: 'javascript:alert(1)'}])
    })

    it('knows whom a text mentions', () => {
        expect(mentions('Hi @[account/u1:Ben]', 'u1')).toBe(true)
        expect(mentions('Hi @[account/u1:Ben]', 'u2')).toBe(false)
    })
})

describe('mentionablesOf', () => {
    it('offers the people of a conversation to the mention completion by account', () => {
        const [ben] = mentionablesOf([{uid: 'u1', firstName: 'Ben', lastName: 'Muster'}])

        expect(ben?.memberUid).toBe('u1')
        expect(ben?.stationUid).toBe(ACCOUNT_SCOPE)
        expect(ben?.name).toBe('Ben Muster')
    })
})

describe('conversationTitle', () => {
    const people = [
        {uid: 'u1', firstName: 'Ben', lastName: 'Muster'},
        {uid: 'u2', firstName: 'Cora', lastName: 'Beispiel'},
    ]

    it('calls a group by its name or by the first names of the others', () => {
        expect(conversationTitle({kind: 'GROUP', name: 'Jugend', others: people}, '?')).toBe('Jugend')
        expect(conversationTitle({kind: 'GROUP', name: null, others: people}, '?')).toBe('Ben, Cora')
    })

    it('calls a direct conversation by the other person and an empty one by the fallback', () => {
        expect(conversationTitle({kind: 'DIRECT', others: people.slice(0, 1)}, '?')).toBe('Ben Muster')
        expect(conversationTitle({kind: 'DIRECT', others: []}, 'Niemand')).toBe('Niemand')
    })
})
