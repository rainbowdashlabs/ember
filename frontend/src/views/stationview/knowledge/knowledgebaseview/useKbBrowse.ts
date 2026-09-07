/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {federation, knowledgeBase} from '@/api'
import type {KbAccessLevelName, KbFile, KbFolder, SharedFileEntry, SharedFolderEntry} from '@/api/knowledgeBase'
import type {SharedContentItem} from '@/api/federation'
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
    const sharedFolders = ref<SharedFolderEntry[]>([])
    const favourites = ref<KbFile[]>([])
    const breadcrumbs = ref<KbFolder[]>([])
    const currentLevel = ref<KbAccessLevelName | undefined>(undefined)
    const folderLevels = ref<Record<number, KbAccessLevelName>>({})
    const fileLevels = ref<Record<number, KbAccessLevelName>>({})
    const sharedTrail = ref<SharedFolderEntry[]>([])
    const publicIds = ref<Set<number>>(new Set())
    const federatedIds = ref<Set<number>>(new Set())
    const narrowIds = ref<Set<number>>(new Set())

    const favouriteIds = computed(() => new Set(favourites.value.map(f => f.id)))

    const {loading, error, reload: loadData} = useAsyncLoader(async () => {
        if (navigation.isTrashView.value) {
            currentFolder.value = null
            folders.value = []
            files.value = []
            sharedFiles.value = []
            sharedFolders.value = []
            sharedTrail.value = []
            breadcrumbs.value = []
        } else if (navigation.isSharedFolderView.value) {
            const level = await federation.browseSharedKbFolder(
                navigation.sharedStationUid.value!, navigation.sharedFolderId.value!)
            currentFolder.value = null
            currentLevel.value = undefined
            folders.value = []
            files.value = []
            favourites.value = []
            breadcrumbs.value = []
            sharedFolders.value = level.folders.map(toSharedFolder)
            sharedFiles.value = level.files.map(toSharedFile)
            sharedTrail.value = level.trail.map(toSharedFolder)
        } else if (navigation.isFavouritesView.value) {
            const result = await knowledgeBase.browse(null)
            currentFolder.value = null
            currentLevel.value = result.currentLevel
            folders.value = []
            files.value = result.favourites ?? []
            sharedFiles.value = []
            sharedFolders.value = []
            favourites.value = result.favourites ?? []
            breadcrumbs.value = []
        } else {
            const result = await knowledgeBase.browse(navigation.currentFolderId.value)
            currentFolder.value = result.currentFolder
            folders.value = result.folders
            files.value = result.files
            currentLevel.value = result.currentLevel
            folderLevels.value = result.folderLevels ?? {}
            fileLevels.value = result.fileLevels ?? {}
            sharedTrail.value = []
            publicIds.value = new Set([
                ...(result.folderReach?.publicly ?? []).map(id => folderKey(id)),
                ...(result.fileReach?.publicly ?? []).map(id => fileKey(id)),
            ])
            federatedIds.value = new Set([
                ...(result.folderReach?.federated ?? []).map(id => folderKey(id)),
                ...(result.fileReach?.federated ?? []).map(id => fileKey(id)),
            ])
            narrowIds.value = new Set([
                ...(result.folderReach?.narrowly ?? []).map(id => folderKey(id)),
                ...(result.fileReach?.narrowly ?? []).map(id => fileKey(id)),
            ])
            favourites.value = result.favourites ?? []
            await buildBreadcrumbs()

            if (navigation.currentFolderId.value == null) {
                try {
                    const shared = await federation.browseSharedKb()
                    sharedFiles.value = shared.files.map(toSharedFile)
                    sharedFolders.value = shared.folders.map(toSharedFolder)
                } catch {
                    sharedFiles.value = []
                    sharedFolders.value = []
                }
            } else {
                sharedFiles.value = []
                sharedFolders.value = []
            }
        }
    }, {autoLoad: false})

    /**
     * A folder and an article can carry the same number, so a reach set holds them apart rather than
     * marking an article because a folder of that number happened to be public.
     */
    function folderKey(id: number): number {
        return id * 2
    }

    function fileKey(id: number): number {
        return id * 2 + 1
    }

    function toSharedFile(item: SharedContentItem): SharedFileEntry {
        return {
            file: {id: item.remoteId, name: item.title, description: item.description},
            stationName: item.stationName,
            sourceStationUid: item.stationUid,
        }
    }

    function toSharedFolder(item: SharedContentItem): SharedFolderEntry {
        return {
            id: item.remoteId,
            name: item.title,
            description: item.description,
            stationName: item.stationName,
            sourceStationUid: item.stationUid,
        }
    }

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
        sharedFolders,
        sharedTrail,
        publicIds,
        federatedIds,
        narrowIds,
        folderKey,
        fileKey,
        favourites,
        breadcrumbs,
        currentLevel,
        folderLevels,
        fileLevels,
        favouriteIds,
        loading,
        error,
        loadData,
        toggleFavourite,
        copySharedFile,
    }
}
