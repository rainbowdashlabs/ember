/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import MemberDocumentsPanel from './MemberDocumentsPanel.vue'
import DocumentModal from './DocumentModal.vue'
import DocumentUploadModal from './DocumentUploadModal.vue'
import type {MemberDocumentSource} from '@/api/documents'
import type {MemberDocumentResponse} from '@/api/generated/schema'

const listTags = vi.fn()

vi.mock('@/api', () => ({
    documents: {
        listTags: (...args: unknown[]) => listTags(...args),
        setMembers: vi.fn(),
        setTags: vi.fn(),
        remove: vi.fn(),
    },
}))

vi.mock('@/api/client', () => ({
    default: {get: () => Promise.reject(new Error('no picture in a test'))},
}))

function document(overrides: Partial<MemberDocumentResponse> = {}): MemberDocumentResponse {
    return {
        id: 7,
        title: 'Einverständnis',
        fileName: 'einverstaendnis.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 2048,
        hidden: false,
        keepOnArchive: true,
        hasThumbnail: false,
        uploadedBy: null,
        uploaderName: 'Vera Verband',
        createdAt: '2026-10-02T10:00:00Z',
        memberIds: [],
        departedNames: ['Lena Weg'],
        tags: [],
        sealed: false,
        sealedVersions: [],
        ...overrides,
    }
}

/** A door into the store that records what is asked of it. */
function fakeSource(): MemberDocumentSource & {listOf: ReturnType<typeof vi.fn>, upload: ReturnType<typeof vi.fn>} {
    return {
        listOf: vi.fn().mockResolvedValue([document()]),
        upload: vi.fn().mockResolvedValue(document()),
        contentUrl: (documentId: number) => `/elsewhere/${documentId}/content`,
        thumbnailUrl: null,
    }
}

/**
 * One panel for every door into a member's documents: the station's own screens and the
 * association's. What differs is the source it is handed and what the reader may do.
 */
describe('MemberDocumentsPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        listTags.mockResolvedValue([])
    })

    it('reads the documents through the source it is handed', async () => {
        const source = fakeSource()
        const view = mount(MemberDocumentsPanel, {props: {memberId: 11, source, canUpload: true}})
        await flushPromises()

        expect(source.listOf).toHaveBeenCalledWith(11)
        expect(view.text()).toContain('Einverständnis')
        expect(view.text()).toContain('Lena Weg')
    })

    it('opens a document where its source serves it', async () => {
        const source = fakeSource()
        const view = mount(MemberDocumentsPanel, {props: {memberId: 11, source}})
        await flushPromises()

        expect(view.findComponent(DocumentModal).props('contentUrl')?.(7)).toBe('/elsewhere/7/content')
    })

    it('files an upload through the source and offers no labels to a reader who may not set them', async () => {
        const source = fakeSource()
        const view = mount(MemberDocumentsPanel, {props: {memberId: 11, source, canUpload: true}})
        await flushPromises()

        const modal = view.findComponent(DocumentUploadModal)
        expect(modal.props('canLabel')).toBe(false)
        expect(modal.props('canHide')).toBe(false)
        const file = new File(['Ja'], 'ja.txt', {type: 'text/plain'})
        modal.vm.$emit('upload', {file, title: 'Ja'})
        await flushPromises()

        expect(source.upload).toHaveBeenCalledWith(11, {file, title: 'Ja'})
        expect(listTags).not.toHaveBeenCalled()
    })
})
