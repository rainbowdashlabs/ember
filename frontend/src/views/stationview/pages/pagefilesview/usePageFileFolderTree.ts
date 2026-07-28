/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import type {PageFileFolder} from '@/api/pageManage'

/**
 * Derives the navigable shapes of the folder hierarchy: the nested tree for the sidebar, the
 * direct children of the open folder and the breadcrumb trail leading to it.
 */
export function usePageFileFolderTree(folders: Ref<PageFileFolder[]>, activeFolder: Ref<number | null>) {
    const folderTree = computed(() => {
        const byId = new Map<number, PageFileFolder & {children: PageFileFolder[]}>()
        folders.value.forEach(f => byId.set(f.id, {...f, children: []}))
        const roots: Array<PageFileFolder & {children: PageFileFolder[]}> = []
        byId.forEach(node => {
            if (node.parentId != null && byId.has(node.parentId)) byId.get(node.parentId)!.children.push(node)
            else roots.push(node)
        })
        return roots
    })

    const folderById = computed(() => {
        const m = new Map<number, PageFileFolder>()
        folders.value.forEach(f => m.set(f.id, f))
        return m
    })

    const visibleFolders = computed(() =>
        folders.value
            .filter(f => (f.parentId ?? null) === activeFolder.value)
            .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)))

    const breadcrumbs = computed(() => {
        const trail: PageFileFolder[] = []
        let curId: number | null | undefined = activeFolder.value
        while (curId != null) {
            const f = folderById.value.get(curId)
            if (!f) break
            trail.unshift(f)
            curId = f.parentId
        }
        return trail
    })

    return {folderTree, visibleFolders, breadcrumbs}
}
