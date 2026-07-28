/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {knowledgeBase} from '@/api'
import type {KbTag} from '@/api/knowledgeBase'
import {useKbTagFilter} from '@/composables/useKbTagFilter'
import type {useKbBrowse} from './useKbBrowse'

/**
 * Station and tag filters applied to the browsed folders, files and federated
 * files, together with the tag lookups the search results reuse.
 */
export function useKbFilters(browse: ReturnType<typeof useKbBrowse>) {
    const showFederated = ref(true)
    const filterStationId = ref<string | null>(null)
    const filterTag = ref('')
    const allKbTags = ref<KbTag[]>([])

    const {
        fileMatchesTagFilter: matchesTag,
        folderHasTaggedDescendant: hasTaggedDescendant,
        ensureFileTagsLoaded,
        ensureTagScopeLoaded,
    } = useKbTagFilter()

    const fileMatchesTagFilter = (id: number) => matchesTag(id, filterTag.value)
    const folderHasTaggedDescendant = (id: number) => hasTaggedDescendant(id, filterTag.value)

    const partnerStations = computed(() => {
        const map = new Map<string, string>()
        for (const s of browse.sharedFiles.value) {
            if (s.sourceStationId) map.set(s.sourceStationId, s.stationName)
        }
        return [...map.entries()].map(([id, name]) => ({id, name}))
    })

    const filteredSharedFiles = computed(() => {
        if (!showFederated.value) return []
        if (filterStationId.value != null) {
            return browse.sharedFiles.value.filter(s => s.sourceStationId === filterStationId.value)
        }
        return browse.sharedFiles.value
    })

    const filteredFolders = computed(() => {
        if (filterStationId.value != null) return []
        if (filterTag.value) return browse.folders.value.filter(f => folderHasTaggedDescendant(f.id))
        return browse.folders.value
    })

    const filteredFiles = computed(() => {
        if (filterStationId.value != null) return []
        if (!filterTag.value) return browse.files.value
        return browse.files.value.filter(f => fileMatchesTagFilter(f.id))
    })

    let tagLoads: Promise<void> = Promise.resolve()

    function preloadFileTags(fileIds: number[]) {
        if (fileIds.length === 0) return
        tagLoads = tagLoads.then(() => ensureFileTagsLoaded(fileIds)).catch(() => undefined)
    }

    watch(filterTag, (tag) => { ensureTagScopeLoaded(tag) }, {immediate: true})

    watch([filterTag, browse.files], () => {
        if (!filterTag.value) return
        preloadFileTags(browse.files.value.map(f => f.id))
    }, {immediate: true})

    async function loadTags() {
        try {
            allKbTags.value = await knowledgeBase.listTags()
        } catch {
            return
        }
    }

    return {
        showFederated,
        filterStationId,
        filterTag,
        allKbTags,
        partnerStations,
        filteredFolders,
        filteredFiles,
        filteredSharedFiles,
        fileMatchesTagFilter,
        preloadFileTags,
        loadTags,
    }
}
