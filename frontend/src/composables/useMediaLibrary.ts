/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {listMediaFolders, listMediaTags, listMediaFiles, listInstanceMediaFiles, type StationFileFolder, type StationFileListing, type StationFileTag} from '@/api/media'

/**
 * Owns the three server-backed collections the file library renders: the files themselves,
 * the folder records and the tag records.
 *
 * @param instance reads the library the instance holds rather than a station's. Those files have
 *                 no station, and no folders or tags either: organising them is a station's
 *                 business, and what the instance keeps is the handful of pictures its own notices
 *                 use.
 */
export function useMediaLibrary(instance = false) {
    const entries = ref<StationFileListing[]>([])
    const folders = ref<StationFileFolder[]>([])
    const tags = ref<StationFileTag[]>([])
    const loading = ref(false)

    async function load() {
        loading.value = true
        try {
            if (instance) {
                entries.value = await listInstanceMediaFiles()
                folders.value = []
                tags.value = []
                return
            }
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
