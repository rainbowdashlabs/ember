/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type Ref} from 'vue'
import {watchDebounced} from '@vueuse/core'
import type {TemplateListQuery, TemplatePages} from '@/api/documentTemplates'
import {TemplateSort, type DocumentTemplateKind, type TemplatePage} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const SEARCH_SETTLE_MS = 300

/** How many templates a page of tiles holds: four rows of the widest grid. */
export const TEMPLATES_PER_PAGE = 16

/** How a screen's template pages are asked for, beyond what the reader chooses. */
export interface TemplatePagesOptions {
    /** What every request carries, such as leaving out templates for appointments or listing archived ones. */
    fixed?: () => TemplateListQuery
    /** Whether the first page is read on mount; a dialog reads it when it opens instead. */
    autoLoad?: boolean
}

/**
 * The page of templates a screen shows, with the search, kind and order it was asked for.
 *
 * <p>The server searches, sorts and pages, so the order covers every template and not only the ones
 * on screen. A changed search, kind or order starts at the first page again, since the page shown
 * before may not exist any more. Typing waits until it settles before it asks.
 *
 * @param pages reads a page from wherever the screen takes its templates
 */
export function useTemplatePages(pages: TemplatePages, options: TemplatePagesOptions = {}) {
    const search = ref('')
    const kind = ref<DocumentTemplateKind | null>(null)
    const sort = ref<TemplateSort>(TemplateSort.LAST_USED)
    const page = ref(0)
    const result = ref<TemplatePage | null>(null) as Ref<TemplatePage | null>

    const pageCount = computed(() =>
        result.value ? Math.max(1, Math.ceil(result.value.total / result.value.size)) : 1)

    const loader = useAsyncLoader(async isCurrent => {
        const found = await pages({
            ...options.fixed?.(),
            q: search.value.trim() || undefined,
            kind: kind.value ?? undefined,
            sort: sort.value,
            page: page.value,
            size: TEMPLATES_PER_PAGE,
        })
        if (isCurrent()) result.value = found
    }, {autoLoad: options.autoLoad})

    /** Reads the first page again, as after a change that may have moved every template. */
    function fromTheStart() {
        if (page.value === 0) loader.reload()
        else page.value = 0
    }

    watch([kind, sort], fromTheStart)
    watchDebounced(search, fromTheStart, {debounce: SEARCH_SETTLE_MS})
    watch(page, () => loader.reload())

    return {search, kind, sort, page, pageCount, result, loader, fromTheStart}
}
