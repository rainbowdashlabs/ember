/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {knowledgeBase} from '@/api'

/**
 * Tag-based filter state for the knowledge base browser.
 *
 * When a tag filter is active, the backend's `/kb/tags/{name}/scope` endpoint
 * is queried once to produce two sets:
 *   - `matchingFileIds`: every file in the station that carries the tag
 *   - `ancestorFolderIds`: every folder that has a matching file somewhere in
 *     its subtree (so directory shells along the path are kept visible)
 *
 * The browse view uses these sets to decide which folders and files to render.
 * A small per-file tag cache (used by federated search results that haven't
 * been resolved via the scope endpoint) is kept around as well.
 */
export function useKbTagFilter() {
    const fileTagCache = ref<Map<number, Set<string>>>(new Map())
    const matchingFileIds = ref<Set<number>>(new Set())
    const ancestorFolderIds = ref<Set<number>>(new Set())
    const loadedTag = ref<string>('')

    function fileMatchesTagFilter(fileId: number, filterTag: string): boolean {
        if (!filterTag) return true
        if (loadedTag.value === filterTag && matchingFileIds.value.has(fileId)) return true
        const tags = fileTagCache.value.get(fileId)
        return !!tags && tags.has(filterTag)
    }

    function folderHasTaggedDescendant(folderId: number, filterTag: string): boolean {
        if (!filterTag) return true
        if (loadedTag.value !== filterTag) return false
        return ancestorFolderIds.value.has(folderId)
    }

    async function ensureTagScopeLoaded(filterTag: string) {
        if (!filterTag) {
            matchingFileIds.value = new Set()
            ancestorFolderIds.value = new Set()
            loadedTag.value = ''
            return
        }
        if (loadedTag.value === filterTag) return
        try {
            const scope = await knowledgeBase.getTagScope(filterTag)
            matchingFileIds.value = new Set(scope.matchingFileIds)
            ancestorFolderIds.value = new Set(scope.ancestorFolderIds)
            loadedTag.value = filterTag
        } catch {
            matchingFileIds.value = new Set()
            ancestorFolderIds.value = new Set()
            loadedTag.value = filterTag
        }
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

    return {
        fileTagCache,
        matchingFileIds,
        ancestorFolderIds,
        fileMatchesTagFilter,
        folderHasTaggedDescendant,
        ensureTagScopeLoaded,
        ensureFileTagsLoaded,
    }
}
