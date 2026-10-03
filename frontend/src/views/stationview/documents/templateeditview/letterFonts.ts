/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DocumentTemplateKind} from '@/api/generated/schema'
import {familiesIn} from '@/components/input/markdowneditor/textFont'
import type {TemplateDraft} from './templateDraft'

/** Every text a value holds, however deep its rows, columns and stacked blocks nest it. */
function textsIn(value: unknown): string[] {
    if (typeof value === 'string') return [value]
    if (Array.isArray(value)) return value.flatMap(textsIn)
    if (value && typeof value === 'object') return Object.values(value).flatMap(textsIn)
    return []
}

/**
 * The families a letter shows its text in: the fonts of its body, header and footer, null where one is
 * the default font, and those words of its blocks are set in. A PDF template names none of them.
 */
export function letterFamilies(draft: TemplateDraft): (string | null)[] {
    if (draft.kind !== DocumentTemplateKind.LETTER) return []
    const {bodyFont, headerFont, footerFont} = draft.page
    const words = textsIn([draft.header, draft.body, draft.footer]).flatMap(familiesIn)
    return [bodyFont ?? null, headerFont ?? null, footerFont ?? null, ...new Set(words)]
}
