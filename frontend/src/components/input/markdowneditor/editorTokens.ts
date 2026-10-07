/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AnyExtension} from '@tiptap/vue-3'
import type TurndownService from 'turndown'

/**
 * Something the stored markdown carries that the editor shows as a node of its own, such as a
 * placeholder of a document template.
 *
 * <p>Markdown has no word for it, so it travels both ways through the editor: `prepare` turns the
 * stored markdown into the HTML the node is parsed from before the editor reads it, and
 * `extendTurndown` adds the rule that writes the node back. The editor stays the one every other
 * screen uses; a screen that needs more hands this in.
 */
export interface EditorTokens {
    /** The extensions the node needs. */
    extensions: AnyExtension[]
    /** Turns the stored markdown into markdown whose tokens are the HTML the node is parsed from. */
    prepare: (markdown: string) => string
    /** Adds the rules that write the node back as its token. */
    extendTurndown: (turndown: TurndownService) => void
}
