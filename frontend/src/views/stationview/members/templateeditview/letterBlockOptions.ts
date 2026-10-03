/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {h, type FunctionalComponent} from 'vue'
import type {Content} from '@tiptap/vue-3'
import {CellContentType, type Placeholder} from '@/api/generated/schema'
import type {BlockEditorOptions, BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'
import {placeholderContent, placeholderTokens} from '@/components/input/markdowneditor/placeholderChip'
import PlaceholderPicker from './PlaceholderPicker.vue'

/** The most columns a row of a letter holds, which is what the server takes. */
export const LETTER_COLUMNS = 3

/** The blocks a letter prints: texts and pictures, and blocks stacked in a column by splitting. */
const LETTER_KINDS: readonly CellContentType[] = [CellContentType.MARKDOWN, CellContentType.IMAGE]

/** What the block editor of a letter knows of the station. */
export interface LetterCatalogue {
    placeholders: Placeholder[]
    labels: ReadonlyMap<string, string>
    legal: boolean
    choices: BlockRestrictionChoices
}

/**
 * The block editor as a letter uses it: texts and pictures in up to three columns, placeholders as
 * chips with their picker above every text, a visibility on every block, and the station logo as a
 * picture.
 *
 * @param catalogue  what the station's letters can name and restrict blocks to
 * @param signatures whether the picker offers signature fields, which stand in the body only
 */
export function letterBlockOptions(catalogue: LetterCatalogue, signatures: boolean): Partial<BlockEditorOptions> {
    const tools: FunctionalComponent<{insert: (content: Content) => void}> = props => h(PlaceholderPicker, {
        placeholders: catalogue.placeholders,
        legal: catalogue.legal,
        signatures,
        onPick: (placeholder: Placeholder) => props.insert(placeholderContent(placeholder.key, placeholder.label)),
    })
    tools.props = ['insert']
    return {
        allowedKinds: LETTER_KINDS,
        maxColumns: LETTER_COLUMNS,
        tokens: placeholderTokens(catalogue.labels),
        markdownTools: tools,
        restrictable: catalogue.choices,
        stationLogo: true,
    }
}
