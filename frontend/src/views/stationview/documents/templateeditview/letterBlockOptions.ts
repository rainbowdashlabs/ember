/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {h, type FunctionalComponent} from 'vue'
import type {Content} from '@tiptap/vue-3'
import {CellContentType, type FontFamilyOption, type Placeholder} from '@/api/generated/schema'
import type {BlockEditorOptions, BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'
import {placeholderContent, placeholderTokens} from '@/components/input/markdowneditor/placeholderChip'
import PlaceholderPicker from './placeholderpicker/PlaceholderPicker.vue'

/** The most columns a row of a letter holds, which is what the server takes. */
export const LETTER_COLUMNS = 3

/**
 * The blocks the header and the footer of a letter print: texts, pictures, lines and gaps, and blocks
 * stacked in a column by splitting.
 */
const LETTERHEAD_KINDS: readonly CellContentType[] = [
    CellContentType.MARKDOWN,
    CellContentType.IMAGE,
    CellContentType.DIVIDER,
    CellContentType.SPACER,
]

/** The blocks the body of a letter prints: those of the letterhead and signature lines. */
const BODY_KINDS: readonly CellContentType[] = [...LETTERHEAD_KINDS, CellContentType.SIGNATURE]

/** What the block editor of a letter knows of the station. */
export interface LetterCatalogue {
    placeholders: Placeholder[]
    labels: ReadonlyMap<string, string>
    legal: boolean
    choices: BlockRestrictionChoices
    /** The font families the template reaches: the instance's, the association's and the station's. */
    fonts: readonly FontFamilyOption[]
}

/**
 * The block editor as a letter uses it: texts, pictures, lines and gaps in up to three columns, with
 * lines between the columns where a row asks for them, placeholders as chips with their picker above
 * every text, a font for selected words from the families the template reaches, a visibility on every
 * block that may also depend on a second guardian, and the station logo as a picture.
 *
 * @param catalogue  what the station's letters can name and restrict blocks to
 * @param signatures whether signature lines are offered, which stand in the body only
 */
export function letterBlockOptions(catalogue: LetterCatalogue, signatures: boolean): Partial<BlockEditorOptions> {
    const tools: FunctionalComponent<{insert: (content: Content) => void}> = props => h(PlaceholderPicker, {
        placeholders: catalogue.placeholders,
        legal: catalogue.legal,
        onPick: (placeholder: Placeholder) => props.insert(placeholderContent(placeholder.key, placeholder.label)),
    })
    tools.props = ['insert']
    return {
        allowedKinds: signatures ? BODY_KINDS : LETTERHEAD_KINDS,
        maxColumns: LETTER_COLUMNS,
        tokens: placeholderTokens(catalogue.labels),
        markdownTools: tools,
        textFonts: catalogue.fonts,
        restrictable: catalogue.choices,
        guardianCondition: true,
        columnLines: true,
        stationLogo: true,
    }
}
