/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Editor, Extension, Mark, mergeAttributes} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import {applyMarkAtCursor} from './markAtCursor'

/** The name the letter spacing mark goes by in the editor. */
export const LETTER_SPACING = 'letterSpacing'

/**
 * The letter spacings words can be set in, from tight to widest. The stylesheet and the printed letter
 * each give every one its width, so a value names a step rather than a measure.
 */
export const LETTER_SPACINGS = ['tight', 'wide', 'wider', 'widest'] as const

/** A letter spacing words can be set in. */
export type LetterSpacing = typeof LETTER_SPACINGS[number]

/** The line heights a paragraph or heading can be set in, the normal one storing nothing. */
export const LINE_HEIGHTS = ['1', '1.15', '1.5', '2'] as const

/** A line height a paragraph or heading can be set in. */
export type LineHeight = typeof LINE_HEIGHTS[number]

function isLetterSpacing(value: string | null | undefined): value is LetterSpacing {
    return (LETTER_SPACINGS as readonly string[]).includes(value ?? '')
}

function isLineHeight(value: string | null | undefined): value is LineHeight {
    return (LINE_HEIGHTS as readonly string[]).includes(value ?? '')
}

/**
 * Words set closer together or further apart, stored as `<span data-spacing="wide">words</span>`, the
 * way words in a font of their own are stored. Markdown has no word for it, and inline HTML is what
 * survives the renderer, the sanitiser and the way back.
 *
 * <p>It ranks above bold, italic and the other marks, so it wraps them rather than being cut in pieces
 * around them.
 */
export const TextLetterSpacing = Mark.create({
    name: LETTER_SPACING,
    priority: 205,

    addAttributes() {
        return {
            spacing: {
                default: null,
                parseHTML: (element: HTMLElement) => {
                    const value = element.getAttribute('data-spacing')
                    return isLetterSpacing(value) ? value : null
                },
                renderHTML: (attributes: {spacing: LetterSpacing | null}) =>
                    attributes.spacing ? {'data-spacing': attributes.spacing} : {},
            },
        }
    },

    parseHTML() {
        return [{tag: 'span[data-spacing]', getAttrs: (element: HTMLElement) => isLetterSpacing(element.getAttribute('data-spacing')) ? null : false}]
    },

    renderHTML({HTMLAttributes}) {
        return ['span', mergeAttributes(HTMLAttributes), 0]
    },
})

/** The letter spacing of the words at the cursor, or null where they have none of their own. */
export function letterSpacingAtCursor(editor: Editor | undefined): LetterSpacing | null {
    const spacing = editor?.getAttributes(LETTER_SPACING).spacing
    return isLetterSpacing(spacing) ? spacing : null
}

/** Sets the words at the cursor in a letter spacing, or back in the normal one for null. */
export function applyLetterSpacing(editor: Editor, spacing: LetterSpacing | null): void {
    applyMarkAtCursor(editor, LETTER_SPACING, spacing ? {spacing} : null)
}

/** Writes spaced words back as the span they are stored as. */
export function extendTurndownWithLetterSpacing(turndown: TurndownService): void {
    turndown.addRule(LETTER_SPACING, {
        filter: (node) => node.nodeName === 'SPAN' && isLetterSpacing((node as HTMLElement).getAttribute('data-spacing')),
        replacement: (content, node) => {
            const spacing = (node as HTMLElement).getAttribute('data-spacing')
            return content ? `<span data-spacing="${spacing}">${content}</span>` : content
        },
    })
}

/**
 * The line height a block is shown with: its own `data-line-height`, or that of the block wrapped
 * around it, which is how the stored markdown carries it.
 */
export function lineHeightOf(element: Element): LineHeight | null {
    const value = element.closest('[data-line-height]')?.getAttribute('data-line-height')
    return isLineHeight(value) ? value : null
}

/**
 * The line height of paragraphs and headings, kept as `data-line-height` on the block the way its
 * alignment is kept, and stored the same way: the block wrapped in a `div` carrying both.
 */
export const BlockLineHeight = Extension.create({
    name: 'blockLineHeight',

    addGlobalAttributes() {
        return [{
            types: ['heading', 'paragraph'],
            attributes: {
                lineHeight: {
                    default: null,
                    parseHTML: (element: HTMLElement) => lineHeightOf(element),
                    renderHTML: (attributes: {lineHeight?: string | null}) =>
                        isLineHeight(attributes.lineHeight) ? {'data-line-height': attributes.lineHeight} : {},
                },
            },
        }]
    },
})

/** The line height of the paragraph or heading at the cursor, or null for the normal one. */
export function lineHeightAtCursor(editor: Editor | undefined): LineHeight | null {
    const value = editor?.getAttributes('paragraph').lineHeight ?? editor?.getAttributes('heading').lineHeight
    return isLineHeight(value) ? value : null
}

/**
 * Sets the paragraph or heading at the cursor, or every one selected, in a line height, or back in the
 * normal one for null. A table cell has no block to wrap, so its text keeps the normal line height.
 */
export function applyLineHeight(editor: Editor, lineHeight: LineHeight | null): void {
    if (editor.isActive('table')) return
    editor.chain().focus()
        .updateAttributes('paragraph', {lineHeight})
        .updateAttributes('heading', {lineHeight})
        .run()
}
