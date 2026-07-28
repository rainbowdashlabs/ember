/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
    createPageFolder,
    deletePageFolder,
    updatePageFolder,
    type PageFileFolder,
} from '@/api/pageManage'

/**
 * Create / rename / delete flow for file folders, including the modal state the folder dialog
 * binds to.
 */
export function usePageFileFolderForm(
    activeFolder: Ref<number | null>,
    reloadFolders: () => Promise<void>,
    reload: () => Promise<void>,
) {
    const {t} = useI18n()

    const folderModalOpen = ref(false)
    const folderName = ref('')
    const folderParent = ref<number | null>(null)
    const editingFolder = ref<PageFileFolder | null>(null)

    function openFolderModal(parent: PageFileFolder | null) {
        folderName.value = ''
        folderParent.value = parent?.id ?? activeFolder.value
        editingFolder.value = null
        folderModalOpen.value = true
    }

    function openFolderEdit(f: PageFileFolder) {
        folderName.value = f.name
        folderParent.value = f.parentId
        editingFolder.value = f
        folderModalOpen.value = true
    }

    async function saveFolder() {
        if (!folderName.value.trim()) return
        if (editingFolder.value) {
            await updatePageFolder(
                editingFolder.value.id, folderName.value, folderParent.value, editingFolder.value.sortOrder)
        } else {
            await createPageFolder(folderName.value, folderParent.value)
        }
        folderModalOpen.value = false
        await reloadFolders()
    }

    async function removeFolder(f: PageFileFolder) {
        if (!confirm(t('stationPages.editor.folderDeletePrompt', {name: f.name}))) return
        await deletePageFolder(f.id)
        if (activeFolder.value === f.id) activeFolder.value = f.parentId ?? null
        await reload()
    }

    return {
        folderModalOpen,
        folderName,
        folderParent,
        editingFolder,
        openFolderModal,
        openFolderEdit,
        saveFolder,
        removeFolder,
    }
}
