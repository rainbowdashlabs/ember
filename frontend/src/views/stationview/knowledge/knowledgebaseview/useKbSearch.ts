/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, watch} from 'vue'
import {knowledgeBase} from '@/api'
import {useDebouncedSearch} from '@/composables/useDebouncedSearch'
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
    const {
        query: searchQuery,
        results: searchResults,
        searching,
        isSearching,
        onInput: onSearchInput,
    } = useDebouncedSearch<SearchResult>(query => knowledgeBase.search(query, {
        tag: filters.filterTag.value || undefined,
        federated: filters.showFederated.value,
    }))

    const filteredSearchResults = computed(() => {
        let results = searchResults.value
        if (!filters.showFederated.value) {
            results = results.filter(r => !r.stationName)
        } else if (filters.filterStationId.value != null) {
            results = results.filter(r => !r.stationName || r.sourceStationUid === filters.filterStationId.value)
        }
        if (filters.filterTag.value) {
            results = results.filter(r => r.stationName || filters.fileMatchesTagFilter(r.file.id))
        }
        return results
    })

    watch([filters.filterTag, searchResults], () => {
        if (!filters.filterTag.value) return
        filters.preloadFileTags(searchResults.value.filter(r => !r.stationName).map(r => r.file.id))
    }, {immediate: true})

    return {searchQuery, searchResults, searching, isSearching, filteredSearchResults, onSearchInput}
}
