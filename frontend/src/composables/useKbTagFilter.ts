/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {knowledgeBase} from '@/api'

/**
 * Per-file tag cache used to drive the tag filter when browsing the knowledge base.
 * Maps file id → set of tag names assigned to that file. The browse API doesn't
 * include tags on each file so we lazily fetch them once the tag filter is active.
 */
export function useKbTagFilter() {
    const fileTagCache = ref<Map<number, Set<string>>>(new Map())

    function fileMatchesTagFilter(fileId: number, filterTag: string): boolean {
        if (!filterTag) return true
        const tags = fileTagCache.value.get(fileId)
        return !!tags && tags.has(filterTag)
    }

    async function ensureFileTagsLoaded(fileIds: number[]) {
        const missing = fileIds.filter(id => !fileTagCache.value.has(id))
        if (missing.length === 0) return
        const fetched = await Promise.all(
            missing.map(id =>
                knowledgeBase.getFileTags(id)
                    .then(tags => ({id, tags: new Set(tags.map(t => t.name))}))
                    .catch(() => ({id, tags: new Set<string>()})),
            ),
        )
        const next = new Map(fileTagCache.value)
        for (const {id, tags} of fetched) next.set(id, tags)
        fileTagCache.value = next
    }

    return {fileTagCache, fileMatchesTagFilter, ensureFileTagsLoaded}
}
