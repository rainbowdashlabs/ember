/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import DocumentModal from './DocumentModal.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {RequestState, type MemberDocumentResponse} from '@/api/generated/schema'

const downloadAuthed = vi.fn()

vi.mock('@/util/downloadAuthed', () => ({
    downloadAuthed: (...args: unknown[]) => downloadAuthed(...args),
}))

/**
 * An open document, which offers its reader what they may change. A sealed one is locked for
 * everybody, so it offers neither removing it nor choosing its members, and shows its versions, each to
 * save. Where signatures were asked for it says how they stand, and their manager looks after them there.
 */

const stubs = {
    Modal: {template: '<div><slot/></div>'},
    FileView: {name: 'FileView', props: ['source', 'title', 'mimeType'], template: '<div/>'},
    MemberSelectInput: {name: 'MemberSelectInput', template: '<div/>'},
    SignatureRequestPanel: {name: 'SignatureRequestPanel', props: ['requestUid', 'onChanged'], template: '<div/>'},
}

function document(overrides: Partial<MemberDocumentResponse> = {}): MemberDocumentResponse {
    return {
        id: 4,
        title: 'Urkunde',
        fileName: 'urkunde.pdf',
        mimeType: 'application/pdf',
        sizeBytes: 4096,
        hidden: false,
        keepOnArchive: true,
        hasThumbnail: false,
        uploadedBy: null,
        uploaderName: null,
        createdAt: '2026-10-08T09:00:00Z',
        memberIds: [3],
        departedNames: [],
        tags: [],
        sealed: false,
        sealedVersions: [],
        signature: null,
        ...overrides,
    }
}

const sealed = document({
    sealed: true,
    sealedVersions: [
        {
            version: 2,
            sha256: 'b'.repeat(64),
            sizeBytes: 4096,
            sealLevel: 'BASELINE_LT',
            timestampedBy: 'http://timestamp.example',
            sealedAt: '2026-10-08T10:00:00Z',
            supersededAt: null,
        },
        {
            version: 1,
            sha256: 'a'.repeat(64),
            sizeBytes: 2048,
            sealLevel: 'BASELINE_B',
            timestampedBy: null,
            sealedAt: '2026-10-08T09:00:00Z',
            supersededAt: '2026-10-08T10:00:00Z',
        },
    ],
})

function open(shown: MemberDocumentResponse) {
    return mount(DocumentModal, {
        props: {modelValue: true, document: shown, canEdit: true, allMembers: [{id: 3, name: 'Mara'}]},
        global: {stubs},
    })
}

describe('DocumentModal', () => {
    it('offers an editor removing a document and choosing its members', () => {
        const view = open(document())

        expect(view.findComponent(DeleteButton).exists()).toBe(true)
        expect(view.findComponent({name: 'MemberSelectInput'}).exists()).toBe(true)
        expect(view.find('[data-testid="document-sealed-versions"]').exists()).toBe(false)
    })

    it('offers neither for a sealed document and lists its versions instead', () => {
        const view = open(sealed)

        expect(view.findComponent(DeleteButton).exists()).toBe(false)
        expect(view.findComponent({name: 'MemberSelectInput'}).exists()).toBe(false)
        const versions = view.get('[data-testid="document-sealed-versions"]').text()
        expect(versions).toContain('Fassung 2')
        expect(versions).toContain('Aktuell')
        expect(versions).toContain('Fassung 1')
        expect(versions).toContain('Ersetzt am')
        expect(versions).toContain('Siegel mit Zeitstempel, offline prüfbar')
        expect(versions).toContain('SHA-256 aaaaaaaaaaaa')
        expect(view.text()).toContain('Mara')
        expect(view.find('[data-testid="document-version-download-1"]').exists()).toBe(false)
    })

    it('saves each sealed version on its own where the door serves versions', async () => {
        const view = mount(DocumentModal, {
            props: {
                modelValue: true,
                document: sealed,
                versionUrl: (documentId: number, version: number) => `/documents/${documentId}/versions/${version}/content`,
            },
            global: {stubs},
        })

        await view.get('[data-testid="document-version-download-1"]').trigger('click')

        expect(downloadAuthed).toHaveBeenCalledWith('/documents/4/versions/1/content', 'urkunde-v1.pdf')
        expect(view.find('[data-testid="document-version-download-2"]').exists()).toBe(true)
    })

    it('offers each version\'s record and the copy with it, and puts the focus on the record asked for', async () => {
        const view = mount(DocumentModal, {
            props: {
                modelValue: true,
                document: sealed,
                recordUrl: (documentId: number, version: number, kind: string) => `/documents/${documentId}/versions/${version}/${kind}`,
                focusRecord: 1,
            },
            global: {stubs},
            attachTo: globalThis.document.body,
        })

        await view.get('[data-testid="document-version-record-2"]').trigger('click')
        await view.get('[data-testid="document-version-with-record-1"]').trigger('click')

        expect(downloadAuthed).toHaveBeenCalledWith('/documents/4/versions/2/record', 'urkunde-v2-record.pdf')
        expect(downloadAuthed).toHaveBeenCalledWith('/documents/4/versions/1/with-record', 'urkunde-v1-with-record.pdf')
        expect(globalThis.document.activeElement?.getAttribute('data-testid')).toBe('document-version-record-1')
        expect(view.text()).toContain('keine neue Fassung')
        view.unmount()
    })

    it('says how the signatures stand and lets only their manager look after them', () => {
        const asked = document({
            signature: {
                requestUid: 'r-1', state: RequestState.OPEN, signed: 0, expected: 2, open: 2, nobodyCanSign: 1,
            },
        })

        const reader = mount(DocumentModal, {props: {modelValue: true, document: asked}, global: {stubs}})
        expect(reader.get('[data-testid="signature-state"]').text()).toContain('Unterschriften offen · 0 von 2')
        expect(reader.findComponent({name: 'SignatureRequestPanel'}).exists()).toBe(false)

        const manager = mount(DocumentModal, {
            props: {modelValue: true, document: asked, manageSignatures: true},
            global: {stubs},
        })
        expect(manager.findComponent({name: 'SignatureRequestPanel'}).props('requestUid')).toBe('r-1')
    })
})
