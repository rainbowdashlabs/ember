/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {assignMediaTag, removeMediaFile, unassignMediaTag, updateMediaFileMeta, type StationFile, type StationFileListing} from '@/api/media'
import {useConfirmAction} from '@/composables/useConfirmAction'

/**
 * Single-file mutations: alt text and description edits, tag assignment and deletion, each applied
 * to the in-memory listing so the grid never has to reload.
 */
export function useMediaEditing(entries: Ref<StationFileListing[]>, selectedIds: Ref<number[]>) {
    const editing = ref<StationFile | null>(null)

    function startEdit(f: StationFile) {
        editing.value = f
    }

    async function saveEdit(id: number, altText: string, description: string) {
        await updateMediaFileMeta(id, altText || null, description || null)
        entries.value = entries.value.map(e => e.file.id === id
            ? {...e, file: {...e.file, defaultAltText: altText, defaultDescription: description}}
            : e)
    }

    async function toggleFileTag(fileId: number, tagId: number, currentlyAssigned: boolean) {
        if (currentlyAssigned) await unassignMediaTag(fileId, tagId)
        else await assignMediaTag(fileId, tagId)
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
    } = useConfirmAction<StationFile>({
        onConfirm: async (file) => {
            await removeMediaFile(file.id)
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
