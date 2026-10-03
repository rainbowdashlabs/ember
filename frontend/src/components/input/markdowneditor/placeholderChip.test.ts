/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {createMarkdownTurndown} from './markdownTurndown'
import {labelledText, placeholderContent, placeholderTokens} from './placeholderChip'

const labels = new Map([
    ['member.firstName', 'Vorname'],
    ['pronoun.subject', 'er / sie / Name'],
])

describe('placeholder chips', () => {
    it('turn every stored token into a chip with its label', () => {
        const html = placeholderTokens(labels).prepare('Hallo {{member.firstName}}, {{ pronoun.subject }} kommt.')

        expect(html).toBe('Hallo <span data-placeholder="member.firstName">Vorname</span>, '
            + '<span data-placeholder="pronoun.subject">er / sie / Name</span> kommt.')
    })

    it('show a key without a label as the key itself, escaped', () => {
        expect(placeholderTokens(new Map([['a.b', '<b>']])).prepare('{{a.b}} {{x.y}}'))
            .toBe('<span data-placeholder="a.b">&lt;b&gt;</span> <span data-placeholder="x.y">x.y</span>')
    })

    it('are written back as the token they stand for', () => {
        const turndown = createMarkdownTurndown()
        placeholderTokens(labels).extendTurndown(turndown)

        const markdown = turndown.turndown('<p>Hallo <span data-placeholder="member.firstName" class="placeholder-chip">Vorname</span>!</p>')

        expect(markdown).toBe('Hallo {{member.firstName}}!')
    })

    it('are inserted with their key and label', () => {
        expect(placeholderContent('member.firstName', 'Vorname'))
            .toEqual({type: 'placeholderChip', attrs: {key: 'member.firstName', label: 'Vorname'}})
    })

    it('read as their labels in a plain text, a key without one as itself', () => {
        expect(labelledText('{{member.firstName}}, {{ x.y }}', labels)).toBe('Vorname, x.y')
    })
})
