/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mergeAttributes, Node} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import type {EditorTokens} from './editorTokens'

/** How a placeholder is written in the stored markdown: `{{key}}`, spaces inside the braces allowed. */
const TOKEN = /\{\{\s*([A-Za-z0-9_.]+)\s*}}/g

/**
 * A placeholder in a document template, shown in the editor as a chip with its label and stored as
 * `{{key}}`.
 *
 * <p>It is one atom: the cursor steps over it and a backspace takes it out whole, so nobody ends up
 * with half a key in the text. The chip carries the key and the label it was drawn with; the label is
 * only for the eye, the key is what is stored.
 */
export const PlaceholderChip = Node.create({
    name: 'placeholderChip',
    group: 'inline',
    inline: true,
    atom: true,
    selectable: true,

    addAttributes() {
        return {
            key: {
                default: '',
                parseHTML: (element: HTMLElement) => element.getAttribute('data-placeholder') ?? '',
                renderHTML: (attributes: {key: string}) => ({'data-placeholder': attributes.key}),
            },
            label: {
                default: '',
                parseHTML: (element: HTMLElement) => element.textContent ?? '',
                renderHTML: () => ({}),
            },
        }
    },

    parseHTML() {
        return [{tag: 'span[data-placeholder]'}]
    },

    renderHTML({node, HTMLAttributes}) {
        return ['span', mergeAttributes(HTMLAttributes, {class: 'placeholder-chip'}), node.attrs.label || node.attrs.key]
    },
})

/** Escapes a label for the HTML the stored markdown is turned into before the editor reads it. */
function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/**
 * Placeholders as chips in the markdown editor: stored markdown turns its `{{key}}` into chips with
 * the label the catalogue gives the key, and a chip is written back as `{{key}}`.
 *
 * @param labels the label of every key, from the station's catalogue
 */
export function placeholderTokens(labels: ReadonlyMap<string, string>): EditorTokens {
    return {
        extensions: [PlaceholderChip],
        prepare: (markdown: string) => markdown.replace(TOKEN, (_match, key: string) =>
            `<span data-placeholder="${escapeHtml(key)}">${escapeHtml(labels.get(key) ?? key)}</span>`),
        extendTurndown: (turndown: TurndownService) => {
            turndown.addRule('placeholderChip', {
                filter: (node) => node.nodeName === 'SPAN' && (node as HTMLElement).hasAttribute('data-placeholder'),
                replacement: (_content, node) => `{{${(node as HTMLElement).getAttribute('data-placeholder')}}}`,
            })
        },
    }
}

/**
 * The chip a placeholder is inserted as.
 *
 * @param key   the key it stands for
 * @param label what it is called
 */
export function placeholderContent(key: string, label: string) {
    return {type: PlaceholderChip.name, attrs: {key, label}}
}
