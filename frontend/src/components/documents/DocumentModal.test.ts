/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DocumentModal from './DocumentModal.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {MemberDocumentResponse} from '@/api/generated/schema'

/**
 * An open document, which offers its reader what they may change. A sealed one is locked for
 * everybody, so it offers neither removing it nor choosing its members, and shows its versions.
 */

const stubs = {
    Modal: {template: '<div><slot/></div>'},
    FileView: {name: 'FileView', props: ['source', 'title', 'mimeType'], template: '<div/>'},
    MemberSelectInput: {name: 'MemberSelectInput', template: '<div/>'},
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
    })
})
