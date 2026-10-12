/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Editor, Mark, mergeAttributes} from '@tiptap/vue-3'
import type TurndownService from 'turndown'
import {NORMAL_TEXT_SIZE, pointSize, pointsOfPixels} from '@/util/textSize'
import {applyMarkAtCursor} from './markAtCursor'

/** The name the mark goes by in the editor, for setting, removing and reading it. */
export const TEXT_SIZE = 'textSize'

/**
 * The size in points the style of an element sets, when it is one words can be set in. Words stored in
 * pixels, as every size was before sizes were written in points, are read at the nearest whole point
 * and written back in points once the text is saved again.
 */
export function styledSize(element: HTMLElement): number | null {
    const [, amount, unit] = /^(\d+(?:\.\d+)?)(pt|px)$/.exec(element.style.fontSize) ?? []
    if (!amount) return null
    return unit === 'pt' ? pointSize(amount) : pointsOfPixels(Number(amount))
}

/**
 * Reads sized spans: a span whose style sets nothing but the size is taken by the size alone; one that
 * sets a colour as well is left to the coloured text too, so neither swallows the other.
 */
function sizedSpanRule(alone: boolean) {
    return {
        tag: 'span',
        consuming: alone,
        getAttrs: (element: HTMLElement) => styledSize(element) && (element.style.length === 1) === alone ? null : false,
    }
}

/**
 * Words set in a size of their own, in points, stored as `<span style="font-size: 11pt">words</span>`,
 * the way coloured words are stored.
 *
 * <p>Markdown has no word for a size, and inline HTML is what survives the renderer, the sanitiser and
 * the way back, the way fonts and colours are.
 *
 * <p>Its parse rules rank above the one of coloured text, which reads every span with a style and would
 * otherwise wrap the sized words in an empty mark of its own. It ranks above bold, italic, the font and
 * the other marks as well, so it wraps them rather than being cut in pieces around them.
 */
export const TextSize = Mark.create({
    name: TEXT_SIZE,
    priority: 210,

    addAttributes() {
        return {
            size: {
                default: null,
                parseHTML: styledSize,
                renderHTML: (attributes: {size: number | null}) =>
                    attributes.size ? {style: `font-size: ${attributes.size}pt`} : {},
            },
        }
    },

    parseHTML() {
        return [sizedSpanRule(true), sizedSpanRule(false)]
    },

    renderHTML({HTMLAttributes}) {
        return ['span', mergeAttributes(HTMLAttributes), 0]
    },
})

/** The size of the words at the cursor in points, or null where they have none of their own. */
export function sizeAtCursor(attributes: Record<string, unknown> | undefined): number | null {
    const size = attributes?.size
    return typeof size === 'number' ? size : null
}

/**
 * Sets the words at the cursor in a size, or gives them their normal size back for null, the way
 * {@link applyMarkAtCursor} reaches them.
 */
export function applyTextSize(editor: Editor, size: number | null): void {
    applyMarkAtCursor(editor, TEXT_SIZE, size ? {size} : null)
}

/**
 * The size in whole points the words at the cursor are shown in, measured on screen, for a size entry to
 * start counting from. Where the editor is not on screen yet or measures nothing usable, the normal size.
 */
export function shownSizeAtCursor(editor: Editor): number {
    if (!editor.isInitialized || editor.isDestroyed) return NORMAL_TEXT_SIZE
    const {node} = editor.view.domAtPos(editor.state.selection.from)
    const element = node instanceof Element ? node : node.parentElement
    const measured = element ? Number.parseFloat(getComputedStyle(element).fontSize) : Number.NaN
    return pointsOfPixels(measured) ?? NORMAL_TEXT_SIZE
}

/** Writes sized words back as the span they are stored as. */
export function extendTurndownWithTextSize(turndown: TurndownService): void {
    turndown.addRule(TEXT_SIZE, {
        filter: (node) => node.nodeName === 'SPAN' && styledSize(node as HTMLElement) !== null,
        replacement: (content, node) => {
            const size = styledSize(node as HTMLElement)
            return size && content ? `<span style="font-size: ${size}pt">${content}</span>` : content
        },
    })
}
