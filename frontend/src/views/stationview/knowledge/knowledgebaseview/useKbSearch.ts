/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {knowledgeBase} from '@/api'
import type {SearchResult} from '@/api/knowledgeBase'
import type {useKbFilters} from './useKbFilters'

/**
 * Debounced knowledge base search, narrowed down by the same station and tag
 * filters the browse lists use.
 *
 * Remote (federated) results cannot be tag-checked client-side, but the backend
 * search already received the tag filter, so they stay in the list.
 */
export function useKbSearch(filters: ReturnType<typeof useKbFilters>) {
    const searchQuery = ref('')
    const searchResults = ref<SearchResult[]>([])
    const searching = ref(false)
    const isSearching = computed(() => searchQuery.value.trim().length > 0)

    const filteredSearchResults = computed(() => {
        let results = searchResults.value
        if (!filters.showFederated.value) {
            results = results.filter(r => !r.stationName)
        } else if (filters.filterStationId.value != null) {
            results = results.filter(r => !r.stationName || r.sourceStationId === filters.filterStationId.value)
        }
        if (filters.filterTag.value) {
            results = results.filter(r => r.stationName || filters.fileMatchesTagFilter(r.file.id))
        }
        return results
    })

    let searchTimeout: ReturnType<typeof setTimeout> | null = null

    function onSearchInput() {
        if (searchTimeout) clearTimeout(searchTimeout)
        if (!searchQuery.value.trim()) {
            searchResults.value = []
            return
        }
        searchTimeout = setTimeout(async () => {
            searching.value = true
            try {
                searchResults.value = await knowledgeBase.search(searchQuery.value.trim(), {
                    tag: filters.filterTag.value || undefined,
                    federated: filters.showFederated.value,
                })
            } catch {
                searchResults.value = []
            } finally {
                searching.value = false
            }
        }, 300)
    }

    watch([filters.filterTag, searchResults], () => {
        if (!filters.filterTag.value) return
        filters.preloadFileTags(searchResults.value.filter(r => !r.stationName).map(r => r.file.id))
    }, {immediate: true})

    return {searchQuery, searchResults, searching, isSearching, filteredSearchResults, onSearchInput}
}
