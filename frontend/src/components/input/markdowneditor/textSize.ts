/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Editor, getMarkRange, Mark, mergeAttributes} from '@tiptap/vue-3'
import type {ResolvedPos} from '@tiptap/pm/model'
import type {EditorState} from '@tiptap/pm/state'
import type TurndownService from 'turndown'
import {NORMAL_TEXT_SIZE, pixelSize} from '@/util/textSize'

/** The name the mark goes by in the editor, for setting, removing and reading it. */
export const TEXT_SIZE = 'textSize'

/**
 * The size in pixels the style of an element sets, when it is a whole number of pixels words can be
 * set in.
 */
export function styledSize(element: HTMLElement): number | null {
    const pixels = /^(\d+)px$/.exec(element.style.fontSize)?.[1]
    return pixels ? pixelSize(pixels) : null
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
 * Words set in a size of their own, in pixels, stored as `<span style="font-size: 14px">words</span>`,
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
                    attributes.size ? {style: `font-size: ${attributes.size}px`} : {},
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

/** The size of the words at the cursor in pixels, or null where they have none of their own. */
export function sizeAtCursor(attributes: Record<string, unknown> | undefined): number | null {
    const size = attributes?.size
    return typeof size === 'number' ? size : null
}

/**
 * Sets the words at the cursor in a size, or gives them their normal size back for null.
 *
 * <p>A selection is sized as it stands. A bare cursor sizes the words it stands in: the run sized
 * together where it stands in one, else the word around it, and stays where it was. Only where there is
 * no word either, on an empty line or between two spaces, does the size wait for what is typed next. A
 * size set on a bare cursor alone changed nothing on screen, while the menu already named it as the size
 * at the cursor.
 */
export function applyTextSize(editor: Editor, size: number | null): void {
    const {selection} = editor.state
    const range = selection.empty ? rangeAtCursor(editor.state) : null
    const chain = editor.chain().focus()
    if (range) chain.setTextSelection(range)
    if (size) chain.setMark(TEXT_SIZE, {size})
    else chain.unsetMark(TEXT_SIZE)
    if (range) chain.setTextSelection(selection.from)
    chain.run()
}

/**
 * The size in whole pixels the words at the cursor are shown in, measured on screen, for a size entry to
 * start counting from. Where the editor is not on screen yet or measures nothing usable, the normal size.
 */
export function shownSizeAtCursor(editor: Editor): number {
    if (!editor.isInitialized || editor.isDestroyed) return NORMAL_TEXT_SIZE
    const {node} = editor.view.domAtPos(editor.state.selection.from)
    const element = node instanceof Element ? node : node.parentElement
    const measured = element ? Math.round(Number.parseFloat(getComputedStyle(element).fontSize)) : Number.NaN
    return pixelSize(measured) ?? NORMAL_TEXT_SIZE
}

const WORD_CHARACTER = /[\p{L}\p{N}_'-]/u

function rangeAtCursor(state: EditorState): {from: number; to: number} | null {
    const {$from} = state.selection
    return getMarkRange($from, state.schema.marks[TEXT_SIZE]!) ?? wordAtCursor($from)
}

function wordAtCursor($from: ResolvedPos): {from: number; to: number} | null {
    const text = $from.parent.textBetween(0, $from.parent.content.size, undefined, '￼')
    let start = $from.parentOffset
    let end = start
    while (start > 0 && WORD_CHARACTER.test(text[start - 1]!)) start--
    while (end < text.length && WORD_CHARACTER.test(text[end]!)) end++
    return start < end ? {from: $from.start() + start, to: $from.start() + end} : null
}

/** Writes sized words back as the span they are stored as. */
export function extendTurndownWithTextSize(turndown: TurndownService): void {
    turndown.addRule(TEXT_SIZE, {
        filter: (node) => node.nodeName === 'SPAN' && styledSize(node as HTMLElement) !== null,
        replacement: (content, node) => {
            const size = styledSize(node as HTMLElement)
            return size && content ? `<span style="font-size: ${size}px">${content}</span>` : content
        },
    })
}
