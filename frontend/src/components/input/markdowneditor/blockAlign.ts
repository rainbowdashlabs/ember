/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Editor} from '@tiptap/core'
import {TextAlign} from '@tiptap/extension-text-align'
import type TurndownService from 'turndown'

/** The alignments a paragraph or heading is stored with; left is the default and stores nothing. */
export const STORED_ALIGNMENTS = ['center', 'right', 'justify'] as const

/** An alignment that is stored, rather than the default. */
export type StoredAlignment = typeof STORED_ALIGNMENTS[number]

/** Every alignment the toolbar offers, the default first. */
export type BlockAlignment = 'left' | StoredAlignment

function isStored(value: string | null | undefined): value is StoredAlignment {
    return (STORED_ALIGNMENTS as readonly string[]).includes(value ?? '')
}

/**
 * The alignment an element is shown with: its own `data-align`, or that of the block wrapped around it,
 * which is how the stored markdown carries it.
 */
export function alignmentOf(element: Element): StoredAlignment | null {
    const value = element.closest('[data-align]')?.getAttribute('data-align')
    return isStored(value) ? value : null
}

/**
 * Centred, right-aligned and justified paragraphs and headings, stored as the block wrapped in
 * `<div data-align="center">`, a blank line on either side of the markdown inside it.
 *
 * <p>Markdown has no word for alignment. A `div` opening a line starts a block of HTML that ends at the
 * next blank line, so the markdown between the blank lines is still read as markdown: bold, colour,
 * fonts and placeholders inside an aligned paragraph keep working in the renderer, the editor and the
 * printed document alike. Left is the default and stays plain markdown.
 *
 * <p>The editor keeps the alignment as `data-align` on the block rather than as a style, so the one
 * stylesheet rule that aligns a rendered page also aligns the editor.
 */
export const BlockAlign = TextAlign.extend({
    addOptions() {
        return {types: ['heading', 'paragraph'], alignments: [...STORED_ALIGNMENTS], defaultAlignment: null}
    },

    addGlobalAttributes() {
        return [{
            types: this.options.types,
            attributes: {
                textAlign: {
                    default: null,
                    parseHTML: (element: HTMLElement) => alignmentOf(element),
                    renderHTML: (attributes: {textAlign?: string | null}) =>
                        isStored(attributes.textAlign) ? {'data-align': attributes.textAlign} : {},
                },
            },
        }]
    },

    addKeyboardShortcuts() {
        return {
            'Mod-Shift-l': () => alignBlock(this.editor, 'left'),
            'Mod-Shift-e': () => alignBlock(this.editor, 'center'),
            'Mod-Shift-r': () => alignBlock(this.editor, 'right'),
            'Mod-Shift-j': () => alignBlock(this.editor, 'justify'),
        }
    },
})

/**
 * Whether the paragraph or heading at the cursor can be aligned: everywhere but in a table cell, whose
 * text is stored on one line of the table, where no block can be wrapped around it.
 */
export function canAlign(editor: Editor): boolean {
    return !editor.isActive('table')
}

/** Whether the paragraph or heading at the cursor has this alignment, left meaning none stored. */
export function isAligned(editor: Editor, alignment: BlockAlignment): boolean {
    if (alignment !== 'left') return editor.isActive({textAlign: alignment})
    return !STORED_ALIGNMENTS.some(stored => editor.isActive({textAlign: stored}))
}

/** Aligns the paragraph or heading at the cursor, or every one selected; left removes the alignment. */
export function alignBlock(editor: Editor, alignment: BlockAlignment): boolean {
    if (!canAlign(editor)) return false
    const chain = editor.chain().focus()
    return (alignment === 'left' ? chain.unsetTextAlign() : chain.setTextAlign(alignment)).run()
}

function blockMarkdown(node: HTMLElement, content: string): string {
    const level = /^H([1-6])$/.exec(node.nodeName)?.[1]
    return level ? `${'#'.repeat(Number(level))} ${content}` : content
}

function isAlignedBlock(node: HTMLElement): boolean {
    return /^(P|H[1-6])$/.test(node.nodeName) && isStored(node.getAttribute('data-align'))
}

/** Writes an aligned paragraph or heading back as the wrapped block it is stored as. */
export function extendTurndownWithBlockAlign(turndown: TurndownService): void {
    turndown.addRule('blockAlign', {
        filter: (node) => isAlignedBlock(node as HTMLElement),
        replacement: (content, node) => {
            const element = node as HTMLElement
            if (!content.trim()) return '\n\n'
            const alignment = element.getAttribute('data-align')
            return `\n\n<div data-align="${alignment}">\n\n${blockMarkdown(element, content)}\n\n</div>\n\n`
        },
    })
}
