/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {removeMediaFile, moveMediaFileToFolder, pruneMediaFiles, type StationFileListing} from '@/api/media'
import {useAsyncAction} from '@/composables/useAsyncAction'

interface PageFileBulkDeps {
    entries: Ref<StationFileListing[]>
    selectedIds: Ref<number[]>
    activeFolder: Ref<number | null>
    clearSelection: () => void
    reload: () => Promise<void>
}

/**
 * Actions that operate on many files at once: moving or deleting the current selection and
 * pruning every file that no page references.
 */
export function useMediaBulkActions(deps: PageFileBulkDeps) {
    const {entries, selectedIds, activeFolder, clearSelection, reload} = deps

    const bulkMoveOpen = ref(false)
    const bulkMoveTarget = ref<number | null>(null)
    const bulkDeleteOpen = ref(false)
    const showPruneConfirm = ref(false)

    function openBulkMove() {
        bulkMoveTarget.value = activeFolder.value
        bulkMoveOpen.value = true
    }

    async function runBulkMove() {
        const target = bulkMoveTarget.value
        const ids = [...selectedIds.value]
        for (const id of ids) {
            await moveMediaFileToFolder(id, target)
        }
        entries.value = entries.value.map(e => ids.includes(e.file.id)
            ? {...e, file: {...e.file, folderId: target}}
            : e)
        bulkMoveOpen.value = false
        selectedIds.value = []
    }

    async function runBulkDelete() {
        const ids = [...selectedIds.value]
        bulkDeleteOpen.value = false
        for (const id of ids) {
            try {
                await removeMediaFile(id)
            } catch {
                continue
            }
        }
        clearSelection()
        await reload()
    }

    function runPrune() {
        showPruneConfirm.value = true
    }

    const {running: pruning, run: confirmPrune} = useAsyncAction(async () => {
        showPruneConfirm.value = false
        await pruneMediaFiles()
        await reload()
    })

    return {
        bulkMoveOpen,
        bulkMoveTarget,
        bulkDeleteOpen,
        openBulkMove,
        runBulkMove,
        runBulkDelete,
        showPruneConfirm,
        pruning,
        runPrune,
        confirmPrune,
    }
}
