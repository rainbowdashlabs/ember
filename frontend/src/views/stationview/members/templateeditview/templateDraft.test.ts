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
    StationUserType,
    type DocumentTemplateResponse,
} from '@/api/generated/schema'
import {DEFAULT_PAGE, draftOf, emptyDraft, requestOf} from './templateDraft'

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
    selfService: true,
    cooldownDays: 14,
    audience: {userTypes: [StationUserType.MEMBER], groupIds: [], tagIds: [], memberIds: [], mode: 'OR'},
    language: DocumentLanguage.EN,
    header: [],
    footer: [],
    body: [{
        id: 0,
        containerId: 0,
        sortOrder: 0,
        cells: [
            {id: 0, rowId: 0, sortOrder: 0, widthPercent: 50, contentType: CellContentType.MARKDOWN, content: 'Hallo {{member.firstName}}', config: {}},
            {id: 0, rowId: 0, sortOrder: 1, widthPercent: 50, contentType: CellContentType.MARKDOWN, content: 'Nur zur Probe', config: {}, restriction: trialOnly},
        ],
    }],
    page: {...DEFAULT_PAGE},
    pdf: null,
    fields: [],
    formBindings: [],
    version: 2,
    updatedAt: '2026-10-02T10:00:00Z',
    archivedAt: null,
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

    it('send what was read back unchanged, a block\'s visibility included', () => {
        const request = requestOf(draftOf(saved))

        expect(request.name).toBe('Bescheinigung')
        expect(request.titlePattern).toBe('Bescheinigung {{today}}')
        expect(request.audience?.mode).toBe('OR')
        expect(request.language).toBe(DocumentLanguage.EN)
        const cells = request.body?.[0]?.cells ?? []
        expect(cells.map(cell => cell.content)).toEqual(['Hallo {{member.firstName}}', 'Nur zur Probe'])
        expect(cells[0]?.restriction).toBeUndefined()
        expect(cells[1]?.restriction?.userTypes).toEqual([StationUserType.TRIAL])
    })

    it('leave empty patterns to the server', () => {
        const request = requestOf({...emptyDraft(), name: 'Neu', titlePattern: '  '})

        expect(request.titlePattern).toBeNull()
        expect(request.fileNamePattern).toBeNull()
    })
})
