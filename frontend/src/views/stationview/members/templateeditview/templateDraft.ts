/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DocumentTemplateKind,
    TextAlign,
    LetterCellKind,
    type DocumentTemplateRequest,
    type DocumentTemplateResponse,
    type FormBinding,
    type LetterCell,
    type Letterhead,
    type LetterPage,
    type PdfField,
    type PronounSource,
} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'

/**
 * A template as the editor holds it while it is written. A letter uses the letterhead, the body and the
 * page; a PDF template the fields on its pages and the values of its form fields. Both carry both, so
 * the kind is the only thing that decides which part the screens show.
 */
export interface TemplateDraft {
    kind: DocumentTemplateKind
    name: string
    titlePattern: string
    fileNamePattern: string
    tags: string[]
    hidden: boolean
    keepOnArchive: boolean
    legal: boolean
    selfService: boolean
    cooldownDays: number
    audience: RestrictionSelection
    pronounSource: PronounSource | null
    letterhead: Letterhead
    bodyMarkdown: string
    page: LetterPage
    fields: PdfField[]
    formBindings: FormBinding[]
}

/** How many cells a header or a footer holds at most, which is what the server takes. */
export const MAX_CELLS = 3

/** The page a new template starts with: A4 with room for the letterhead, 10 point text. */
export const DEFAULT_PAGE: LetterPage = {marginTopMm: 40, marginBottomMm: 30, marginLeftMm: 20, marginRightMm: 20, fontSizePt: 10}

/** A cell holding nothing, which is what a new cell starts as. */
export function emptyCell(): LetterCell {
    return {kind: LetterCellKind.EMPTY, mediaHash: null, text: null, align: TextAlign.LEFT, imageHeightMm: 18}
}

/** A new template of a kind: nothing written yet, a wait of 30 days, every member as the audience. */
export function emptyDraft(kind: DocumentTemplateKind = DocumentTemplateKind.LETTER): TemplateDraft {
    return {
        kind,
        name: '',
        titlePattern: '',
        fileNamePattern: '',
        tags: [],
        hidden: false,
        keepOnArchive: false,
        legal: false,
        selfService: false,
        cooldownDays: 30,
        audience: emptyRestriction(),
        pronounSource: null,
        letterhead: {header: [], footer: []},
        bodyMarkdown: '',
        page: {...DEFAULT_PAGE},
        fields: [],
        formBindings: [],
    }
}

/** A saved template as the editor holds it. */
export function draftOf(template: DocumentTemplateResponse): TemplateDraft {
    return {
        kind: template.kind,
        name: template.name,
        titlePattern: template.titlePattern,
        fileNamePattern: template.fileNamePattern,
        tags: [...template.tags],
        hidden: template.hidden,
        keepOnArchive: template.keepOnArchive,
        legal: template.legal,
        selfService: template.selfService,
        cooldownDays: template.cooldownDays,
        audience: toRestriction(template.audience),
        pronounSource: template.pronounSource ?? null,
        letterhead: {header: [...template.letterhead.header], footer: [...template.letterhead.footer]},
        bodyMarkdown: template.bodyMarkdown,
        page: {...template.page},
        fields: template.fields.map(field => ({...field, rect: {...field.rect}})),
        formBindings: template.formBindings.map(binding => ({...binding})),
    }
}

/** The draft as the server takes it. Empty patterns are left to the server, which builds them from the name. */
export function requestOf(draft: TemplateDraft): DocumentTemplateRequest {
    return {
        kind: draft.kind,
        name: draft.name,
        titlePattern: draft.titlePattern.trim() || null,
        fileNamePattern: draft.fileNamePattern.trim() || null,
        tags: draft.tags,
        hidden: draft.hidden,
        keepOnArchive: draft.keepOnArchive,
        legal: draft.legal,
        selfService: draft.selfService,
        cooldownDays: draft.cooldownDays,
        audience: draft.audience,
        pronounSource: draft.pronounSource,
        letterhead: draft.letterhead,
        bodyMarkdown: draft.bodyMarkdown,
        page: draft.page,
        fields: draft.fields,
        formBindings: draft.formBindings.filter(binding => binding.text.trim().length > 0),
    }
}
