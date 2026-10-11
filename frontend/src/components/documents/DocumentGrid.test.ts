/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import DocumentGrid from './DocumentGrid.vue'
import type {MemberDocumentResponse} from '@/api/generated/schema'

const requested: string[] = []

vi.mock('@/api/client', () => ({
    default: {
        get: async (url: string) => {
            requested.push(url)
            return {status: 200, data: new Blob(['picture'], {type: 'image/webp'})}
        },
    },
}))

function documentWith(id: number, hasThumbnail: boolean): MemberDocumentResponse {
    return {
        id,
        title: `Dokument ${id}`,
        fileName: `dokument-${id}.pdf`,
        mimeType: 'application/pdf',
        sizeBytes: 1000,
        hidden: false,
        keepOnArchive: true,
        hasThumbnail,
        uploadedBy: null,
        uploaderName: null,
        createdAt: '2026-10-11T09:00:00Z',
        memberIds: [],
        departedNames: [],
        tags: [],
        sealed: false,
        sealedVersions: [],
        signature: null,
    } as unknown as MemberDocumentResponse
}

/** Every document that has a picture shows it on its tile, in the store as anywhere the grid stands. */
describe('DocumentGrid', () => {
    it('shows the picture of each document that has one', async () => {
        const wrapper = mount(DocumentGrid, {
            props: {documents: [documentWith(1, true), documentWith(2, false)], selected: []},
        })
        await flushPromises()

        expect(requested).toEqual(['/documents/1/thumbnail?size=1024'])
        const tiles = wrapper.findAll('[data-testid="document-tile"]')
        expect(tiles[0]!.find('img').exists()).toBe(true)
        expect(tiles[1]!.find('img').exists()).toBe(false)
    })
})
