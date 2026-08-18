/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type Ref, type WatchSource} from 'vue'
import type {PageFileListing} from '@/api/pageManage'
import {getItem, setItem} from '@/api/storage'

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const

const PAGE_SIZE_KEY = 'stationPages.filesPerPage'

function loadStoredPageSize(): number {
    const raw = getItem(PAGE_SIZE_KEY)
    const parsed = raw ? parseInt(raw, 10) : NaN
    return (PAGE_SIZE_OPTIONS as readonly number[]).includes(parsed) ? parsed : 20
}

/**
 * Slices the filtered file list into pages and remembers the chosen page size across visits.
 *
 * @param resetSources filter inputs that send the browser back to the first page when they change
 */
export function usePageFilePaging(filtered: Ref<PageFileListing[]>, resetSources: WatchSource[]) {
    const pageSize = ref<number>(loadStoredPageSize())
    const currentPage = ref(1)

    watch(pageSize, (v) => {
        setItem(PAGE_SIZE_KEY, String(v))
        currentPage.value = 1
    })

    watch(resetSources, () => { currentPage.value = 1 })

    const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))

    const pagedFiles = computed(() => {
        const start = (currentPage.value - 1) * pageSize.value
        return filtered.value.slice(start, start + pageSize.value)
    })

    watch(totalPages, (max) => {
        if (currentPage.value > max) currentPage.value = max
    })

    return {pageSize, currentPage, totalPages, pagedFiles}
}
