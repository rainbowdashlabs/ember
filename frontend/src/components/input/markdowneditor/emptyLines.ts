/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** An empty line as stored: a paragraph holding nothing but a non-breaking space. */
const EMPTY_LINE = String.fromCodePoint(0xa0)

const STORED_EMPTY_LINE = new RegExp(`<p>(?:${EMPTY_LINE}|&nbsp;)</p>`, 'g')
const OUTER_EMPTY_LINES = new RegExp(`^(?:${EMPTY_LINE}(?:\\n\\n|$))+|(?:(?:^|\\n\\n)${EMPTY_LINE})+$`, 'g')

/**
 * What an element without words is written as: an empty paragraph as the line it is stored as, so lines
 * left empty on purpose survive, and every other block as a paragraph break, as turndown does by default.
 * Turndown hands every element it counts as blank to this alone, before any rule is asked.
 *
 * <p>Markdown folds any number of blank lines into one paragraph break, and the editor's empty
 * paragraphs vanished with them on every save. A paragraph holding a non-breaking space is one that
 * Markdown, the page renderer and the printed letter all keep, and it looks empty in each.
 */
export function blankReplacement(_content: string, node: {nodeName: string; isBlock?: boolean}): string {
    if (node.nodeName === 'P') return `\n\n${EMPTY_LINE}\n\n`
    return node.isBlock ? '\n\n' : ''
}

/**
 * The stored markdown without empty lines at its very start and end: the editor keeps an empty
 * paragraph after the last line it was given, and nobody means that one.
 */
export function withoutOuterEmptyLines(markdown: string): string {
    return markdown.replace(OUTER_EMPTY_LINES, '')
}

/**
 * The rendered HTML with every stored empty line an empty paragraph again, for the editor to open, so
 * the cursor lands in an empty line rather than beside an invisible space.
 */
export function openEmptyLines(html: string): string {
    return html.replace(STORED_EMPTY_LINE, '<p></p>')
}
