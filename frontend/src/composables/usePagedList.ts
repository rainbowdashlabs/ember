/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type ComputedRef, type Ref} from 'vue'

export const PAGE_SIZE = 10

/**
 * What a list that grows a page at a time hands its section component.
 *
 * <p>One prop rather than three, so every such list is passed the same way and a section cannot be
 * given the rows of one list and the state of another.
 */
export interface PagedListView<T> {
    items: T[]
    hasMore: boolean
    loadingMore: boolean
}

/**
 * A list the server hands over one page at a time, together with the state the load-more button
 * reads.
 *
 * <p>Whether more is waiting is answered by a full page having come back, which is one request
 * rather than a count the server would have to work out for every page. The cost is a last press
 * that returns nothing, on a list whose length happens to be a multiple of the page.
 *
 * <p>Neither call swallows a failure: the screen that owns the list says what a failed load looks
 * like, and a list that quietly stopped growing would read as a list that had ended.
 *
 * @param fetchPage asks the server for the page starting at an offset
 * @param pageSize  how many rows a page holds, which is also the limit sent with every request
 */
export function usePagedList<T>(fetchPage: (offset: number) => Promise<T[]>, pageSize = PAGE_SIZE) {
    const items = ref([]) as Ref<T[]>
    const hasMore = ref(false)
    const loadingMore = ref(false)

    const view: ComputedRef<PagedListView<T>> = computed(() => ({
        items: items.value,
        hasMore: hasMore.value,
        loadingMore: loadingMore.value,
    }))

    async function load() {
        const page = await fetchPage(0)
        items.value = page
        hasMore.value = page.length >= pageSize
    }

    async function loadMore() {
        if (loadingMore.value || !hasMore.value) return
        loadingMore.value = true
        try {
            const page = await fetchPage(items.value.length)
            items.value = [...items.value, ...page]
            hasMore.value = page.length >= pageSize
        } finally {
            loadingMore.value = false
        }
    }

    return {items, hasMore, loadingMore, view, load, loadMore}
}
