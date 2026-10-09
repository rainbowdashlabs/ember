/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {h, type FunctionalComponent} from 'vue'
import type {Content} from '@tiptap/vue-3'
import type {FontFamilyOption, LetterPart, LetterPartRules, Placeholder} from '@/api/generated/schema'
import type {BlockEditorOptions, BlockRestrictionChoices} from '@/composables/useBlockEditorOptions'
import {placeholderContent, placeholderTokens, type PlaceholderLabels} from '@/components/input/markdowneditor/placeholderChip'
import type {PlaceholderChoice} from './placeholderpicker/placeholderKey'
import PlaceholderPicker from './placeholderpicker/PlaceholderPicker.vue'

/** What the block editor of a letter knows of the station. */
export interface LetterCatalogue {
    placeholders: Placeholder[]
    labels: PlaceholderLabels
    legal: boolean
    choices: BlockRestrictionChoices
    /** The font families the template reaches: the instance's, the association's and the station's. */
    fonts: readonly FontFamilyOption[]
    /** What each part of a letter holds, as the server checks it. */
    parts: readonly LetterPartRules[]
}

/**
 * The block editor as a letter uses it: the blocks and the columns the part takes, as the server names
 * them, with lines between the columns where a row asks for them, placeholders as chips with their
 * picker above every text, a font for selected words from the families the template reaches, a
 * visibility on every block that may also depend on a second guardian, and the station logo as a
 * picture. Until the server has named them, a part offers no block and a single column.
 *
 * @param catalogue what the station's letters can name and restrict blocks to
 * @param part      the part of the letter being written
 */
export function letterBlockOptions(catalogue: LetterCatalogue, part: LetterPart): Partial<BlockEditorOptions> {
    const tools: FunctionalComponent<{insert: (content: Content) => void}> = props => h(PlaceholderPicker, {
        placeholders: catalogue.placeholders,
        legal: catalogue.legal,
        onPick: (choice: PlaceholderChoice) => props.insert(placeholderContent(choice.key, choice.label)),
    })
    tools.props = ['insert']
    const rules = catalogue.parts.find(candidate => candidate.part === part)
    return {
        allowedKinds: rules?.kinds ?? [],
        maxColumns: rules?.maxColumns ?? 1,
        tokens: placeholderTokens(catalogue.labels),
        markdownTools: tools,
        textFonts: catalogue.fonts,
        restrictable: catalogue.choices,
        guardianCondition: true,
        columnLines: true,
        verticalDivider: true,
        stationLogo: true,
    }
}
