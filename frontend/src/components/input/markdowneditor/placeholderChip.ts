/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mergeAttributes, Node} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import type {EditorTokens} from './editorTokens'
import {replaceTemplatePlaceholders} from '@/util/placeholders'
import {escapeHtml} from '@/util/html'

/** What every key is called, from the catalogue: a map, or anything that answers like one. */
export type PlaceholderLabels = Pick<ReadonlyMap<string, string>, 'get'>

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

/**
 * Placeholders as chips in the markdown editor: stored markdown turns its `{{key}}` into chips with
 * the label the catalogue gives the key, and a chip is written back as `{{key}}`.
 *
 * @param labels the label of every key, from the station's catalogue
 */
export function placeholderTokens(labels: PlaceholderLabels): EditorTokens {
    return {
        extensions: [PlaceholderChip],
        prepare: (markdown: string) => replaceTemplatePlaceholders(markdown, key =>
            `<span class="placeholder-chip" data-placeholder="${escapeHtml(key)}">${escapeHtml(labels.get(key) ?? key)}</span>`),
        extendTurndown: (turndown: TurndownService) => {
            turndown.addRule('placeholderChip', {
                filter: (node) => node.nodeName === 'SPAN' && (node as HTMLElement).hasAttribute('data-placeholder'),
                replacement: (_content, node) => `{{${(node as HTMLElement).getAttribute('data-placeholder')}}}`,
            })
        },
    }
}

/**
 * A text with its placeholders written as what they are called, for a short look at it outside the
 * editor, such as the label of a field over a PDF.
 *
 * @param text   the text with `{{key}}` in it
 * @param labels the label of every key, from the station's catalogue
 */
export function labelledText(text: string, labels: PlaceholderLabels): string {
    return replaceTemplatePlaceholders(text, key => labels.get(key) ?? key)
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
