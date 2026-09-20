/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {createMarkdownTurndown} from './markdownTurndown'

/**
 * What survives being written in the editor and stored as markdown.
 *
 * <p>A table did not. Turndown knows nothing about tables, so it threw the tags away and kept the
 * words, and a table written into a page came back as a run of plain text with its rows gone. That
 * is content loss rather than a formatting slip, which is why it is held here.
 */

const turndown = createMarkdownTurndown()

describe('the editor writing markdown', () => {
    it('writes a table as a table', () => {
        const html = '<table><thead><tr><th>Name</th><th>Rolle</th></tr></thead>'
            + '<tbody><tr><td>Anna</td><td>Betreuerin</td></tr>'
            + '<tr><td>Ben</td><td>Jugendlicher</td></tr></tbody></table>'

        expect(turndown.turndown(html).trim()).toBe(
            '| Name | Rolle |\n| --- | --- |\n| Anna | Betreuerin |\n| Ben | Jugendlicher |')
    })

    /** Markdown has no table without a header, so one is invented rather than losing the rows. */
    it('gives a table without a header an empty one so its rows survive', () => {
        const html = '<table><tbody><tr><td>Anna</td><td>Ben</td></tr></tbody></table>'

        expect(turndown.turndown(html).trim()).toBe('|  |  |\n| --- | --- |\n| Anna | Ben |')
    })

    /** A pipe inside a cell would otherwise end the cell early and shift every column after it. */
    it('keeps a pipe inside a cell from splitting the row', () => {
        const html = '<table><tbody><tr><td>a | b</td><td>c</td></tr></tbody></table>'

        expect(turndown.turndown(html)).toContain('| a \\| b | c |')
    })

    /** A cell written over two lines is still one cell. */
    it('keeps a line break inside a cell from ending the row', () => {
        const html = '<table><tbody><tr><td><p>eins</p><p>zwei</p></td><td>drei</td></tr></tbody></table>'

        expect(turndown.turndown(html)).toContain('| eins zwei | drei |')
    })

    it('keeps the markup inside a cell', () => {
        const html = '<table><tbody><tr><td><strong>Anna</strong></td><td><em>ja</em></td></tr></tbody></table>'

        expect(turndown.turndown(html)).toContain('| **Anna** | _ja_ |')
    })

    /** A short row would otherwise leave the table ragged and the columns misread. */
    it('pads a short row out to the width of the table', () => {
        const html = '<table><thead><tr><th>A</th><th>B</th><th>C</th></tr></thead>'
            + '<tbody><tr><td>1</td></tr></tbody></table>'

        expect(turndown.turndown(html)).toContain('| 1 |  |  |')
    })

    it('still writes everything else the way it did', () => {
        expect(turndown.turndown('<p><strong>fett</strong></p>')).toBe('**fett**')
        expect(turndown.turndown('<del>weg</del>')).toBe('~~weg~~')
    })
})
