/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {RequirementStatus, type RequiredDocumentStatus} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import {downloadAuthed} from '@/util/downloadAuthed'
import {fetchCopy, needsGenerating} from './documentsToBring'

vi.mock('@/api', () => ({
    appointmentDocuments: {generateToBring: vi.fn()},
    documents: {contentUrl: (id: number) => `/documents/${id}/content`},
}))
vi.mock('@/util/downloadAuthed', () => ({downloadAuthed: vi.fn()}))

const open: RequiredDocumentStatus = {
    templateId: 8,
    name: 'Einverständnis',
    status: RequirementStatus.NOT_GENERATED,
    documentId: null,
    generatedAt: null,
    outdated: false,
    paper: null,
    signature: null,
    agreementOffered: false,
}
const generated: RequiredDocumentStatus = {
    ...open,
    status: RequirementStatus.GENERATED,
    documentId: 40,
    generatedAt: '2026-09-20T10:00:00Z',
}

describe('a participant\'s copy of a document to bring', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.generateToBring).mockReset()
        vi.mocked(downloadAuthed).mockReset()
    })

    it('is generated where there is none or the template changed since', () => {
        expect(needsGenerating(open)).toBe(true)
        expect(needsGenerating(generated)).toBe(false)
        expect(needsGenerating({...generated, outdated: true})).toBe(true)
    })

    it('is generated and filed before it is downloaded', async () => {
        vi.mocked(appointmentDocuments.generateToBring)
            .mockResolvedValue({documentId: 41, generationId: 9, title: 'Einverständnis', missing: []})

        expect(await fetchCopy(5, '2026-09-27', 11, open)).toBe(41)
        expect(appointmentDocuments.generateToBring).toHaveBeenCalledWith(5, '2026-09-27', 8, 11)
        expect(downloadAuthed).toHaveBeenCalledWith('/documents/41/content', 'Einverständnis.pdf')
    })

    it('is downloaded as filed where it is current', async () => {
        expect(await fetchCopy(5, '2026-09-27', 11, generated)).toBe(40)
        expect(appointmentDocuments.generateToBring).not.toHaveBeenCalled()
        expect(downloadAuthed).toHaveBeenCalledWith('/documents/40/content', 'Einverständnis.pdf')
    })
})
