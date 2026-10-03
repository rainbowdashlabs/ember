/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DocumentTemplateKind,
    type DocumentLanguage,
    type DocumentTemplateRequest,
    type DocumentTemplateResponse,
    type FormBinding,
    type LetterPage,
    type PdfField,
} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import {toBlockRequests, toEditRows} from '@/util/blockSwitch'

/**
 * A template as the editor holds it while it is written. A letter uses its header, footer and body
 * rows and the page; a PDF template the fields on its pages and the values of its form fields. Both
 * carry both, so the kind is the only thing that decides which part the screens show.
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
    /** Whether appointments may ask participants to bring it, which makes it legal. */
    forAppointments: boolean
    selfService: boolean
    cooldownDays: number
    audience: RestrictionSelection
    /** The language documents are written in, or null for the station's until the template is saved. */
    language: DocumentLanguage | null
    header: RowEditData[]
    footer: RowEditData[]
    body: RowEditData[]
    page: LetterPage
    fields: PdfField[]
    formBindings: FormBinding[]
}

/** The page a new template starts with: A4 with room for the letterhead, 10 point text. */
export const DEFAULT_PAGE: LetterPage = {
    marginTopMm: 40,
    marginBottomMm: 30,
    marginLeftMm: 20,
    marginRightMm: 20,
    fontSizePt: 10,
    bodyFont: null,
    headerFont: null,
    footerFont: null,
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
        forAppointments: false,
        selfService: false,
        cooldownDays: 30,
        audience: emptyRestriction(),
        language: null,
        header: [],
        footer: [],
        body: [],
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
        forAppointments: template.forAppointments,
        selfService: template.selfService,
        cooldownDays: template.cooldownDays,
        audience: toRestriction(template.audience),
        language: template.language,
        header: toEditRows(template.header),
        footer: toEditRows(template.footer),
        body: toEditRows(template.body),
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
        forAppointments: draft.forAppointments,
        selfService: draft.selfService,
        cooldownDays: draft.cooldownDays,
        audience: draft.audience,
        language: draft.language,
        header: toBlockRequests(draft.header),
        footer: toBlockRequests(draft.footer),
        body: toBlockRequests(draft.body),
        page: draft.page,
        fields: draft.fields,
        formBindings: draft.formBindings.filter(binding => binding.text.trim().length > 0),
    }
}
