/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {useMediaBulkActions} from './useMediaBulkActions'
import type {FileListing} from '@/api/generated/schema'

const moved: [number, number | null][] = []

vi.mock('@/api/media', () => ({
    moveMediaFileToFolder: async (id: number, folderId: number | null) => { moved.push([id, folderId]) },
    removeMediaFile: async () => {},
    pruneMediaFiles: async () => {},
}))
vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))

function listing(id: number): FileListing {
    return {file: {id, folderId: null}, tagIds: [], inUse: true} as unknown as FileListing
}

function actions(selected: number[]) {
    const entries = ref([listing(1), listing(2), listing(3)])
    const selectedIds = ref(selected)
    const bulk = useMediaBulkActions({
        entries, selectedIds, activeFolder: ref(null), clearSelection: () => { selectedIds.value = [] }, reload: async () => {},
    })
    return {bulk, entries, selectedIds}
}

/** A file dropped on a folder moves alone, or with the selection it belongs to. */
describe('dropOnFolder', () => {
    it('moves the whole selection when a selected file is dropped', async () => {
        moved.length = 0
        const {bulk, entries, selectedIds} = actions([1, 2])

        await bulk.dropOnFolder(2, 9)

        expect(moved).toEqual([[1, 9], [2, 9]])
        expect(entries.value.map(e => e.file.folderId)).toEqual([9, 9, null])
        expect(selectedIds.value).toEqual([])
    })

    it('moves only the dropped file when it is not selected, and keeps the selection', async () => {
        moved.length = 0
        const {bulk, entries, selectedIds} = actions([1, 2])

        await bulk.dropOnFolder(3, 9)

        expect(moved).toEqual([[3, 9]])
        expect(entries.value.map(e => e.file.folderId)).toEqual([null, null, 9])
        expect(selectedIds.value).toEqual([1, 2])
    })
})
