/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {listMediaFolders, listMediaTags, listMediaFiles, type StationFileFolder, type StationFileListing, type StationFileTag} from '@/api/media'

/**
 * Owns the three server-backed collections the file library renders: the files themselves,
 * the folder records and the tag records.
 */
export function useMediaLibrary() {
    const entries = ref<StationFileListing[]>([])
    const folders = ref<StationFileFolder[]>([])
    const tags = ref<StationFileTag[]>([])
    const loading = ref(false)

    async function load() {
        loading.value = true
        try {
            const [files, fs, ts] = await Promise.all([listMediaFiles(), listMediaFolders(), listMediaTags()])
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
        folders.value = await listMediaFolders()
    }

    async function reloadTags() {
        tags.value = await listMediaTags()
    }

    return {entries, folders, tags, loading, load, reloadFolders, reloadTags}
}
