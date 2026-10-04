/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {Mark, mergeAttributes} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import {escapeHtml, unescapeHtml} from '@/util/html'

/** The name the mark goes by in the editor, for setting, removing and reading it. */
export const TEXT_FONT = 'textFont'

/** What the mark asks of the editor around it. */
export interface TextFontOptions {
    /**
     * Whether the words of a family show in the family itself, which the template editor arranges by
     * loading its files. Where they do not, the mark names the family on hover instead.
     */
    shown: (family: string) => boolean
}

/**
 * Words set in a font family of their own, stored as `<span data-font="Family">words</span>`.
 *
 * <p>Markdown has no word for a font, and inline HTML is what survives the renderer, the sanitiser and
 * the way back, the way coloured text does. In the template editor the words show in the family itself,
 * from the font files the editor loads for as long as it is open (`useEditorFonts`), and the stylesheet
 * it adds sets them in it by their `data-font`. Where no file could be loaded, and in every other editor,
 * the words keep the editor's own look: the mark underlines them and names the family on hover, and the
 * menu names the family at the cursor.
 *
 * <p>It ranks above bold, italic and the other marks, so it wraps them rather than being cut in pieces
 * around them, and a family spanning a bold word is stored as one span.
 */
export const TextFont = Mark.create<TextFontOptions>({
    name: TEXT_FONT,
    priority: 200,

    addOptions() {
        return {shown: () => false}
    },

    addAttributes() {
        const shown = this.options.shown
        return {
            family: {
                default: '',
                parseHTML: (element: HTMLElement) => element.getAttribute('data-font') ?? '',
                renderHTML: (attributes: {family: string}) => shown(attributes.family)
                    ? {'data-font': attributes.family}
                    : {'data-font': attributes.family, title: attributes.family},
            },
        }
    },

    parseHTML() {
        return [{tag: 'span[data-font]'}]
    },

    renderHTML({HTMLAttributes}) {
        return ['span', mergeAttributes(HTMLAttributes, {class: 'text-font'}), 0]
    },
})

const STORED_FAMILY = /<span data-font="([^"]*)">/g

/** The families words of a stored text are set in, each as often as it is named. */
export function familiesIn(markdown: string): string[] {
    return [...markdown.matchAll(STORED_FAMILY)].map(match => unescapeHtml(match[1] ?? '').trim()).filter(Boolean)
}

/**
 * Writes words set in a family of their own back as the span they are stored as, the family escaped,
 * and nothing but the words where the span names no family.
 */
export function extendTurndownWithTextFont(turndown: TurndownService): void {
    turndown.addRule(TEXT_FONT, {
        filter: (node) => node.nodeName === 'SPAN' && (node as HTMLElement).hasAttribute('data-font'),
        replacement: (content, node) => {
            const family = (node as HTMLElement).getAttribute('data-font')?.trim() ?? ''
            return family && content ? `<span data-font="${escapeHtml(family)}">${content}</span>` : content
        },
    })
}
