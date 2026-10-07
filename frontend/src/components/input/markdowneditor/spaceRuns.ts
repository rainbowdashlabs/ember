/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Editor, getHTMLFromFragment} from '@tiptap/vue-3'
import type {Node as ProseMirrorNode} from '@tiptap/pm/model'
import type {Transaction} from '@tiptap/pm/state'

const NBSP = String.fromCodePoint(0xa0)
const RUN = new RegExp(`[ ${NBSP}]{2,}`, 'g')
const INLINE_CODE = /(?<!<pre>)<code>([^<]*)<\/code>/g
const SPACES = / {2,}/g
const LINE_BREAK = '\n'
const OTHER = String.fromCodePoint(0xfffc)

/** The parts of an editor that reading and rewriting its content needs, as the component and its events hand it over. */
export type EditorContentAccess = Pick<Editor, 'state' | 'schema' | 'view'>

/**
 * Several spaces in a row, as stored: every space of the run but the last becomes a non-breaking space.
 *
 * <p>Markdown, HTML and Pandoc all fold a run of spaces into one, and none of them folds a non-breaking
 * space, which Typst prints as a space too. The last space of the run stays an ordinary one, so a line
 * can still wrap there. A run that ends a line has no word after it to keep the ordinary space in front
 * of and would lose it to the trimming, so it is stored whole. Single spaces are left as they are.
 *
 * @param text the text of a line, line breaks inside it written as `\n`
 */
export function storedSpaceRuns(text: string): string {
    return text.replace(RUN, (run: string, offset: number) => {
        const end = offset + run.length
        return end === text.length || text[end] === LINE_BREAK
            ? NBSP.repeat(run.length)
            : `${NBSP.repeat(run.length - 1)} `
    })
}

/**
 * Several spaces in a row, as typed: the stored run as ordinary spaces again. A non-breaking space on
 * its own between two words was put there on purpose and stays.
 */
export function typedSpaceRuns(text: string): string {
    return text.replace(RUN, (run: string) => ' '.repeat(run.length))
}

/**
 * The editor's content as HTML, with its runs of spaces in the stored form. Code keeps its spaces as
 * they are, because Markdown and Pandoc keep them there and a non-breaking space would end up in what
 * is copied out of it.
 */
export function htmlWithStoredSpaces(editor: EditorContentAccess): string {
    const tr = editor.state.tr
    rewriteTextblocks(tr, storedSpaceRuns, true)
    return getHTMLFromFragment(tr.doc.content, editor.schema)
}

/**
 * Turns the stored runs of spaces in the editor's content back into ordinary spaces, outside the
 * history, so they are typed over and deleted like any other space.
 */
export function showTypedSpaces(editor: EditorContentAccess): void {
    const tr = editor.state.tr
    rewriteTextblocks(tr, typedSpaceRuns, false)
    if (tr.docChanged) editor.view.dispatch(tr.setMeta('addToHistory', false))
}

/**
 * Rendered HTML with the runs of spaces in inline code as non-breaking spaces, which the editor reads
 * without folding them and {@link showTypedSpaces} turns back. A code block keeps its spaces on its own.
 */
export function keepInlineCodeSpaces(html: string): string {
    return html.replace(INLINE_CODE, (_match, code: string) =>
        `<code>${code.replace(SPACES, (run: string) => NBSP.repeat(run.length))}</code>`)
}

/**
 * Rewrites the text of every textblock but a code block, one character for another, keeping the marks.
 *
 * @param tr       the transaction to rewrite in
 * @param rewrite  the new text of a textblock, as long as the old one
 * @param skipCode whether text in inline code is left as it is
 */
function rewriteTextblocks(tr: Transaction, rewrite: (text: string) => string, skipCode: boolean): void {
    tr.doc.descendants((block, position) => {
        if (!block.isTextblock) return true
        if (block.type.spec.code) return false
        const start = position + 1
        const before = textOf(block, skipCode)
        const after = rewrite(before)
        for (let index = 0; index < before.length; index++) {
            if (before[index] === after[index]) continue
            const at = start + index
            const marks = tr.doc.nodeAt(at)?.marks ?? []
            tr.replaceWith(at, at + 1, tr.doc.type.schema.text(after[index]!, marks))
        }
        return false
    })
}

/**
 * A textblock's text, one character per position: a line break as `\n`, anything else that is not
 * text, and inline code where it is skipped, as a character no rewrite touches.
 */
function textOf(block: ProseMirrorNode, skipCode: boolean): string {
    let text = ''
    block.forEach(child => {
        if (child.isText && !(skipCode && child.marks.some(mark => mark.type.spec.code))) text += child.text
        else if (child.type.spec.linebreakReplacement) text += LINE_BREAK
        else text += OTHER.repeat(child.nodeSize)
    })
    return text
}
