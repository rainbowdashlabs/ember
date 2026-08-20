/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {createMediaFolder, deleteMediaFolder, updateMediaFolder, type StationFileFolder} from '@/api/media'

/**
 * Create / rename / delete flow for file folders, including the modal state the folder dialog
 * binds to.
 *
 * <p>Deletion is a request-then-confirm pair rather than a single call: `removeFolder` opens the
 * confirmation and `confirmRemoveFolder` performs it, so the view can render the styled modal.
 */
export function useMediaFolderForm(
    activeFolder: Ref<number | null>,
    reloadFolders: () => Promise<void>,
    reload: () => Promise<void>,
) {
    const folderModalOpen = ref(false)
    const folderName = ref('')
    const folderParent = ref<number | null>(null)
    const editingFolder = ref<StationFileFolder | null>(null)

    function openFolderModal(parent: StationFileFolder | null) {
        folderName.value = ''
        folderParent.value = parent?.id ?? activeFolder.value
        editingFolder.value = null
        folderModalOpen.value = true
    }

    function openFolderEdit(f: StationFileFolder) {
        folderName.value = f.name
        folderParent.value = f.parentId
        editingFolder.value = f
        folderModalOpen.value = true
    }

    async function saveFolder() {
        if (!folderName.value.trim()) return
        if (editingFolder.value) {
            await updateMediaFolder(
                editingFolder.value.id, folderName.value, folderParent.value, editingFolder.value.sortOrder)
        } else {
            await createMediaFolder(folderName.value, folderParent.value)
        }
        folderModalOpen.value = false
        await reloadFolders()
    }

    const deleteFolder = useConfirmAction<StationFileFolder>({
        onConfirm: async f => {
            await deleteMediaFolder(f.id)
            if (activeFolder.value === f.id) activeFolder.value = f.parentId ?? null
        },
        onSuccess: () => reload(),
    })

    return {
        folderModalOpen,
        folderName,
        folderParent,
        editingFolder,
        openFolderModal,
        openFolderEdit,
        saveFolder,
        removeFolder: deleteFolder.request,
        showDeleteFolder: deleteFolder.show,
        deleteFolderTarget: deleteFolder.target,
        confirmRemoveFolder: deleteFolder.confirm,
    }
}
