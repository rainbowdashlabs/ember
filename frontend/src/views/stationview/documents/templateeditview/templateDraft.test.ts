/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {
    CellContentType,
    DocumentLanguage,
    DocumentTemplateKind,
    GuardianCondition,
    StationUserType,
    type DocumentTemplateResponse,
} from '@/api/generated/schema'
import {DEFAULT_PAGE, LEGAL_RETENTION_MONTHS, draftOf, emptyDraft, requestOf, signingOf} from './templateDraft'

const trialOnly = {userTypes: [StationUserType.TRIAL], groupIds: [], tagIds: [], memberIds: [], mode: 'AND' as const}

const saved: DocumentTemplateResponse = {
    id: 4,
    name: 'Bescheinigung',
    kind: DocumentTemplateKind.LETTER,
    titlePattern: 'Bescheinigung {{today}}',
    fileNamePattern: 'bescheinigung',
    tags: ['Nachweis'],
    hidden: false,
    keepOnArchive: true,
    legal: true,
    forAppointments: true,
    selfService: true,
    cooldownDays: 14,
    audience: {userTypes: [StationUserType.MEMBER], groupIds: [], tagIds: [], memberIds: [], mode: 'OR'},
    language: DocumentLanguage.EN,
    issuerId: 12,
    issuerFunction: 'Jugendfeuerwehrwartin',
    header: [],
    footer: [],
    body: [{
        id: 0,
        containerId: 0,
        sortOrder: 0,
        columnLines: true,
        cells: [
            {id: 0, rowId: 0, sortOrder: 0, widthPercent: 50, contentType: CellContentType.MARKDOWN, content: 'Hallo {{member.firstName}}', config: {}},
            {id: 0, rowId: 0, sortOrder: 1, widthPercent: 50, contentType: CellContentType.MARKDOWN, content: 'Nur zur Probe', config: {}, restriction: trialOnly,
                guardianCondition: GuardianCondition.SECOND_GUARDIAN},
        ],
    }],
    page: {...DEFAULT_PAGE},
    pdf: null,
    fields: [],
    formBindings: [],
    version: 2,
    updatedAt: '2026-10-02T10:00:00Z',
    archivedAt: null,
    signing: {retentionMonths: 120, copyAttached: true},
}

describe('template drafts', () => {
    it('start new templates with a wait of 30 days, everybody as the audience and the station\'s language', () => {
        const draft = emptyDraft()

        expect(draft.cooldownDays).toBe(30)
        expect(draft.audience.userTypes).toEqual([])
        expect(draft.page).toEqual(DEFAULT_PAGE)
        expect(draft.language).toBeNull()
        expect(draft.body).toEqual([])
    })

    it('leave how signed documents are kept to the server until it is set, as a template of its kind starts', () => {
        const draft = emptyDraft()

        expect(draft.signing).toBeNull()
        expect(requestOf(draft).signing).toBeNull()
        expect(signingOf(draft)).toEqual({retentionMonths: null, copyAttached: false})
        expect(signingOf({...draft, legal: true})).toEqual({retentionMonths: LEGAL_RETENTION_MONTHS, copyAttached: false})
    })

    it('keep how a saved template keeps and sends its signed documents', () => {
        const draft = draftOf(saved)

        expect(signingOf(draft)).toEqual({retentionMonths: 120, copyAttached: true})
        expect(requestOf(draft).signing).toEqual({retentionMonths: 120, copyAttached: true})
    })

    it('send what was read back unchanged, a block\'s visibility and a row\'s lines included', () => {
        const request = requestOf(draftOf(saved))

        expect(request.name).toBe('Bescheinigung')
        expect(request.titlePattern).toBe('Bescheinigung {{today}}')
        expect(request.audience?.mode).toBe('OR')
        expect(request.language).toBe(DocumentLanguage.EN)
        expect(request.forAppointments).toBe(true)
        expect(request.issuerId).toBe(12)
        expect(request.issuerFunction).toBe('Jugendfeuerwehrwartin')
        const cells = request.body?.[0]?.cells ?? []
        expect(cells.map(cell => cell.content)).toEqual(['Hallo {{member.firstName}}', 'Nur zur Probe'])
        expect(cells[0]?.restriction).toBeUndefined()
        expect(cells[1]?.restriction?.userTypes).toEqual([StationUserType.TRIAL])
        expect(request.body?.[0]?.columnLines).toBe(true)
        expect(cells[0]?.guardianCondition).toBeUndefined()
        expect(cells[1]?.guardianCondition).toBe(GuardianCondition.SECOND_GUARDIAN)
    })

    it('leave empty patterns to the server', () => {
        const request = requestOf({...emptyDraft(), name: 'Neu', titlePattern: '  ', issuerFunction: ' '})

        expect(request.titlePattern).toBeNull()
        expect(request.fileNamePattern).toBeNull()
        expect(request.issuerId).toBeNull()
        expect(request.issuerFunction).toBeNull()
    })
})
