/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {listMediaFolders, listMediaTags, listMediaFiles, listInstanceMediaFiles, type StationFileFolder, type StationFileListing, type StationFileTag} from '@/api/media'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'

/**
 * The permissions that open the whole library and the folders and tags that organise it. They
 * belong to the three features that author content with the library: pages, news and the knowledge
 * base.
 */
const CONTENT_PERMISSIONS = [
    StationPermission.PAGE_EDIT,
    StationPermission.NEWS_EDIT,
    StationPermission.KNOWLEDGE_EDIT,
] as const

/**
 * Owns the three server-backed collections the file library renders: the files themselves,
 * the folder records and the tag records.
 *
 * <p>Only the files are everybody's. Folders and tags are refused to a member who authors none of
 * the station's content, so they are asked for only where they can be read: a member who is in the
 * library to put a picture on a board ticket was otherwise told, by a message naming three
 * permissions that have nothing to do with what they were doing, that they may not do the thing
 * they had just done.
 *
 * <p>The files are also loaded apart from the two, so a library that cannot say how it is organised
 * still shows what is in it.
 *
 * @param instance reads the library the instance holds rather than a station's. Those files have
 *                 no station, and no folders or tags either: organising them is a station's
 *                 business, and what the instance keeps is the handful of pictures its own notices
 *                 use.
 */
export function useMediaLibrary(instance = false) {
    const {hasPermission} = useSession()
    const entries = ref<StationFileListing[]>([])
    const folders = ref<StationFileFolder[]>([])
    const tags = ref<StationFileTag[]>([])
    const loading = ref(false)

    /**
     * Whether the caller authors station content, which is what decides both the set of files the
     * server answers with and whether the folders and tags may be read at all.
     */
    const organises = computed(() => !instance && CONTENT_PERMISSIONS.some(permission => hasPermission(permission)))

    async function loadEntries() {
        try {
            entries.value = instance ? await listInstanceMediaFiles() : await listMediaFiles()
        } catch {
            entries.value = []
        }
    }

    async function loadOrganisation() {
        if (!organises.value) {
            folders.value = []
            tags.value = []
            return
        }
        try {
            const [fs, ts] = await Promise.all([listMediaFolders(), listMediaTags()])
            folders.value = fs
            tags.value = ts
        } catch {
            folders.value = []
            tags.value = []
        }
    }

    async function load() {
        loading.value = true
        try {
            await Promise.all([loadEntries(), loadOrganisation()])
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

    return {entries, folders, tags, loading, organises, load, reloadFolders, reloadTags}
}
