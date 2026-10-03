/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {DocumentTemplateKind, PronounForm, StationUserType, type DocumentTemplateResponse} from '@/api/generated/schema'
import {DEFAULT_PAGE, draftOf, emptyDraft, requestOf} from './templateDraft'

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
    pronounSource: {fieldId: 3, answers: {weiblich: PronounForm.SIE}, fallback: PronounForm.NAME},
    letterhead: {header: [], footer: []},
    bodyMarkdown: 'Hallo {{member.firstName}}',
    page: {...DEFAULT_PAGE},
    version: 2,
    updatedAt: '2026-10-02T10:00:00Z',
    archivedAt: null,
}

describe('template drafts', () => {
    it('start new templates with a wait of 30 days and everybody as the audience', () => {
        const draft = emptyDraft()

        expect(draft.cooldownDays).toBe(30)
        expect(draft.audience.userTypes).toEqual([])
        expect(draft.page).toEqual(DEFAULT_PAGE)
    })

    it('send what was read back unchanged', () => {
        const request = requestOf(draftOf(saved))

        expect(request.name).toBe('Bescheinigung')
        expect(request.titlePattern).toBe('Bescheinigung {{today}}')
        expect(request.audience?.mode).toBe('OR')
        expect(request.pronounSource?.answers.weiblich).toBe(PronounForm.SIE)
        expect(request.bodyMarkdown).toBe('Hallo {{member.firstName}}')
    })

    it('leave empty patterns to the server', () => {
        const request = requestOf({...emptyDraft(), name: 'Neu', titlePattern: '  '})

        expect(request.titlePattern).toBeNull()
        expect(request.fileNamePattern).toBeNull()
    })
})
