/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {
    listPageFolders,
    listPageTags,
    listStationPageFiles,
    type PageFileFolder,
    type PageFileListing,
    type PageFileTag,
} from '@/api/pageManage'

/**
 * Owns the three server-backed collections the file library renders: the files themselves,
 * the folder records and the tag records.
 */
export function usePageFileLibrary() {
    const entries = ref<PageFileListing[]>([])
    const folders = ref<PageFileFolder[]>([])
    const tags = ref<PageFileTag[]>([])
    const loading = ref(false)

    async function load() {
        loading.value = true
        try {
            const [files, fs, ts] = await Promise.all([listStationPageFiles(), listPageFolders(), listPageTags()])
            entries.value = files
            folders.value = fs
            tags.value = ts
        } catch {
            entries.value = []
            folders.value = []
            tags.value = []
        } finally {
            loading.value = false
        }
    }

    async function reloadFolders() {
        folders.value = await listPageFolders()
    }

    async function reloadTags() {
        tags.value = await listPageTags()
    }

    return {entries, folders, tags, loading, load, reloadFolders, reloadTags}
}
