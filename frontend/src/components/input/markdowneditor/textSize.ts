/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {Mark, mergeAttributes} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import {pixelSize} from '@/util/textSize'

/** The name the mark goes by in the editor, for setting, removing and reading it. */
export const TEXT_SIZE = 'textSize'

/**
 * Words set in a size of their own, in pixels, stored as `<span data-size="14">words</span>`.
 *
 * <p>Markdown has no word for a size, and inline HTML is what survives the renderer, the sanitiser and
 * the way back, the way fonts are. The number lives in a data attribute rather than a style, so the
 * stored text carries nothing but the number; the renderer and the editor derive the font size from it.
 *
 * <p>Its parse rule ranks above the one of coloured text, which would otherwise also read the rendered
 * span for its style and wrap the words in an empty mark. It ranks above bold, italic, the font and the
 * other marks as well, so it wraps them rather than being cut in pieces around them.
 */
export const TextSize = Mark.create({
    name: TEXT_SIZE,
    priority: 210,

    addAttributes() {
        return {
            size: {
                default: null,
                parseHTML: (element: HTMLElement) => pixelSize(element.getAttribute('data-size')),
                renderHTML: (attributes: {size: number | null}) =>
                    attributes.size ? {'data-size': String(attributes.size), style: `font-size: ${attributes.size}px`} : {},
            },
        }
    },

    parseHTML() {
        return [{tag: 'span[data-size]', getAttrs: (element: HTMLElement) => pixelSize(element.getAttribute('data-size')) ? null : false}]
    },

    renderHTML({HTMLAttributes}) {
        return ['span', mergeAttributes(HTMLAttributes), 0]
    },
})

/** The size of the words at the cursor in pixels, or null where they have none of their own. */
export function sizeAtCursor(attributes: Record<string, unknown> | undefined): number | null {
    const size = attributes?.size
    return typeof size === 'number' ? size : null
}

/** Writes sized words back as the span they are stored as, and nothing but the words for a size out of bounds. */
export function extendTurndownWithTextSize(turndown: TurndownService): void {
    turndown.addRule(TEXT_SIZE, {
        filter: (node) => node.nodeName === 'SPAN' && (node as HTMLElement).hasAttribute('data-size'),
        replacement: (content, node) => {
            const size = pixelSize((node as HTMLElement).getAttribute('data-size'))
            return size && content ? `<span data-size="${size}">${content}</span>` : content
        },
    })
}
