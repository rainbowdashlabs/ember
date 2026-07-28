/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {federation, knowledgeBase} from '@/api'
import type {KbFile, KbFolder, SharedFileEntry} from '@/api/knowledgeBase'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import type {useKbNavigation} from './useKbNavigation'

/**
 * Contents of the folder the browser currently shows: its subfolders, files,
 * federated files and favourites, plus the breadcrumb trail leading to it.
 */
export function useKbBrowse(navigation: ReturnType<typeof useKbNavigation>) {
    const {t} = useI18n()

    const currentFolder = ref<KbFolder | null>(null)
    const folders = ref<KbFolder[]>([])
    const files = ref<KbFile[]>([])
    const sharedFiles = ref<SharedFileEntry[]>([])
    const favourites = ref<KbFile[]>([])
    const breadcrumbs = ref<KbFolder[]>([])

    const favouriteIds = computed(() => new Set(favourites.value.map(f => f.id)))

    const {loading, error, reload: loadData} = useAsyncLoader(async () => {
        if (navigation.isFavouritesView.value) {
            const result = await knowledgeBase.browse(null)
            currentFolder.value = null
            folders.value = []
            files.value = result.favourites ?? []
            sharedFiles.value = []
            favourites.value = result.favourites ?? []
            breadcrumbs.value = []
        } else {
            const result = await knowledgeBase.browse(navigation.currentFolderId.value)
            currentFolder.value = result.currentFolder
            folders.value = result.folders
            files.value = result.files
            favourites.value = result.favourites ?? []
            await buildBreadcrumbs()

            if (navigation.currentFolderId.value == null) {
                try {
                    const shared = await federation.browseSharedKb()
                    sharedFiles.value = shared.map(s => ({
                        file: {id: s.remoteId, name: s.title, description: s.description},
                        stationName: s.stationName,
                        sourceStationId: s.stationId,
                    }))
                } catch { sharedFiles.value = [] }
            } else {
                sharedFiles.value = []
            }
        }
    }, {autoLoad: false})

    async function buildBreadcrumbs() {
        const crumbs: KbFolder[] = []
        let folder = currentFolder.value
        while (folder) {
            crumbs.unshift(folder)
            if (folder.parentId) {
                try {
                    folder = await knowledgeBase.getFolder(folder.parentId)
                } catch {
                    break
                }
            } else {
                break
            }
        }
        breadcrumbs.value = crumbs
    }

    async function toggleFavourite(file: KbFile, event?: MouseEvent) {
        if (event) event.stopPropagation()
        try {
            if (favouriteIds.value.has(file.id)) {
                await knowledgeBase.removeFavourite(file.id)
                favourites.value = favourites.value.filter(f => f.id !== file.id)
                if (navigation.isFavouritesView.value) {
                    files.value = files.value.filter(f => f.id !== file.id)
                }
            } else {
                await knowledgeBase.addFavourite(file.id)
                favourites.value = [...favourites.value, file]
            }
        } catch {
            error.value = t('common.error')
        }
    }

    async function copySharedFile(fileId: number) {
        try {
            await federation.copyKbFile(fileId)
            await loadData()
        } catch { error.value = t('common.error') }
    }

    return {
        currentFolder,
        folders,
        files,
        sharedFiles,
        favourites,
        breadcrumbs,
        favouriteIds,
        loading,
        error,
        loadData,
        toggleFavourite,
        copySharedFile,
    }
}
