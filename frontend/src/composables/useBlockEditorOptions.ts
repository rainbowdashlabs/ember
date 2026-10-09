/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, provide, toValue, type Component, type ComputedRef, type InjectionKey, type MaybeRefOrGetter} from 'vue'
import type {CellContentType, FontFamilyOption, MemberGroup, UserTag} from '@/api/generated/schema'
import type {EditorTokens} from '@/components/input/markdowneditor/editorTokens'

/** What a block's visibility is chosen from: the station's groups and tags. */
export interface BlockRestrictionChoices {
    groups: MemberGroup[]
    tags: UserTag[]
}

/**
 * What the block editor offers beyond what a page needs, for a surface that writes something else
 * with it, such as a letter.
 *
 * <p>Every part is off where nothing is provided, so a page, a news entry, a wiki article and a system
 * news entry are edited exactly as before.
 */
export interface BlockEditorOptions {
    /** The kinds of block an empty cell offers, every kind where left out. */
    allowedKinds?: readonly CellContentType[]
    /** The most columns a row holds. */
    maxColumns: number
    /** What text blocks show as chips, in the editor and rendered, such as the placeholders of a letter. */
    tokens?: EditorTokens
    /**
     * Drawn above the text editor of a block and handed an `insert` function that puts content in at
     * the cursor, such as a placeholder picker.
     */
    markdownTools?: Component
    /** What a block's visibility is chosen from; no block offers a visibility where left out. */
    restrictable?: BlockRestrictionChoices
    /** Whether a block's visibility may also depend on the member having a second guardian. */
    guardianCondition?: boolean
    /** Whether a row may draw a line between its columns. */
    columnLines?: boolean
    /** Whether a divider may run vertically, between the two blocks either side of it. */
    verticalDivider?: boolean
    /** Whether a picture block may show the station's logo. */
    stationLogo?: boolean
    /**
     * The font families the selected words of a text block can be set in; the text editor's menu offers
     * no font where left out.
     */
    textFonts?: readonly FontFamilyOption[]
}

const DEFAULT_MAX_COLUMNS = 4

const BLOCK_EDITOR_OPTIONS: InjectionKey<MaybeRefOrGetter<Partial<BlockEditorOptions>>> = Symbol('blockEditorOptions')

/**
 * Hands the block editor below its options. A getter or a ref keeps them current, so choices that
 * load later, such as the groups, reach blocks already on the screen.
 */
export function provideBlockEditorOptions(options: MaybeRefOrGetter<Partial<BlockEditorOptions>>): void {
    provide(BLOCK_EDITOR_OPTIONS, options)
}

/** The block editor's options, a page's where nothing was provided. */
export function useBlockEditorOptions(): ComputedRef<BlockEditorOptions> {
    const provided = inject(BLOCK_EDITOR_OPTIONS, {})
    return computed(() => ({maxColumns: DEFAULT_MAX_COLUMNS, ...toValue(provided)}))
}
