/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Editor, getMarkRange} from '@tiptap/vue-3'
import type {ResolvedPos} from '@tiptap/pm/model'
import type {EditorState} from '@tiptap/pm/state'

const WORD_CHARACTER = /[\p{L}\p{N}_'-]/u

/**
 * Sets a mark on the words at the cursor, or takes it off them for null.
 *
 * <p>A selection is marked as it stands. A bare cursor marks the words it stands in: the run carrying
 * the mark together where it stands in one, else the word around it, and stays where it was. Only where
 * there is no word either, on an empty line or between two spaces, does the mark wait for what is typed
 * next. A mark set on a bare cursor alone changed nothing on screen, while the menu already named it as
 * the one at the cursor.
 *
 * @param editor     the editor
 * @param mark       the name of the mark
 * @param attributes what the mark is set with, or null to take it off
 */
export function applyMarkAtCursor(editor: Editor, mark: string, attributes: Record<string, unknown> | null): void {
    const {selection} = editor.state
    const range = selection.empty ? rangeAtCursor(editor.state, mark) : null
    const chain = editor.chain().focus()
    if (range) chain.setTextSelection(range)
    if (attributes) chain.setMark(mark, attributes)
    else chain.unsetMark(mark)
    if (range) chain.setTextSelection(selection.from)
    chain.run()
}

function rangeAtCursor(state: EditorState, mark: string): {from: number; to: number} | null {
    const {$from} = state.selection
    return getMarkRange($from, state.schema.marks[mark]!) ?? wordAtCursor($from)
}

function wordAtCursor($from: ResolvedPos): {from: number; to: number} | null {
    const text = $from.parent.textBetween(0, $from.parent.content.size, undefined, '￼')
    let start = $from.parentOffset
    let end = start
    while (start > 0 && WORD_CHARACTER.test(text[start - 1]!)) start--
    while (end < text.length && WORD_CHARACTER.test(text[end]!)) end++
    return start < end ? {from: $from.start() + start, to: $from.start() + end} : null
}
