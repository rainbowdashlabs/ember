/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {
    assignPageTag,
    deleteStationPageFile,
    unassignPageTag,
    updatePageFileMeta,
    type PageFile,
    type PageFileListing,
} from '@/api/pageManage'
import {useConfirmAction} from '@/composables/useConfirmAction'

/**
 * Single-file mutations: alt text and description edits, tag assignment and deletion, each applied
 * to the in-memory listing so the grid never has to reload.
 */
export function usePageFileEditing(entries: Ref<PageFileListing[]>, selectedIds: Ref<number[]>) {
    const editing = ref<PageFile | null>(null)

    function startEdit(f: PageFile) {
        editing.value = f
    }

    async function saveEdit(id: number, altText: string, description: string) {
        await updatePageFileMeta(id, altText || null, description || null)
        entries.value = entries.value.map(e => e.file.id === id
            ? {...e, file: {...e.file, defaultAltText: altText, defaultDescription: description}}
            : e)
    }

    async function toggleFileTag(fileId: number, tagId: number, currentlyAssigned: boolean) {
        if (currentlyAssigned) await unassignPageTag(fileId, tagId)
        else await assignPageTag(fileId, tagId)
        entries.value = entries.value.map(e => {
            if (e.file.id !== fileId) return e
            const next = currentlyAssigned ? e.tagIds.filter(t => t !== tagId) : [...e.tagIds, tagId]
            return {...e, tagIds: next}
        })
    }

    const {
        show: showDeleteFileModal,
        target: deleteFileTarget,
        request: requestDeleteFile,
        confirm: confirmDeleteFile,
    } = useConfirmAction<PageFile>({
        onConfirm: async (file) => {
            await deleteStationPageFile(file.id)
            entries.value = entries.value.filter(e => e.file.id !== file.id)
            selectedIds.value = selectedIds.value.filter(x => x !== file.id)
        },
    })

    return {
        editing,
        startEdit,
        saveEdit,
        toggleFileTag,
        showDeleteFileModal,
        deleteFileTarget,
        requestDeleteFile,
        confirmDeleteFile,
    }
}
